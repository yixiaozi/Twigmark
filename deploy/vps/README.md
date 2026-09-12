# VPS：Twigmark / Docear MCP 运维配置

本目录收录 **149.104.79.102** 上已跑通、但此前只在服务器本地的配置。  
仓库 `yixiaozi/Twigmark` 为 **公开仓库**，**禁止**提交明文 API Key / 密码。

## 布局（服务器）

| 路径 | 用途 |
|------|------|
| `/opt/docear/runtime` | Twigmark 运行时 + 插件 |
| `/opt/docear/bin/start-docear-mcp.sh` | 启动脚本（systemd） |
| `/opt/docear/mcp-api-key.txt` | MCP Bearer Key（`chmod 600`） |
| `/data/mindmaps` → `/root/Dropbox/mindmaps` | 导图库（Dropbox） |
| `/data/docear` | 配置与索引（**不在** Dropbox） |
| `/data/docear/mcp-access.json` | 多把 Key（可选） |
| `https://webchat.mantoublog.top` | Nginx → `127.0.0.1:7720` |
| `/opt/docear-dify-kb` | Dify 外部知识库网关 → `7721` |

## 本目录文件

### `docear-mcp/`

- `start-docear-mcp.sh` — 从 `mcp-api-key.txt` 读 Key；固定 `:99` Xvfb
- `docear-mcp.service` — systemd unit
- `nginx-webchat.conf` — `webchat.mantoublog.top`（含 `/dify-kb/`）
- `webchat-mcp-info.html` — `/mcp-info` 静态页
- `mcp-access.example.json` — 多 Key 模板
- `DOCEAR_MCP_README.txt` — 服务器备忘原文

### `docear-dify-kb/`

- `app.py` — MCP → Dify External Knowledge API
- `docear-dify-kb.service` / `env.example` / `CREDENTIALS.example.txt`

### `secrets.local/`（gitignore，仅本机）

从现网导出的 Key，供维护者对照，**不会**进 Git：

- `mcp-api-key.txt`
- `docear-dify-kb.env`
- `docear-dify-kb.CREDENTIALS.txt`

## 鉴权要点

1. MCP：`Authorization: Bearer <mcp-api-key>`（owner，见启动参数 `-Dmcp.auth.role=owner`）
2. 网页 Webchat：账号密码（库在 `/data/docear/webchat-*.db`）
3. OAuth（Grok / Cursor）：源码 `McpOAuthRedirects` 已允许 `cursor://`；`mcp.oauth.accessTtlSeconds=0` 表示令牌永不过期且持久化到 `webchat-*.db`（重启仍有效）
4. Dify KB：`/dify-kb/` 使用 `DIFY_KB_API_KEY` Bearer

## 部署

插件 / runtime 更新：

```bash
MCP_API_KEY="$(cat deploy/vps/secrets.local/mcp-api-key.txt)" \
  ./docear_plugin_mcp/scripts/deploy-vps-runtime.sh root@149.104.79.102
```

仅同步本目录启动脚本与 unit（示例）：

```bash
scp deploy/vps/docear-mcp/start-docear-mcp.sh root@HOST:/opt/docear/bin/
scp deploy/vps/docear-mcp/docear-mcp.service root@HOST:/etc/systemd/system/
ssh root@HOST 'systemctl daemon-reload && systemctl restart docear-mcp'
```

健康检查：`curl -sS http://127.0.0.1:7720/health`
