---
name: docear-mcp-context
description: >-
  Before any Docear MCP mind-map read or write, always fetch the user's currently
  open map and selected node via get_selection_context. When writing to mind maps,
  use clear hierarchical categories (overview → major sections → sub-items), not
  flat long one-liners. Use when the user mentions mind maps, .mm files, Docear,
  MCP tools, todos, reminders, pomodoro/focus time, personal finance ledger,
  relationship graph, or asks to add/organize nodes.
---

# Docear MCP：先读当前上下文

## 铁律

**任何导图操作之前，必须先确认用户正在看什么。**

禁止默认 `test.mm`、禁止凭对话历史猜父节点、禁止硬编码节点 ID（除非上一步刚从上下文读到）。

## 访问审计（每次 MCP 调用必传 `_audit`）

Docear MCP 会记录访问日志，供后续统计。**每次调用工具时**在 `arguments` 里附带 `_audit`（服务端会自动剥离，不影响业务参数）：

```json
"_audit": {
  "caller": "cursor-agent",
  "traceId": "20260708-mcp-audit",
  "questionSummary": "用户问题的概括（同一轮对话内保持一致）",
  "operationGoal": "本次这一个调用的目的（一句话）"
}
```

服务端异步写入本机 SQLite：`audit-<MAC>.db`（WAL；机器标识用网卡 MAC，不写可被同步的 id 文件）。主进程仅入队。查看审计时自动加载数据目录下全部 `audit-*.db` / 旧版 `audit.db`（多机把各自库同步到同一目录即可汇总，无需导出导入）。统计页展示各动作平均耗时。

| 字段 | 要求 |
|------|------|
| `caller` | 访问者标识，如 `cursor-agent`、模型名、脚本名 |
| `traceId` | 同一用户问题的一轮 MCP 调用共用（便于报表按问题聚合） |
| `questionSummary` | 用户原始问题的**概括**（≤240 字），同一 trace 内保持一致 |
| `operationGoal` | **本次这一个 tool call** 想做什么（≤160 字），每个调用各写一句 |

服务端会把 `_audit` 标进每个工具的 JSON Schema（必填）。**缺 `questionSummary` 会 `isError` 拒绝执行**，不只依赖 Cursor 技能提醒。Grok 等外部 MCP 客户端同样适用。

**操作意图分类（`intent`）**由服务端按工具自动映射到服务类，**每个类只有一个枚举**，Agent 不必传：

| intent | 服务类 |
|--------|--------|
| `CONTEXT` | McpContextService |
| `MINDMAP` | McpMindMapService |
| `NODE` | McpNodeService |
| `TASK` | McpTaskService |
| `WORKSPACE` | McpWorkspaceService |
| `GRAPH` | McpRelationshipGraphService |
| `TAG` | McpTagService |
| `POMODORO` | McpPomodoroService |
| `RESOURCE` / `PROMPT` | 资源读取 / 提示词 |

查询已记录日志：

- `list_audit_log` — 明细（含 `requestJson` / `responseJson`）
- `list_audit_traces` — 按 `traceId` 聚合的用户问题与调用链
- `get_audit_stats` — 分钟/小时/天预聚合（查报表用，勿扫明细表）

**示例**（用户问「把 MCP 审计记到当前节点」）：

1. `get_selection_context` → `operationGoal`: "读取当前打开导图与选中节点"
2. `add_node` → `operationGoal`: "在选中节点下写入 MCP 审计说明"

两轮 `questionSummary` 相同；`operationGoal` 各不同。

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
| 历史全文、考古 | `search_nodes` 传 `modifiedWithinDays: 0`（不限时间；按节点 MODIFIED 新→旧；建议加 `projectId`） |
| 只在某个文件里搜 | `search_nodes` 传 `filePath`（可用 `子目录/文件名.mm` 消歧） |

结果字段：`modifiedAt` / `modifiedAtMillis`、`parentPath`、`depth`、`parentNodeId`。

**搜索语义**：`modifiedWithinDays` 按每个节点的 XML `MODIFIED` 过滤，不再用全局「最近修改 Top 5000」预截断。

