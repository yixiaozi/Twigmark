#!/usr/bin/env python3
"""Source-level check: new MCP write tools are registered everywhere."""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src" / "org" / "docear" / "plugin" / "mcp"

TOOLS = [
    "copy_nodes",
    "cut_nodes",
    "paste_nodes",
    "clone_nodes",
    "undo_map",
    "redo_map",
    "add_arrow_link",
    "remove_arrow_link",
    "set_node_cloud",
    "set_node_style",
    "set_node_details",
    "set_node_privacy",
    "set_node_image",
    "clear_node_image",
    "set_node_attachment",
    "clear_reminder",
]


def read(rel: str) -> str:
    return (SRC / rel).read_text(encoding="utf-8")


def main() -> int:
    permissions = read("server/McpPermissions.java")
    protocol = read("server/McpProtocol.java")
    audit = read("audit/McpAuditService.java")
    prompt = read("webchat/WebchatSystemPrompt.java")
    edit = read("service/McpNodeEditService.java")
    missing = []
    for name in TOOLS:
        if f'set.add("{name}")' not in permissions:
            missing.append(f"permissions:{name}")
        if f'tool("{name}"' not in protocol:
            missing.append(f"protocol.tool:{name}")
        if f'"{name}".equals(name)' not in protocol:
            missing.append(f"protocol.dispatch:{name}")
        if f'"{name}".equals(toolName)' not in audit:
            missing.append(f"audit:{name}")
        if name.replace("_", " ") not in prompt.replace("_", " ") and name not in prompt:
            missing.append(f"prompt:{name}")
    for method in (
        "copyNodes",
        "cutNodes",
        "pasteNodes",
        "cloneNodes",
        "undoMap",
        "redoMap",
        "addArrowLink",
        "removeArrowLink",
        "setNodeCloud",
        "setNodeStyle",
        "setNodeDetails",
        "setNodePrivacy",
        "setNodeImage",
        "clearNodeImage",
        "setNodeAttachment",
        "clearReminder",
    ):
        if f"public static String {method}" not in edit:
            missing.append(f"service:{method}")
    if missing:
        raise SystemExit("missing registrations:\n- " + "\n- ".join(missing))
    print("test_node_edit_tools_sources.py OK (%d tools)" % len(TOOLS))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
