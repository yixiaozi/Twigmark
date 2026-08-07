# Twigmark MCP：API Key、公网与网页版

## 能力概览

| 能力 | 说明 |
|------|------|
| MCP API Key | 可选；公网绑定强制开启（保护 `/mcp`） |
| 公网监听 | 主机设为 `0.0.0.0`（或非 loopback） |
| 网页账号 | `/web/` **账号密码**；**只允许注册一次**（单人使用） |
| 大模型 | 推荐 **OpenRouter**（`https://openrouter.ai/api/v1`，模型如 `openai/gpt-4o-mini`）；配置写入 `webchat-*.db` 的 `llm_profiles`，**桌面右侧聊天与网页共用** |
| 大模型库 | 每台电脑 `webchat-<MAC>.db`；配置与对话写入本机库 |
| 跨机历史 | 数据目录同步后，列表合并所有 `webchat-*.db` |
| 单独域名 | 见 [WEB_DOMAIN.md](WEB_DOMAIN.md)（DNS + Caddy/Nginx 反代） |

## 网页账号与数据库

路径（默认与审计库同目录，即主目录下的 `data/`）：

```text
webchat-<本机MAC>.db   ← 本机写入（用户镜像、会话、LLM 配置、新对话）
webchat-<其他MAC>.db   ← 同步过来的同伴库（只读合并）
```

表：`users`、`sessions`、`llm_profiles`、`conversations`、`messages`。

- **注册 / 登录**：在任意已同步库中匹配用户名；密码哈希用加盐迭代 SHA-256  
- **会话**：只写本机 `sessions`；请求头 `Authorization: Bearer <token>`  
- **大模型配置**：按用户存入 DB（Base URL / API Key / 模型）；优先本机默认配置  
- **对话**：新消息始终写入本机库；历史列表按 `username` 汇总全部 `webchat-*.db`

把整个 `data/`（或其中 `webchat-*.db`）用网盘/Git/同步盘在多台电脑间同步，即可在任意一台看到全部对话。

## 产品设置

**产品设置 → MCP**：

1. （可选）MCP API Key / 公网绑定 — 保护 Cursor 等 MCP 客户端  
2. Web 开关；设置里的 LLM 字段可作为**新账号的默认画像种子**（首次登录时写入 DB）  
3. 重启服务  

网页端登录后，可在页面「大模型」里增删改配置（以数据库为准）。

## API（网页）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/status` | 公开状态（无密钥） |
| POST | `/api/register` | `{username,password}`（仅当尚无任何用户时可用） |
| POST | `/api/login` | → `{token,username,…}` |
| POST | `/api/logout` | 需会话 |
| GET | `/api/me` | 需会话 |
| GET/POST | `/api/llm-profiles` | 列表 / 保存 |
| POST | `/api/llm-profiles/delete` | `{id}` |
| GET/POST | `/api/conversations` | 列表 / 新建 |
| GET | `/api/conversations/{id}` | 含消息（跨库合并） |
| POST | `/api/chat` | `{message,conversationId?,profileId?,mapFile?}`（`mapFile` 时聚焦该导图提问） |
| GET | `/api/maps` | 导图库列表 `?q=&limit=` |
| GET | `/api/maps/json` | 只读树 JSON `?path=&maxDepth=&includeFolded=` |
| GET | `/api/maps/search` | 节点搜索 `?q=&path=&limit=` |

## MCP 客户端

启用认证或公网后，`/mcp` 仍需：

```http
Authorization: Bearer tm_your_mcp_key
```

与网页账号相互独立。

## 安全建议

1. 公网务必开 MCP API Key，并加 HTTPS 反代  
2. 网页密码与 LLM Key 勿提交到公开仓库  
3. 同步 `webchat-*.db` 时注意文件含密钥与对话明文，目录权限要收紧  