**性能**：无 `filePath` 时默认 `modifiedWithinDays=365`（传 `0` 表示不限）。节点 TEXT 有进程内 LRU + 磁盘 spill 索引，重复查询走热缓存；大库仍建议加 `filePath` / `projectId`。侧栏类查询（提醒/逾期/今日时间线/已发布）优先快照，避免全库 SAX。

**路径消歧**：仅 `文件名.mm` 且有多份同名时，优先已打开标签 → 当前导图同目录 → 主项目 → 唯一最新文件；仍歧义则报错并列候选路径。建议传 partial path。

限定范围：`search_nodes` 可传 `filePath` 或 `projectId`（来自 `list_projects`）。

钉选节点：`list_pinned`（侧栏钉选列表）；单节点详情：`get_node_details`（note、link、icons、tags、TASKLEVEL/JINJI、隐私级别、加密状态 `encrypted`/`encryptionUnlocked`、番茄钟 `pomodoro` 等）。

## 番茄钟 / 专注时间（Pomodoro）

节点自由计时专注会话，数据存在导图 XML（`POMODORO_*` / `POMODORO_LOG`）。**同一时刻只会有一个 running**；可有多个 paused。

| 场景 | 推荐 MCP 调用 |
|------|----------------|
| 用户现在在干什么 / 正在计时吗 | `get_running_pomodoro` 或资源 `docear://pomodoro/running`；`get_selection_context` 也会带 `runningPomodoro` |
| 今日/本周累计 | `get_pomodoro_stats` 或 `docear://pomodoro/stats`（`allMaps` 默认 false=当前图） |
| 哪些节点开了番茄钟 | `list_pomodoro_sessions`（可选 `stateFilter`: `running`\|`paused`\|`idle`，`allMaps`） |
| 什么时间干了什么 | `get_pomodoro_history`（`filePath` / 可选 `nodeId` / `sinceMillis` / `limit`） |
| 帮我开始/暂停/结束 | `start_pomodoro` / `pause_pomodoro` / `stop_pomodoro`（可选 `filePath`+`nodeId`，省略 nodeId=当前选中） |

**原则**：问「现在/最近在做什么」时先读 running + stats，再按需查 history；不要为查计时去 `open_mindmap`。

## 个人财务（Finance Ledger）

数据写在专用导图 `个人财务.mm`（可用 `finance.map.path` 覆盖），左侧「财务」Tab 与 MCP 共用同一套账本。金额以**分**存 XML（`FINANCE_*`），对外用元字符串（如 `28.50`）。

| 场景 | 推荐 MCP 调用 |
|------|----------------|
| 确保账本存在 / 取路径 | `ensure_finance_map` |
| 本月收支总览 | `get_finance_summary` 或资源 `docear://finance/summary`（`period=yyyy-MM`） |
| 记一笔 | `add_finance_transaction`（`amount` 必填；`flow`: `expense`\|`income`\|`transfer`\|`borrow`\|`lend`\|`credit`；转账必须 `account`+`accountTo`） |
| 查流水 | `list_finance_transactions`（`from`/`to`/`limit`） |
| 分类 / 账户 | 读：`list_finance_categories`（`flow=all\|expense\|income`）、`list_finance_accounts`；写：`add_finance_category`、`add_finance_account` |
| 预算 | `set_finance_budget` / `list_finance_budgets`（`总预算`对照整月损益支出） |
| 订阅 / 优惠券 | `upsert_finance_subscription`、`list_finance_subscriptions`、`upsert_finance_coupon`、`list_finance_coupons`、`mark_finance_coupon_used` |
| 删除 | `delete_finance_node`（`nodeId`） |
| 报表（视口图表） | `get_finance_report`（`reportId` + 可选 `showInViewport=true`；返回 kpis） |

**原则**：收入/支出是损益；借入/借出/信用卡/转账单独计量，不混入损益结余。财务读写走上述工具，不要为记账扫全库或 `open_mindmap`。Prompt：`finance-review`。

## 标签 / 分组 / 收藏（侧栏）

