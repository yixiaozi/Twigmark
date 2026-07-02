---
name: docear-mcp-context
description: >-
  Before any Docear MCP mind-map read or write, always fetch the user's currently
  open map and selected node via get_selection_context. When writing to mind maps,
  use clear hierarchical categories (overview → major sections → sub-items), not
  flat long one-liners. Use when the user mentions mind maps, .mm files, Docear,
  MCP tools, todos, reminders, or asks to add/organize nodes.
---

# Docear MCP：先读当前上下文

## 铁律

**任何导图操作之前，必须先确认用户正在看什么。**

禁止默认 `test.mm`、禁止凭对话历史猜父节点、禁止硬编码节点 ID（除非上一步刚从上下文读到）。

## 第一步（必做）

通过 Docear MCP 调用（Cursor 直连 `http://127.0.0.1:7720/mcp`，或已配置的 `docear` MCP 工具）：

1. `get_selection_context` — 当前打开导图路径、选中节点 ID/文字
2. 需要看清结构时：
   - 查**任意文件** → `get_mindmap_json`（静默，不打开 UI）
   - 只看**当前已打开**的导图 → `get_active_map_json`

## 时间优先（回答「现在/最近」类问题）

节点有 XML 属性 `MODIFIED`（毫秒时间戳）。旧内容（如几年前的 `卢英.mm`）不应默认当作当前答案。

| 问题类型 | 推荐 MCP 调用 |
|----------|----------------|
| 当前状态、最近想法、「现在喜欢谁」 | 先 `list_recently_modified`（默认近 365 天），再 `search_nodes` 且 `modifiedWithinDays: 365` |
| 历史全文、考古 | `search_nodes` 不设 `modifiedWithinDays`（仍按修改时间**新→旧**排序） |

结果字段：`modifiedAt` / `modifiedAtMillis`。回答时注明依据节点的修改时间。

## 第二步：决定写入位置

| 情况 | 父节点 |
|------|--------|
| 用户指定了节点或分支 | 用指定的节点 |
| 用户说「记到这里」「当前节点」 | 用 `get_selection_context` 的 `nodeId` |
| 用户只说「加到导图」未指定 | 用当前选中节点；若选中的是根节点再考虑其子分支 |
| 用户问「猜你会记到哪」 | **先读上下文再猜**，并简短说明依据 |

## 第三步：执行写入

- **仅写入时**且目标导图未打开，才调 `open_mindmap`；只读查询禁止调用
- `parentNodeId` 必须来自上一步，不能来自旧会话
- 写入后可用 `get_selection_context` 或 `get_active_map_json` 验证

## 回复用户时

简要说明：**当前导图**、**当前选中节点**、**本次写入位置**（若与选中节点不同要解释原因）。

## 写入导图：分类清晰

把内容记入思维导图时，**不要挤在一条长节点里**；用层级分支，让读者一眼能扫到类别。

### 推荐结构（文档/能力清单类）

```
父节点（用户选中或指定）
├── 概览（1 条：数量、地址、前提）
├── 大类 A（如「工具 Tools（20个）」）
│   ├── 子类 A1（如「查询类：tool1 / tool2 …」）
│   └── 子类 A2
├── 大类 B（如「资源 Resources（11个，只读）」）
│   └── 每条资源一行，格式：uri — 说明
├── 大类 C（如「提示词 Prompts（6个）」）
│   └── 每个提示词单独一行：name — 用途
└── 使用方式 / 原则
    └── 操作步骤分行列出
```

### 表达规则

| 规则 | 做法 |
|------|------|
| **先分大类，再列细节** | 工具/资源/提示词/用法分开，不混在一层 |
| **子类按用途分组** | 如工具分：查询、上下文、导图读写、任务管理、其他 |
| **一条节点一个主题** | 单节点不超过 2～3 行；列表用子节点展开 |
| **名称 + 说明** | 格式：`名称 — 简短说明` 或 `类别：a / b / c` |
| **去重** | 写入前先 `get_active_map_json` 看是否已有同类节点，避免重复堆叠 |
| **改优于叠** | 已有结构不清时，删掉扁平重复子节点再重建分层 |

### 反例（避免）

- ❌ 8 条平铺子节点，每条塞满一整类能力
- ❌ `工具Tools(20): a b c d e` 全部挤一行
- ❌ 未读当前导图就写到别的文件

## 反例（禁止）

- 未读上下文就写 `test.mm`
- 用 `search_nodes` 猜位置代替读当前选中
- 因上次会话用过某 ID 就直接复用
- 用 `loadCatchExceptions` / `newMap` 扫全库（会弹「无法打开URL」）

## 静默查询 vs 打开 UI

| 工具 | 是否打开导图标签 |
|------|------------------|
| `search_nodes` | 否 |
| `get_mindmap_json` | 否 |
| `get_selection_context` / `get_active_map_json` | 否（只读当前状态） |
| `open_mindmap` | **是** — 仅用户明确要求打开时 |
| `navigate_to_node` | **是** — 仅用户要求跳转时 |

查信息、回答问题、搜索内容：**只用静默工具**。用户说「打开 xx.mm」才用 `open_mindmap`。

## MCP 弹窗说明

Docear 原生 UI 在加载失败时会 `UITools.errorMessage` 弹窗。MCP 后台操作若触发下列路径，用户会看到弹窗：

| 触发操作 | 典型原因 |
|----------|----------|
| `search_nodes`（旧版） | 扫描时逐个 `loadCatchExceptions`，缺失/损坏的 .mm 各弹一次 |
| `open_mindmap` / `navigate_to_node` | 路径不存在、文件已移动（如工作区索引里还有旧路径） |
| 频繁 `open_mindmap` | 多标签打开时可能触发 Alt+1/2 等快捷键冲突提示 |

**规避**：优先 `get_selection_context`；搜索用静默 SAX（已修复）；打开导图前确认文件存在；不要批量 `open_mindmap`。

## MCP 工具速查

- 读上下文：`get_selection_context`、`get_active_map_json`
- 写节点：`add_node`、`create_todo`、`set_reminder`（`parentNodeId` / `nodeId` 来自上下文）
- 打开导图：`open_mindmap`（`filePath` 来自上下文 `mapFile`）
