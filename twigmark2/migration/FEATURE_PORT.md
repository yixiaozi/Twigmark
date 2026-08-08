# Twigmark 功能移植清单（1.x → 2.0）

状态：`todo` / `wip` / `done` / `n/a`

| 优先级 | 功能 | 1.x 位置（约） | 状态 |
|--------|------|----------------|------|
| P0 | `.mm` 打开/保存/自动保存 | `freeplane` map IO | todo |
| P0 | FlatLaf + Twigmark 主题令牌 | `DocearUiTheme` | todo |
| P0 | 品牌 / 启动器 / 便携包 | `docear_framework` | todo |
| P1 | MCP server + 审计 | `docear_plugin_mcp` | todo |
| P1 | 工作区 / 项目树 | `freeplane_plugin_workspace` | todo |
| P1 | 提醒 / 待办 | reminder UI in freeplane | todo |
| P2 | 番茄钟 | pomodoro UI | todo |
| P2 | 报表中心 | report tabs | todo |
| P2 | AI 侧栏 / Web 聊天 | `docear_plugin_ai` + mcp web | todo |
| P3 | 财务账本 | finance panels | todo |
| P3 | Draw.io | `docear_plugin_drawio` | todo |
| P3 | BibTeX / PDF | bibtex / pdfutilities | todo |

上游已有、可评估复用而非重写：

- AI Chat / MCP（Freeplane 1.13）— 与 Twigmark MCP **协议不同**，需适配层  
- Tags / Bookmarks / Outline — 优先在 2.0 直接采用上游  
- jpackage 发行 — 2.0 默认路径  