与 UI「标签」「收藏」共用索引存储，但**分组层级各自独立**：`TagGroupStore`（`tag-groups.properties`）与 `getFavoritesInstance()`（`favorite-tag-groups.properties`）。

| 场景 | 推荐调用 |
|------|----------|
| 一次拿到组→标签→计数/颜色 | `get_tag_catalog` 或资源 `docear://tags/catalog` |
| 只要分组（含嵌套 tree） | `list_tag_groups` |
| 所有/某组标签 | `list_tags`（`scope`: `pins`\|`favorites`\|`all`，可选 `groupId`） |
| 某标签有哪些节点 | `list_nodes_by_tag`（`tag` 必填；`scope` 默认 `pins`） |
| 收藏列表 | `list_favorites`（可选 `tag`） |
| 建/改/移/删组、移标签、设色 | `create_tag_group`（可选 `parentId` 无限嵌套） / `rename_tag_group` / `move_tag_group` / `delete_tag_group`（含子分组） / `set_tag_group` / `set_tag_color` |
| 给节点打标签 | 仍用 `set_node_tags` |

**注意**：`list_nodes_by_tag` 的 pins 范围 = 侧栏标签索引中的节点；收藏 scope 返回 `kind:favorite`（文件级 URI，无 nodeId）。

## 第二步：决定写入位置

| 情况 | 父节点 |
|------|--------|
| 用户指定了节点或分支 | 用指定的节点 |
| 用户说「记到这里」「当前节点」 | 用 `get_selection_context` 的 `nodeId` |
| 用户只说「加到导图」未指定 | 用当前选中节点；若选中的是根节点再考虑其子分支 |
| 用户问「猜你会记到哪」 | **先读上下文再猜**，并简短说明依据 |

## 第三步：执行写入

- 写入任意导图：传 `filePath`（来自 `search_nodes` 的 `mapFile` 或 `get_selection_context`），**无需** `open_mindmap`
- 未传 `filePath` 时写入当前打开的导图；若无打开导图则报错
- 只读查询禁止调用 `open_mindmap`
- `parentNodeId` / `nodeId` 必须来自上一步，不能来自旧会话
- 写入后检查返回的 `mapFile`、`saved`、`headlessLoad`
- **批量写节点用 `add_nodes`（一次保存）**，禁止为同一父节点连续多次 `add_node`

### `add_nodes` 约定（支持多层）

`nodes` 为 JSON 数组；每项可以是字符串，或对象 `{ "text", "todo"?, "children"? }`。

```json
{
  "parentNodeId": "ID_xxx",
  "filePath": "可选.mm",
  "nodes": [
    "概览：本批说明",
    {
      "text": "工具",
      "children": [
        { "text": "查询类", "children": ["search_nodes", "list_tags"] },
        { "text": "写入类", "todo": true }
      ]
    }
  ]
}
```

| 规则 | 说明 |
|------|------|
| 单层 | `["a","b","c"]` 或 `[{"text":"a"},{"text":"b"}]` |
| 多层 | 用 `children` 递归；深度 ≤ 20，总节点 ≤ 300 |
| 返回 | 镜像树，含各层 `nodeId` / `nodeText` / `children`，以及 `createdCount` |
| 与 `add_node` | 只加 1 个节点时可用 `add_node`；≥2 或有层级时用 `add_nodes` |

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
| `get_mindmap_json` | 否（默认 `includeFolded=true` 含折叠分支；`false` 同 UI 折叠视图） |
| `get_node_details` / `list_pinned` | 否 |
| `get_relationship_graph` / `get_node_relationships` | 否 |
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

## 关系图（跨导图 / 跨节点关联）

Docear 关系图扫描工作区全部 `.mm` 的超链接（LINK）与箭头关联（arrowlink），**MCP 静默读取，不打开 UI**。

