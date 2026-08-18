#!/usr/bin/env python3
"""Live MCP write-tool test against a dedicated .mm (local or SSH-forwarded).

Env:
  MCP_URL   default http://127.0.0.1:7720/mcp
  MCP_KEY   Authorization bearer / x-api-key
  TEST_MAP  optional existing .mm; otherwise create_mindmap under TEST_DIR
  TEST_DIR  default /tmp
"""
from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

BASE = os.environ.get("MCP_URL", "http://127.0.0.1:7720/mcp")
KEY = os.environ.get("MCP_KEY", "")
TEST_DIR = Path(os.environ.get("TEST_DIR", "/tmp"))
REQ_ID = 1
failures = []


def mcp(method: str, params=None, timeout=90):
    global REQ_ID
    payload = {"jsonrpc": "2.0", "id": REQ_ID, "method": method}
    REQ_ID += 1
    if params is not None:
        payload["params"] = params
    headers = {"Content-Type": "application/json; charset=utf-8"}
    if KEY:
        headers["Authorization"] = "Bearer " + KEY
        headers["X-API-Key"] = KEY
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(BASE, data=data, headers=headers, method="POST")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def tool(name: str, arguments: dict | None = None) -> dict:
    arguments = dict(arguments or {})
    arguments["_audit"] = {
        "caller": "mcp-node-edit-live-test",
        "traceId": "20260818-node-edit-tools",
        "questionSummary": "live test new MCP write tools",
        "operationGoal": name,
    }
    resp = mcp("tools/call", {"name": name, "arguments": arguments})
    if "error" in resp:
        raise RuntimeError("%s RPC error: %s" % (name, resp["error"]))
    content = (resp.get("result") or {}).get("content") or []
    text = content[0].get("text") if content else ""
    is_error = bool((resp.get("result") or {}).get("isError"))
    try:
        body = json.loads(text) if text and text.lstrip().startswith("{") else {"raw": text}
    except json.JSONDecodeError:
        body = {"raw": text}
    if is_error:
        raise RuntimeError("%s tool error: %s" % (name, text))
    return body


def expect(cond: bool, msg: str) -> None:
    if cond:
        print("PASS", msg)
    else:
        print("FAIL", msg)
        failures.append(msg)


