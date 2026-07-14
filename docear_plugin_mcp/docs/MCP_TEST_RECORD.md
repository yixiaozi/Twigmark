# Docear MCP 功能测试记录

- 测试时间: 2026-07-03 11:23:08
- MCP 地址: http://127.0.0.1:7720/mcp

## 0. 连通性
- PASS health: {"status": "ok", "service": "docear-mcp"}

## 1. 协议与工具列表
- initialize: docear-mcp v1.0.0
- tools/list: 34 tools
- 工具名: list_todos, list_reminders, list_overdue, get_workspace_plan, get_active_map_json, get_mindmap_json, get_node_details, list_pinned, list_published, list_tag_groups, list_tags, list_nodes_by_tag, list_favorites, get_tag_catalog, create_tag_group, rename_tag_group, move_tag_group, delete_tag_group, set_tag_group, set_tag_color, get_selection_context, search_nodes, list_recently_modified, open_mindmap, navigate_to_node, add_node, change_node_text, remove_node, create_todo, complete_todo, set_reminder, set_priority, move_node, set_node_folded, set_node_link, set_node_note, set_node_tags, toggle_pin, set_node_icon, set_recurring_reminder, create_mindmap, list_projects, quick_capture, sync_todoist, export_workspace_snapshot

## 2. 读：上下文与当前导图
### get_selection_context
```json
{"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","nodeId":"ID_1845609643","nodeText":"test","hasChildren":true}
```
- mapFile: `E:\yixiaozi\00统领全局\test.mm`
- nodeId: `ID_1845609643`  nodeText: `test`

### get_active_map_json
- 响应长度: 84 chars
- 预览: {"jsonrpc": "2.0", "id": 4, "result": {"content": [{"type": "text", "text": null}]}}