| 场景 | 推荐 MCP 调用 |
|------|----------------|
| 概览有多少连接 | 资源 `docear://graph/summary` 或 `get_relationship_graph`（默认 `map_files`，`maxNodes` 小） |
| 某导图连了哪些导图 | `get_node_relationships`，`filePath` = 目标 `.mm`，`hops: 1` |
| 某节点连了哪些节点 | `get_node_relationships`，`filePath` + `nodeId`（来自 `get_selection_context` 或 `search_nodes`），`mode` 自动为 `map_nodes` |
| 按关键词找关联簇 | `get_relationship_graph`，`mode: map_nodes`，`query: 关键词`，`maxNodes: 50` |
| 全库节点级图（慢） | `get_relationship_graph`，`mode: map_nodes`，`refresh: false`（有 10 分钟缓存） |

**参数要点**

- `mode`: `map_files`（导图级，快）| `map_nodes`（节点级，慢，112k+ 节点时务必加 `query` / `filePath` / 小 `maxNodes`）
- `filePath` + `nodeId` + `hops`: 以该点为中心的 N 跳邻域
- `query`: 按 label / mapLabel / 路径过滤，并包含匹配节点的直接邻居
- `showIsolated`: 默认 false（隐藏无连接项）
- `refresh`: true 强制重扫；默认用缓存
- 返回 `nodes[]`（`key`, `label`, `mapFile`, `nodeId?`, `openUrl`）与 `edges[]`（`source`, `target` 为 `key`）

**与 UI 关系图 Tab 共用同一扫描引擎**；MCP 不触发中心视口切换。

## MCP 工具速查

- 读上下文：`get_selection_context`、`get_active_map_json`、`get_mindmap_json`（含 note/link/icons/tags/MODIFIED）
- **关系图**：`get_relationship_graph`、`get_node_relationships`；资源 `docear://graph/summary`
- 读详情/钉选：`get_node_details`、`list_pinned`、`list_published`
- **番茄钟**：`get_running_pomodoro`、`list_pomodoro_sessions`、`get_pomodoro_stats`、`get_pomodoro_history`；写：`start_pomodoro` / `pause_pomodoro` / `stop_pomodoro`；资源 `docear://pomodoro/running`、`docear://pomodoro/stats`
- **财务**：`ensure_finance_map`、`get_finance_summary`、`add_finance_transaction`、`list_finance_transactions`、分类/账户/预算/订阅/优惠券 CRUD、`get_finance_report`；资源 `docear://finance/summary`
- **标签**：`get_tag_catalog`、`list_tag_groups`、`list_tags`、`list_nodes_by_tag`、`list_favorites`；写：`create_tag_group`（可选 `parentId`）、`rename_tag_group`、`move_tag_group`、`delete_tag_group`、`set_tag_group`、`set_tag_color`、`set_node_tags`
- 搜索：`search_nodes`（`filePath` / `projectId` / `modifiedWithinDays`）、`list_recently_modified`
- 写节点：`add_nodes`（批量/多层，优先）、`add_node`（单节点）、`create_todo`、`set_reminder`、`set_recurring_reminder`、`clear_reminder`
- 子树剪贴板：`copy_nodes` / `cut_nodes` / `paste_nodes` / `clone_nodes`（整棵子树；粘贴后同图会生成新 ID）
- 撤销/重做：`undo_map` / `redo_map`（针对该图内存撤销栈，然后保存）
- 箭头关联：`add_arrow_link` / `remove_arrow_link`（同图 source→target；不是超链接 LINK）
- 写结构/属性：`move_node`、`set_node_folded`、`set_node_link`、`set_node_note`、`set_node_details`、`set_node_tags`、`toggle_pin`、`set_node_icon`、`set_node_cloud`、`set_node_style`、`set_node_privacy`、`set_node_image`、`clear_node_image`、`set_node_attachment`、`create_mindmap`
- **加密**：`encrypt_node`（加锁并写入 ENCRYPTED_CONTENT）、`decrypt_node`（本会话解锁，文件仍加密）、`remove_node_encryption`（永久去掉密码保护）。`password` 可选，缺省用本机加密设置里的默认密码；审计日志会把密码打成 `***`
- 打开导图：`open_mindmap`（`filePath` 来自上下文 `mapFile`）
- 审计日志：`list_audit_log`、`list_audit_traces`、`get_audit_stats`