def main() -> int:
    listed = mcp("tools/list")
    names = [t.get("name") for t in ((listed.get("result") or {}).get("tools") or [])]
    for required in (
        "copy_nodes",
        "clone_nodes",
        "undo_map",
        "add_arrow_link",
        "set_node_cloud",
        "set_node_style",
        "set_node_details",
        "set_node_privacy",
        "set_node_image",
        "clear_reminder",
    ):
        expect(required in names, "tools/list has " + required)

    map_path = os.environ.get("TEST_MAP", "")
    if not map_path:
        map_path = str(TEST_DIR / ("mcp-write-tools-%d.mm" % int(time.time())))
        created = tool("create_mindmap", {"filePath": map_path, "rootText": "MCP Write Tools Test"})
        expect(created.get("created") is True, "create_mindmap")
        root_id = created.get("rootNodeId") or ""
    else:
        data = tool("get_mindmap_json", {"filePath": map_path, "maxDepth": 1})
        root_id = (data.get("id") or data.get("nodeId") or data.get("rootNodeId") or "")
        if not root_id and data.get("root"):
            root_id = data["root"].get("id") or data["root"].get("nodeId") or ""

    if not root_id:
        # some responses wrap the tree
        tree = tool("get_mindmap_json", {"filePath": map_path, "maxDepth": 1, "includeFolded": True})
        root_id = tree.get("id") or tree.get("nodeId") or ""
        if not root_id:
            raise SystemExit("no rootNodeId: " + json.dumps(tree)[:500])

    src = tool("add_node", {"filePath": map_path, "parentNodeId": root_id, "text": "src-tree"})
    src_id = src.get("nodeId")
    child = tool("add_node", {"filePath": map_path, "parentNodeId": src_id, "text": "leaf-a"})
    leaf_id = child.get("nodeId")
    dst = tool("add_node", {"filePath": map_path, "parentNodeId": root_id, "text": "dst"})
    dst_id = dst.get("nodeId")
    other = tool("add_node", {"filePath": map_path, "parentNodeId": root_id, "text": "style-target"})
    other_id = other.get("nodeId")

    cloned = tool("clone_nodes", {"filePath": map_path, "nodeId": src_id, "parentNodeId": dst_id})
    expect(cloned.get("pastedNodeCount") == 2, "clone_nodes count=" + str(cloned.get("pastedNodeCount")))
    clone_root = (cloned.get("pastedRootIds") or [None])[0]
    expect(bool(clone_root) and clone_root != src_id, "clone got new id")

    details = tool("set_node_details", {"filePath": map_path, "nodeId": other_id, "detailsHtml": "<html><body>detail-ok</body></html>"})
    expect(details.get("cleared") is False, "set_node_details")
    got = tool("get_node_details", {"filePath": map_path, "nodeId": other_id})
    expect("detail-ok" in (got.get("detailsHtml") or got.get("detailsPlain") or ""), "details readable")

    priv = tool("set_node_privacy", {"filePath": map_path, "nodeId": other_id, "privacy": "PRIVATE"})
    expect(priv.get("privacy") == "PRIVATE", "set_node_privacy")
    got = tool("get_node_details", {"filePath": map_path, "nodeId": other_id})
    expect(got.get("privacy") == "PRIVATE", "privacy readable")

    cloud = tool("set_node_cloud", {"filePath": map_path, "nodeId": other_id, "enabled": True, "color": "#ffcc00", "shape": "ROUND_RECT"})
    expect(cloud.get("cloud") is True, "set_node_cloud")

    style = tool("set_node_style", {"filePath": map_path, "nodeId": other_id, "color": "#112233", "backgroundColor": "#eeeeee", "bold": True, "fontSize": 16, "shape": "bubble"})
    expect(style.get("saved") is True or style.get("nodeId") == other_id, "set_node_style")
    got = tool("get_node_details", {"filePath": map_path, "nodeId": other_id})
    expect(got.get("bold") is True, "style bold")
    expect((got.get("nodeColor") or "").lower() in ("#112233", "112233"), "style color %s" % got.get("nodeColor"))

    arrow = tool("add_arrow_link", {"filePath": map_path, "sourceNodeId": other_id, "targetNodeId": dst_id, "label": "rel"})
    expect(arrow.get("targetNodeId") == dst_id, "add_arrow_link")
    got = tool("get_node_details", {"filePath": map_path, "nodeId": other_id})
    arrows = got.get("arrowLinks") or []
    expect(any(a.get("targetNodeId") == dst_id for a in arrows), "arrowLinks visible")

    remind_at = int(time.time() * 1000) + 3600 * 1000
    tool("set_reminder", {"filePath": map_path, "nodeId": leaf_id, "remindAtMillis": remind_at})
    cleared = tool("clear_reminder", {"filePath": map_path, "nodeId": leaf_id})
    expect(cleared.get("cleared") is True, "clear_reminder")
    got = tool("get_node_details", {"filePath": map_path, "nodeId": leaf_id})
    expect(not got.get("remindAtMillis"), "reminder gone")

    png = TEST_DIR / "mcp-write-tools.png"
    png.write_bytes(
        bytes.fromhex(
            "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4"
            "890000000a49444154789c63000100000500010d0a2db40000000049454e44ae426082"
        )
    )
    img = tool("set_node_image", {"filePath": map_path, "nodeId": other_id, "imagePath": str(png)})
    expect(img.get("imageAttached") is True, "set_node_image")
    att = tool("set_node_attachment", {"filePath": map_path, "nodeId": dst_id, "attachmentPath": str(png)})
    expect(att.get("linkSet") is True, "set_node_attachment")

    copied = tool("copy_nodes", {"filePath": map_path, "nodeId": src_id})
    expect(copied.get("copiedNodeCount") == 2, "copy_nodes")
    pasted = tool("paste_nodes", {"filePath": map_path, "parentNodeId": dst_id})
    expect((pasted.get("pastedNodeCount") or 0) >= 2, "paste_nodes")

    undone = tool("undo_map", {"filePath": map_path})
    expect(undone.get("applied") is True, "undo_map applied")
    redone = tool("redo_map", {"filePath": map_path})
    expect(redone.get("applied") is True, "redo_map applied")

    removed = tool("remove_arrow_link", {"filePath": map_path, "sourceNodeId": other_id, "targetNodeId": dst_id})
    expect((removed.get("removedCount") or 0) >= 1, "remove_arrow_link")

    xml = Path(map_path).read_text(encoding="utf-8", errors="replace")
    expect("detail-ok" in xml, "mm file has details")
    expect("DCR_PRIVACY_LEVEL" in xml or "PRIVATE" in xml, "mm file has privacy")
    expect("arrowlink" in xml.lower(), "mm file has arrowlink")
    expect("cloud" in xml.lower(), "mm file has cloud")

    print("map:", map_path)
    if failures:
        print("FAILED", len(failures))
        for item in failures:
            print(" -", item)
        return 1
    print("ALL PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
