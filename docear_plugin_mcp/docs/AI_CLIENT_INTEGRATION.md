# Docear MCP 与 AI 客户端集成

## 权威来源

Cursor / AI 客户端的安装文件位于：

`docear_plugin_mcp/cursor-plugin/`

包含 `mcp.json`、rules、skills、`.cursor-plugin/plugin.json`。构建时写入 `VERSION`，随 `org.docear.plugin.mcp` 打包。

## 自动同步

Docear 启动约 12 秒后，`CursorAiClientSync` 会将 bundled 目录复制到：

`%USERPROFILE%\.cursor\plugins\local\docear\`

仅当 bundled `VERSION` 变化时重新复制。状态保存在用户目录 `cursor-plugin-sync.properties`。

配置项（`mcp.properties` 或 Docear 偏好）：

- `mcp.cursorPlugin.sync.enabled` — 默认 `true`
- `mcp.cursorPlugin.sync.delayMs` — 默认 `12000`

同步完成后在 Cursor 中 **Reload Window**。

## 开发工作区 `.cursor/`

仓库根目录 `.cursor/` 供 Docear-Desktop 开发时使用。修改 MCP 规范时请改 `docear_plugin_mcp/cursor-plugin/`，再编译 MCP 插件。

## Copilot CLI

Docear 内置 AI 面板的 Copilot 通过 `docear_plugin_ai` 连接同一 MCP URL；提示词中的 MCP 说明在 `AiPromptBuilder.buildMcpToolInstructions()`，随 AI 插件编译更新。

## API Key / 公网 / 网页版

见 [MCP_AUTH_AND_WEB.md](MCP_AUTH_AND_WEB.md)。启用认证或公网绑定后，客户端需发送 `Authorization: Bearer <mcp-api-key>`。

单独域名与 HTTPS 反代：[WEB_DOMAIN.md](WEB_DOMAIN.md)。
