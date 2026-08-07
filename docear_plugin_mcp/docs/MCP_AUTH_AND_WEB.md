# Twigmark MCP：API Key、公网与网页版

## 能力概览

| 能力 | 说明 |
|------|------|
| MCP API Key | 可选；公网绑定强制开启 |
| 公网监听 | 主机设为 `0.0.0.0`（或非 loopback） |
| 网页版 | `http://<host>:<port>/web/` 对话并调用导图工具 |
| 大模型 Key | 只保存在 Twigmark 设置里，不进浏览器 |

## 产品设置

**产品设置 → MCP**：

1. **生成 Key** — 写入 `mcp.auth.apiKey`，并勾选「要求 API Key」  
2. **公网绑定** — 一键设为 `0.0.0.0` 并确保有 Key  
3. **Web** — 启用网页；填写 OpenAI 兼容的 Base URL / LLM API Key / 模型  
4. **重启服务** — 使监听与路由生效  

默认仍为 `127.0.0.1:7720`，本机 Cursor 可无 Key。

## 客户端调用 MCP

启用认证或公网后，请求需带：

```http
Authorization: Bearer tm_your_key
```

或：

```http
X-Api-Key: tm_your_key
```

Cursor `mcp.json` 示例（远端 / 隧道）：

```json
{
  "mcpServers": {
    "twigmark": {
      "url": "https://your-host.example/mcp",
      "headers": {
        "Authorization": "Bearer tm_your_key"
      }
    }
  }
}
```

本机默认可继续用 `http://127.0.0.1:7720/mcp`（未开认证时无需 Header）。

## 网页版

- 静态页：`GET /web/`  
- 状态：`GET /api/status`（不含密钥）  
- 对话：`POST /api/chat`（认证开启时需 MCP Key）  
  ```json
  { "message": "当前选中了什么？", "history": [] }
  ```
- 服务端用配置的 LLM Key 调 `/chat/completions`，并在进程内执行 MCP 工具（不二次走 HTTP）。

## 安全建议

1. 公网务必用 API Key；尽量前面再加 HTTPS 反代（nginx / Caddy）  
2. 不要把 LLM Key 或 MCP Key 写进仓库  
3. 只读模式：`mcp.readonly=true`（部分工具仍在完善）  
4. `/health` 不要求 Key，便于探活；勿在公网暴露未鉴权的管理面  

## 相关配置键

见 `docear_plugin_mcp/resources/mcp.properties`：`mcp.auth.*`、`mcp.web.*`。