### get_mindmap_json (silent, maxDepth=2)
- 响应长度: 4207 chars
- 预览: {"file":"E:\\yixiaozi\\00统领全局\\test.mm","root":{"id":"ID_1845609643","text":"test","folded":false,"modifiedAtMillis":1782898309229,"modifiedAt":"2026-07-01 17:31:49","link":"","note":"","detailsHtml":"","tags":"","pinned":false,"taskTime":0,"taskLevel":0,"jinji":0,"remindType":"","icons":["closed","yes"],"children":[{"id":"ID_1874188519","text":"周期提醒","folded":false,"modifiedAtMillis":1782884764978,"modifiedAt":"2026-07-01 13:46:04","link":"","note":"","detailsHtml":"","tags":"","pinned":false,"... [truncated 4207 chars]

## 3. 读：搜索与列表
### list_recently_modified (90d)
```json
[{"mapFile":"E:\\yixiaozi\\02目标发展\\07创作达人\\00开源作者\\二次开发\\Docear改造.mm","nodeId":"ID_1763166957","nodeText":"能否给MCP添加日志功能，可以查看最近查过说明返回什么","modifiedAtMillis":1783047012373,"modifiedAt":"2026-07-03 10:50:12","parentNodeId":"ID_1586028830","parentPath":"Docear改造 / 剩余开发任务 / 优化功能","depth":3},{"mapFile":"E:\\yixiaozi\\04信息技术\\AI\\AI Coding\\Cursor.mm","nodeId":"ID_80460821","nodeText":"GitHub","modifiedAtMillis":1783042973340,"modifiedAt":"2026-07-03 09:42:53","parentNodeId":"ID_910350124","parentPath":"Cursor / Plugins","depth":2},{"mapFile":"E:\\yixiaozi\\04信息技术\\AI\\AI Coding\\Cursor.mm","nodeId":"ID_1097519566","nodeText":"看 PR diff 时分组展示，比纯 diff 好读","modifiedAtMillis":1783042935935,"modifiedAt":"2026-07-03 09:42:15","parentNodeId":"ID_1586799611","parentPath":"Cursor / Plugins / PR Review Canvas","depth":3},{"mapFile":"E:\\yixiaozi\\04信息技术\\AI\\AI Coding\\Cursor.mm","nodeId":"ID_1586799611","nodeText":"PR Review Canvas","modifiedAtMillis":1783042935400,"modifiedAt":"2026-07-03 09:42:15","parentNodeId":"ID_910350124","parentPath":"Cursor / Plugins","depth":2},{"mapFile":"E:\\yixiaozi\\04信息技术\\AI\\AI Coding\\Cursor.mm","nodeId":"ID_1441799673","nodeText":"把架构说明、API 文档渲染成可导航 Canvas，适合梳理 Freeplane/Docear 模块","modifiedAtMillis":1783042918026,"modifiedAt":"2026-07-03 09:41:58","parentNodeId":"ID_1266336773","parentPath":"Cursor / Plugins / Docs Canvas","depth":3}]
```

### search_nodes (MCP, 365d)
```json
[{"mapFile":"E:\\yixiaozi\\02目标发展\\07创作达人\\00开源作者\\二次开发\\Docear改造.mm","nodeId":"ID_1763166957","nodeText":"能否给MCP添加日志功能，可以查看最近查过说明返回什么","modifiedAtMillis":1783047012373,"modifiedAt":"2026-07-03 10:50:12","parentNodeId":"ID_1586028830","parentPath":"Docear改造 / 剩余开发任务 / 优化功能","depth":3},{"mapFile":"E:\\yixiaozi\\04信息技术\\AI\\AI Coding\\Cursor.mm","nodeId":"ID_224282335","nodeText":"Plugins 里那一套——把 Skills、Rules、Agents、Commands、Hooks、MCP 打包成可安装插件。","modifiedAtMillis":1783042839289,"modifiedAt":"2026-07-03 09:40:39","parentNodeId":"ID_910350124","parentPath":"Cursor / Plugins","depth":2},{"mapFile":"E:\\yixiaozi\\04信息技术\\AI\\AI Coding\\Cursor.mm","nodeId":"ID_342737839","nodeText":"PluginMCPServers","modifiedAtMillis":1783042053889,"modifiedAt":"2026-07-03 09:27:33","parentNodeId":"ID_147219164","parentPath":"Cursor","depth":1},{"mapFile":"E:\\yixiaozi\\04信息技术\\AI\\AI Coding\\Cursor.mm","nodeId":"ID_931777122","nodeText":"MCPs","modifiedAtMillis":1783042047802,"modifiedAt":"2026-07-03 09:27:27","parentNodeId":"ID_147219164","parentPath":"Cursor","depth":1},{"mapFile":"E:\\yixiaozi\\02目标发展\\07创作达人\\00开源作者\\二次开发\\Docear改造.mm","nodeId":"ID_514308474","nodeText":"添加了MCP功能，让AI可以查看操作我的思维导图","modifiedAtMillis":1782989341979,"modifiedAt":"2026-07-02 18:49:01","parentNodeId":"ID_80425228","parentPath":"Docear改造 / Docear改造时间轴 / 2026 / 7 / 2","depth":5}]
```

### list_pinned
```json
[{"mapFile":"E:\\yixiaozi\\01工作事业\\06敏捷艾克\\奥林巴斯\\奥林巴斯会议.mm","nodeId":"ID_1786232565","nodeText":"一般维修经销商添加电子签功能UAT","tags":"会议"}]
```

### list_projects
```json
[{"id":"17DAB3A24CC7NGK3HWY5ERX3AURZZAJ2PT99","name":"yixiaozi","home":"E:\\yixiaozi"}]
```

### list_todos
```json
[{"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","nodeId":"ID_659717380","nodeText":"7/4 06:00起床 07:15到健德门"},{"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","nodeId":"ID_1941673443","nodeText":"MCP待办测试"},{"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","nodeId":"ID_160937706","nodeText":"准备装备雨衣路餐水登山杖"},{"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","nodeId":"ID_1477173095","nodeText":"报名缴费并扫码进群"},{"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","nodeId":"ID_1260140853","nodeText":"明天买雨衣"},{"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","nodeId":"ID_1503722395","nodeText":"确认休闲组或徒步组"},{"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","nodeId":"ID_1759369411","nodeText":"签署户外活动免责声明"},{"mapFile":"E:\\yixiaozi\\01工作事业\\06敏捷艾克\\奥林巴斯\\SPO\\SPO日志.mm","nodeId":"ID_1037444742","nodeText":"3个站点代码统一问题"},{"mapFile":"E:\\yixiaozi\\01工作事业\\06敏捷艾克\\奥林巴斯\\SPO\\SPO日志.mm","nodeId":"ID_291433882","nodeText":"FY27还有待处理的"},{"mapFile":"E:\\yixiaozi\\01工作事业\\06敏捷艾克\\奥林巴斯\\SPO\\SPO日志.mm","nodeId":"ID_854300540","nodeText":"GIET/RET三方的选了是OSH，非带采，不能选择平台商，因为没有这个模板类型。"},{"mapFile":"E:\\yixiaozi\\01工作事业\\06敏捷艾克\\奥林巴斯\\SPO\\SPO日志.mm","nodeId":"ID_1318919972","nodeText":"为什么发生频率比较高呢？"},{"mapFile":"E:\\yixiaozi\\01工作事业\\06敏捷艾克\\奥林巴斯\\SPO\\SPO日志.mm","nodeId":"ID_689233486","nodeText":"为什么程序有问题？和自动计算的不一致为什么呢？"},{"mapFile":"E:\\yixiaozi\\01工作事业\\06敏捷艾克\\奥林巴斯\\SPO\\SPO日志.mm","nodeId":"ID_1801436520","nodeText":"加阴伟收到邮件那个，也可以开始做了"},{"mapFile":"E:\\yixiaozi\\01工作事业\\06敏捷艾克\\奥林巴斯\\SPO\\SPO日志.mm","nodeId":"ID_48948577","nodeText":"安徽及福建三方协议豁免备选新规申请"},{... [truncated 9358 chars]
```

### list_reminders
```json
[{"mapFile":"E:\\yixiaozi\\01工作事业\\06敏捷艾克\\奥林巴斯\\SPO\\SPO日志.mm","nodeId":"ID_132489811","nodeText":"配置一下电子签配置","remindAt":"2026-07-02 12:00","remindAtMillis":1782964800000,"recurring":false,"remindType":""},{"mapFile":"E:\\yixiaozi\\01工作事业\\06敏捷艾克\\奥林巴斯\\SPO\\SPO日志.mm","nodeId":"ID_48948577","nodeText":"安徽及福建三方协议豁免备选新规申请","remindAt":"2026-07-02 14:00","remindAtMillis":1782972000000,"recurring":false,"remindType":""},{"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","nodeId":"ID_1260140853","nodeText":"明天买雨衣","remindAt":"2026-07-03 09:00","remindAtMillis":1783040400000,"recurring":false,"remindType":""},{"mapFile":"E:\\yixiaozi\\01工作事业\\06敏捷艾克\\奥林巴斯\\SPO\\Case\\20260519维修经销商添加电子签功能\\维修经销商添加电子签功能.mm","nodeId":"ID_1141365312","nodeText":"把电子签功能仔细测试一下，大概花费2个小时","remindAt":"2026-07-03 10:22","remindAtMillis":1783045320000,"recurring":false,"remindType":""},{"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","nodeId":"ID_314309947","nodeText":"MCP提醒节点","remindAt":"2026-07-03 17:21","remindAtMillis":1783070514395,"recurring":false,"remindType":""},{"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","nodeId":"ID_1503722395","nodeText":"确认休闲组或徒步组","remindAt":"2026-07-03 18:00","remindAtMillis":1783072800000,"recurring":false,"remindType":""},{"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","nodeId":"ID_160937706","nodeText":"准备装备雨衣路餐水登山杖","remindAt":"2026-07-03 21:00","remindAtMillis":1783083600000,"recurring":false,"remindType":""},{"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","nodeId":"ID_659717380","nodeText":"7/4 ... [truncated 3958 chars]
```

### get_workspace_plan
```json
=== 工作区安排（MCP 摘要）===

【一次性提醒】10 项
- 2026-07-02 12:00 配置一下电子签配置 (E:\yixiaozi\01工作事业\06敏捷艾克\奥林巴斯\SPO\SPO日志.mm)
- 2026-07-02 14:00 安徽及福建三方协议豁免备选新规申请 (E:\yixiaozi\01工作事业\06敏捷艾克\奥林巴斯\SPO\SPO日志.mm)
- 2026-07-03 09:00 明天买雨衣 (E:\yixiaozi\00统领全局\test.mm)
- 2026-07-03 10:22 把电子签功能仔细测试一下，大概花费2个小时 (E:\yixiaozi\01工作事业\06敏捷艾克\奥林巴斯\SPO\Case\20260519维修经销商添加电子签功能\维修经销商添加电子签功能.mm)
- 2026-07-03 17:21 MCP提醒节点 (E:\yixiaozi\00统领全局\test.mm)
- 2026-07-03 18:00 确认休闲组或徒步组 (E:\yixiaozi\00统领全局\test.mm)
- 2026-07-03 21:00 准备装备雨衣路餐水登山杖 (E:\yixiaozi\00统领全局\test.mm)
- 2026-07-04 06:00 7/4 06:00起床 07:15到健德门 (E:\yixiaozi\00统领全局\test.mm)
- 2026-07-06 10:00 一般维修经销商添加电子签功能UAT (E:\yixiaozi\01工作事业\06敏捷艾克\奥林巴斯\奥林巴斯会议.mm)
- 2026-07-06 11:49 买一个水墨屏的微信读书 (E:\yixiaozi\07有条不紊\05财务管理\购物\购物.mm)

【周期提醒】8 项
- 2026-07-01 13:00 更新进度表 (E:\yixiaozi\01工作事业\06敏捷艾克\工作任务.mm)
- 2026-07-01 17:00 工数统计一览表 (E:\yixiaozi\01工作事业\06敏捷艾克\工作任务.mm)
- 2026-07-02 14:56 每周电影院看一个电影 (E:\yixiaozi\02目标发展\06兴趣广泛\03影视追星\电影.mm)
- 2026-07-03 09:00 做一些简单的工作进入状态 (E:\yixiaozi\01工作事业\06敏捷艾克\工作任务.mm)
- 2026-07-03 13:30 每周5请大家和咖啡 (E:\yixiaozi\01工作事业\06敏捷艾克\生活记录\同事\同事.mm)
- 2026-07-15 15:05 发工资,将要存的钱打入存储卡上 (E:\yixiaozi\07有条不紊\05财务管理\理财.mm)
- 2026-07-26 12:16 周期提醒 (E:\yixiaozi\00统领全局\test.mm)
- 2026-07-28 17:00 发加班文件给公司邮件 (E:\yixiaozi\01工作事业\06敏捷艾克\工作任务.mm)

【待办】82 项
- 7/4 06:00起床 07:15到健德门 (E:\yixiaozi\00统领全局\test.mm)
- MCP待办测试 (E:\yixiaozi\00统领全局\test.mm)
- 准备装备雨衣路餐水登山杖 (E:\yixiaozi\00统领全局\test.mm)
- 报名缴费并扫码进群 (E:\yixiaozi\00统领全局\test.mm)
- 明天买雨衣 (E:\yixiaozi\... [truncated 2530 chars]
```

### search_nodes (scoped to current file)
```json
[{"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","nodeId":"ID_486158678","nodeText":"Docear MCP 能力清单（Cursor 直连 127.0.0.1:7720）","modifiedAtMillis":1782986243380,"modifiedAt":"2026-07-02 17:57:23","parentNodeId":"ID_1254382246","parentPath":"test / 测试发布","depth":2},{"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","nodeId":"ID_128923893","nodeText":"单次操作约几十毫秒；脚本批量测试才慢","modifiedAtMillis":1782986243236,"modifiedAt":"2026-07-02 17:57:23","parentNodeId":"ID_1976189984","parentPath":"test / 测试发布 / Docear MCP 能力清单（Cursor 直连 127.0.0.1:7720） / 使用方式","depth":4},{"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","nodeId":"ID_216191648","nodeText":"对话直接说指令即可，Agent调MCP，无需PowerShell脚本","modifiedAtMillis":1782986243081,"modifiedAt":"2026-07-02 17:57:23","parentNodeId":"ID_1976189984","parentPath":"test / 测试发布 / Docear MCP 能力清单（Cursor 直连 127.0.0.1:7720） / 使用方式","depth":4}]
```

## 4. 读：单节点详情
### get_node_details (before writes)
```json
{"jsonrpc": "2.0", "id": 14, "result": {"content": [{"type": "text", "text": null}]}}
```

## 5. 写：在当前导图/选中节点下测试
### 写操作结果
| 工具 | 结果 | 响应摘要 |
|------|------|----------|
| `add_node` | PASS | {"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","saved":true,"headlessLoad":false,"nodeId":"ID_251121005","nodeText":"MCP_TES... [truncated 139 chars] |
| `change_node_text` | PASS | {"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","saved":true,"headlessLoad":false,"nodeId":"ID_251121005","nodeText":"MCP_TES... [truncated 146 chars] |
| `set_node_folded` | PASS | {"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","saved":true,"headlessLoad":false,"nodeId":"ID_251121005","folded":true} |
| `set_node_folded` | PASS | {"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","saved":true,"headlessLoad":false,"nodeId":"ID_251121005","folded":false} |
| `set_node_note` | PASS | {"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","saved":true,"headlessLoad":false,"nodeId":"ID_251121005"} |
| `set_node_tags` | PASS | {"jsonrpc": "2.0", "id": 20, "result": {"content": [{"type": "text", "text": null}]}} |
| `set_node_link` | PASS | {"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","saved":true,"headlessLoad":false,"nodeId":"ID_251121005","link":"https://exa... [truncated 139 chars] |
| `set_priority` | PASS | {"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","saved":true,"headlessLoad":false,"nodeId":"ID_251121005","priority":3} |
| `set_node_icon` | PASS | {"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","saved":true,"headlessLoad":false,"nodeId":"ID_251121005","icon":"button_ok",... [truncated 135 chars] |
| `create_todo` | PASS | {"mapFile":"E:\\yixiaozi\\00统领全局\\test.mm","saved":true,"headlessLoad":false,"nodeId":"ID_1198171101","nodeText":"MCP_TE... [truncated 157 chars] |

### get_node_details (after writes)
```json
{"jsonrpc": "2.0", "id": 25, "result": {"content": [{"type": "text", "text": null}]}}
```

**测试残留**: 节点 `MCP_TEST_20260703_112318 edited` (nodeId=`ID_251121005`)，含 todo 子节点；可手动删除。

## 6. 未测 / 需手动的工具
以下工具本次未调用（避免改 UI / 外部副作用）：

- `list_overdue`
- `list_published`
- `open_mindmap`
- `navigate_to_node`
- `remove_node`
- `complete_todo`
- `set_reminder`
- `move_node`
- `toggle_pin`
- `set_recurring_reminder`
- `create_mindmap`
- `quick_capture`
- `sync_todoist`
- `export_workspace_snapshot`

## 7. 摘要
| 类别 | 状态 |
|------|------|
| 连通 / 协议 | PASS |
| 当前导图 | PASS (`test.mm`，选中根节点 `test`) |
| 读工具 | 大部分 PASS（见第 8 节异常） |
| 写工具 | PASS（已在当前导图留下测试节点） |

## 8. 本次发现的异常（需后续修复）

| 工具 | 现象 | 说明 |
|------|------|------|
| `get_active_map_json` | 返回 `text: null` | 当前已打开 `test.mm`，但 JSON 未返回 |
| `get_node_details` | 返回 `text: null` | 写前/写后均失败；节点详情读不到 |
| `set_node_tags` | 返回 `text: null` | 可能已写入，但 MCP 响应体为空 |

其余读工具（`get_mindmap_json`、`search_nodes`、`list_*` 等）均正常返回 JSON。

## 9. 如何复跑

```bash
python docear_plugin_mcp/scripts/mcp_smoke_test.py
```

输出覆盖：`docear_plugin_mcp/docs/MCP_TEST_RECORD.md`
