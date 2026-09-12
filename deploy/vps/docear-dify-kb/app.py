#!/usr/bin/env python3
"""Docear MCP -> Dify External Knowledge API gateway."""

from __future__ import annotations

import json
import os
import re
import uuid
from typing import Any

import httpx
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

DOCEAR_MCP_URL = os.environ.get("DOCEAR_MCP_URL", "http://127.0.0.1:7720/mcp")
DOCEAR_MCP_KEY = os.environ.get("DOCEAR_MCP_KEY", "")
DIFY_KB_API_KEY = os.environ.get("DIFY_KB_API_KEY", "")
DEFAULT_MODIFIED_DAYS = int(os.environ.get("DEFAULT_MODIFIED_DAYS", "365"))
ENRICH_DETAILS = os.environ.get("ENRICH_DETAILS", "true").lower() == "true"
MCP_TIMEOUT = float(os.environ.get("MCP_TIMEOUT", "90"))

app = FastAPI(title="Docear Dify KB Gateway", version="1.0.0")


class RetrievalSetting(BaseModel):
    top_k: int = 5
    score_threshold: float = 0.0


class RetrievalRequest(BaseModel):
    knowledge_id: str
    query: str
    retrieval_setting: RetrievalSetting
    metadata_condition: dict[str, Any] | None = None


def verify_auth(authorization: str | None) -> None:
    if not DIFY_KB_API_KEY:
        return
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=401,
            detail={"error_code": 1001, "error_msg": "Invalid Authorization header format"},
        )
    token = authorization[7:].strip()
    if token != DIFY_KB_API_KEY:
        raise HTTPException(
            status_code=401,
            detail={"error_code": 1002, "error_msg": "Authorization failed. Please check your API key."},
        )


def knowledge_scope(knowledge_id: str) -> dict[str, str]:
    kid = knowledge_id.strip()
    if kid == "memory":
        return {"file_prefix": "/data/mindmaps/09记录存档/01AI对话/01Cursor/memory"}
    if kid == "cursor":
        return {"file_prefix": "/data/mindmaps/09记录存档/01AI对话/01Cursor"}
    if kid == "no-daily":
        return {"exclude_pattern": r"/daily/"}
    if kid.startswith("path:"):
        rel = kid[5:].strip("/")
        return {"file_prefix": f"/data/mindmaps/{rel}"}
    return {}


def normalize_map_path(path: str) -> str:
    return path.replace("/root/Dropbox/mindmaps/", "/data/mindmaps/")


def should_include(item: dict[str, Any], scope: dict[str, str]) -> bool:
    map_file = normalize_map_path(item.get("mapFile", ""))
    prefix = scope.get("file_prefix")
    if prefix and not map_file.startswith(prefix):
        return False
    exclude = scope.get("exclude_pattern")
    if exclude and re.search(exclude, map_file):
        return False
    return True


def compute_score(query: str, node_text: str, parent_path: str, rank: int) -> float:
    q = query.lower().strip()
    text = (node_text or "").lower()
    path = (parent_path or "").lower()
    if not q:
        return max(0.1, 0.9 - rank * 0.05)

    score = 0.35
    if q in text:
        score += 0.45
    else:
        terms = [t for t in re.split(r"\s+", q) if len(t) >= 2]
        if terms:
            hits = sum(1 for term in terms if term in text or term in path)
            score += 0.35 * (hits / len(terms))
    score -= rank * 0.03
    return max(0.01, min(0.99, score))


def map_file_to_title(map_file: str) -> str:
    name = map_file.rsplit("/", 1)[-1]
    if name.endswith(".mm"):
        return name[:-3]
    return name


def build_content(item: dict[str, Any], details: dict[str, Any] | None) -> str:
    parts: list[str] = []
    parent = item.get("parentPath") or (details or {}).get("parentPath", "")
    text = item.get("nodeText") or item.get("text") or (details or {}).get("text", "")
    if parent:
        parts.append(f"路径: {parent}")
    if text:
        parts.append(f"节点: {text}")
    if details:
        for key, label in (
            ("notePlain", "备注"),
            ("detailsPlain", "详情"),
            ("link", "链接"),
            ("tags", "标签"),
        ):
            value = (details.get(key) or "").strip()
            if value:
                parts.append(f"{label}: {value}")
    map_file = normalize_map_path(item.get("mapFile") or (details or {}).get("mapFile", ""))
    if map_file:
        parts.append(f"导图: {map_file}")
    return "\n".join(parts)


