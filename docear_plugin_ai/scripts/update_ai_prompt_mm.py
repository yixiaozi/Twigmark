# -*- coding: utf-8 -*-
import re
import sys

def encode_attr(s):
    out = []
    for c in s:
        o = ord(c)
        if o > 0x7E:
            out.append("&#x" + format(o, "x") + ";")
        elif c == "\n":
            out.append("&#xa;")
        elif c == "&":
            out.append("&amp;")
        elif c == '"':
            out.append("&quot;")
        elif c == "<":
            out.append("&lt;")
        elif c == ">":
            out.append("&gt;")
        elif c == "'":
            out.append("&apos;")
        elif o < 32:
            out.append("&#x" + format(o, "x") + ";")
        else:
            out.append(c)
    return "".join(out)


CHAT_TEMPLATE = """你是具备最强分析能力的思维导图与研究助手。用户正在 Docear/Freeplane 中工作。
Docear 已在本地为你读取了当前思维导图、节点链接及文本中提及的关联文件内容（见下方）。请基于这些已提供的信息进行全面、深入、多角度的分析，尽可能考虑周全后回答用户问题。

重要说明：
1. 你收到的内容已经过本地脱敏，密码、密钥、令牌等敏感信息已替换为 [已脱敏]，请勿试图还原或猜测。
2. 下方 MAP_CONTENT 等为本地预读摘要；信息不足或需要写入/搜索时，请通过 Docear MCP 工具获取最新数据，也可直接读取工作区内的 .mm 等文件。
3. 若仍无法获得所需信息，请明确说明缺什么。
4. 下方「全局安排与关注」汇总了用户在所有导图中的提醒、周期提醒、待办与钉选节点。用户在任意导图中可询问「我有什么安排」「今天要做什么」等问题，请基于该汇总回答。
5. 「导图库文件索引」列出工作区内文件（路径相对 @ 根目录压缩）。用户问题涉及找文件、关联材料时，请从索引中选择路径，结合 {{REFERENCED_FILES}} 与 {{MAP_CONTENT}} 分析，必要时用 MCP 或直读文件补充。
6. 所有返回的信息都要用中文。

当前思维导图路径：{{MAP_PATH}}
当前思维导图标题：{{MAP_TITLE}}

当前选中节点（及子树）：
{{SELECTED_NODE}}

思维导图与关联上下文：
{{MAP_CONTENT}}

关联文件摘要：
{{REFERENCED_FILES}}

导图库文件索引（压缩路径，便于定位关联文件）：
{{WORKSPACE_FILE_INDEX}}

关键词参考：
{{KEYWORDS}}

当前匹配的关键词规则：
{{ACTIVE_KEYWORD_RULES}}

全局安排与关注（全部提醒 / 周期提醒 / 全部待办 / 我的钉选，跨所有导图汇总）：
{{WORKSPACE_PLANS}}

历史对话：
{{CHAT_HISTORY}}

用户问题：
{{USER_QUESTION}}"""

HELP_TEXT = """绿色背景节点为系统节点，不可删除：
AI提示词、聊天系统提示词、生成子节点提示词、关键词库、使用说明

支持占位符：{{MAP_PATH}}、{{MAP_TITLE}}、{{SELECTED_NODE}}、{{MAP_CONTENT}}、{{REFERENCED_FILES}}、{{WORKSPACE_FILE_INDEX}}、{{KEYWORDS}}、{{ACTIVE_KEYWORD_RULES}}、{{WORKSPACE_PLANS}}、{{CHAT_HISTORY}}、{{USER_QUESTION}}。
关键词库下的子节点作为规则；用户提问匹配关键词名称时，子节点内容会注入 {{ACTIVE_KEYWORD_RULES}}。
{{WORKSPACE_FILE_INDEX}} 列出导图库内所有文件（路径相对 @ 根目录压缩），便于定位关联 .mm 及其他文件。
{{WORKSPACE_PLANS}} 自动汇总全部提醒、周期提醒、全部待办、我的钉选四个标签页内容，含导图路径与节点 ID。
Docear 会自动读取导图链接和路径中的文件，发送前自动脱敏敏感信息。
在「关键词库」下添加子节点即可自定义关键词（关键词子节点可删改）。
修改后保存文件即可生效。"""


def replace_node_text(content, node_id, new_text):
    marker = 'ID="' + node_id + '"'
    idx = content.find(marker)
    if idx < 0:
        raise SystemExit("Node not found: " + node_id)
    text_start = content.rfind('TEXT="', 0, idx)
    if text_start < 0:
        raise SystemExit("TEXT attribute not found for: " + node_id)
    text_start += len('TEXT="')
    text_end = content.index('"', text_start)
    enc = encode_attr(new_text)
    return content[:text_start] + enc + content[text_end:]


def main():
    path = r"E:\yixiaozi\00统领全局\AI提示词.mm"
    if len(sys.argv) > 1:
        path = sys.argv[1]
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    content = replace_node_text(content, "ID_AI_PROMPT_CHAT", CHAT_TEMPLATE)
    content = replace_node_text(content, "ID_AI_PROMPT_HELP", HELP_TEXT)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    print("Updated:", path)


if __name__ == "__main__":
    main()