async def mcp_call(tool_name: str, arguments: dict[str, Any]) -> Any:
    if not DOCEAR_MCP_KEY:
        raise HTTPException(status_code=500, detail="DOCEAR_MCP_KEY is not configured")

    payload = {
        "jsonrpc": "2.0",
        "id": str(uuid.uuid4()),
        "method": "tools/call",
        "params": {"name": tool_name, "arguments": arguments},
    }
    headers = {
        "Authorization": f"Bearer {DOCEAR_MCP_KEY}",
        "Content-Type": "application/json",
    }
    async with httpx.AsyncClient(timeout=MCP_TIMEOUT) as client:
        response = await client.post(DOCEAR_MCP_URL, json=payload, headers=headers)
        response.raise_for_status()
        data = response.json()

    if "error" in data:
        raise HTTPException(status_code=502, detail={"error_code": 2002, "error_msg": str(data["error"])})

    result = data.get("result", {})
    if result.get("isError"):
        content = result.get("content", [])
        message = content[0].get("text", "MCP tool error") if content else "MCP tool error"
        raise HTTPException(status_code=502, detail={"error_code": 2003, "error_msg": message})

    content = result.get("content", [])
    if not content:
        return None

    text = content[0].get("text", "")
    if not text:
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return text


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok", "service": "docear-dify-kb-gateway"}


@app.post("/retrieval")
async def retrieval(
    request: RetrievalRequest,
    authorization: str | None = Header(default=None),
) -> dict[str, list[dict[str, Any]]]:
    verify_auth(authorization)

    query = request.query.strip()
    if not query:
        return {"records": []}

    scope = knowledge_scope(request.knowledge_id)
    top_k = max(1, min(request.retrieval_setting.top_k, 20))
    trace_id = str(uuid.uuid4())
    fetch_limit = min(top_k * 8, 40) if scope else top_k

    search_args: dict[str, Any] = {
        "query": query,
        "limit": fetch_limit,
        "modifiedWithinDays": DEFAULT_MODIFIED_DAYS,
        "_audit": {
            "caller": "dify-kb",
            "traceId": trace_id,
            "questionSummary": query[:240],
            "operationGoal": f"retrieval knowledge_id={request.knowledge_id}",
        },
    }
    # Docear search_nodes filePath must be one .mm file, not a directory.
    # Scope filtering is applied after search.

    hits = await mcp_call("search_nodes", search_args)
    if not isinstance(hits, list):
        hits = []

    filtered = [hit for hit in hits if should_include(hit, scope)][:top_k]

    records: list[dict[str, Any]] = []
    for rank, item in enumerate(filtered):
        details: dict[str, Any] | None = None
        map_file = normalize_map_path(item.get("mapFile", ""))
        node_id = item.get("nodeId", "")

        if ENRICH_DETAILS and map_file and node_id:
            raw_details = await mcp_call(
                "get_node_details",
                {
                    "filePath": map_file,
                    "nodeId": node_id,
                    "_audit": {
                        "caller": "dify-kb",
                        "traceId": trace_id,
                        "questionSummary": query[:240],
                        "operationGoal": "enrich node details",
                    },
                },
            )
            if isinstance(raw_details, dict):
                details = raw_details

        score = compute_score(
            query,
            item.get("nodeText", ""),
            item.get("parentPath", ""),
            rank,
        )
        if score < request.retrieval_setting.score_threshold:
            continue

        parent_path = item.get("parentPath", "")
        title = map_file_to_title(map_file)
        if parent_path:
            title = f"{title} > {parent_path}"

        records.append(
            {
                "content": build_content(item, details),
                "score": round(score, 4),
                "title": title[:200],
                "metadata": {
                    "mapFile": map_file,
                    "nodeId": node_id,
                    "knowledge_id": request.knowledge_id,
                },
            }
        )

    records.sort(key=lambda record: record["score"], reverse=True)
    return {"records": records[:top_k]}
