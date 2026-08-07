# Twigmark Web：单独域名怎么配

Twigmark Web 本身只监听本机 HTTP（默认 `0.0.0.0:7720` 或 `127.0.0.1:7720`）。  
**单独域名 = DNS 指向你的机器 + 反向代理做 HTTPS**，不要把 7720 裸奔到公网。

## 推荐架构

```text
浏览器  https://twig.example.com
   │
   ▼
Caddy / Nginx（443，TLS）
   │ 反代
   ▼
Twigmark MCP  http://127.0.0.1:7720
              /web/  /api/  /mcp  /health
```

## 1. Twigmark 侧

产品设置 → MCP：

1. **公网绑定**或主机填 `127.0.0.1`（推荐只给本机反代用）  
2. 若要从外网直连 MCP：主机 `0.0.0.0`，并生成 **MCP API Key**  
3. 网页用**账号密码**（仅允许注册一次）  
4. 重启 MCP 服务  

网页大模型建议 OpenRouter：`https://openrouter.ai/api/v1` + `sk-or-…` + 模型如 `openai/gpt-4o-mini`。

## 2. DNS

在域名服务商添加：

| 类型 | 主机记录 | 值 |
|------|----------|-----|
| A | `twig`（或 `@`） | 你的公网 IP |
| 或 CNAME | `twig` | 你的动态域名主机 |

## 3. Caddy 示例（自动 HTTPS）

```caddy
twig.example.com {
	encode gzip
	reverse_proxy 127.0.0.1:7720
}
```

## 4. Nginx 示例

```nginx
server {
	listen 443 ssl http2;
	server_name twig.example.com;

	# ssl_certificate     /path/fullchain.pem;
	# ssl_certificate_key /path/privkey.pem;

	location / {
		proxy_pass http://127.0.0.1:7720;
		proxy_http_version 1.1;
		proxy_set_header Host $host;
		proxy_set_header X-Real-IP $remote_addr;
		proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
		proxy_set_header X-Forwarded-Proto $scheme;
		proxy_read_timeout 300s;
	}
}
```

用 [Certbot](https://certbot.eff.org/) 或 Cloudflare Origin 证书配置 TLS。

## 5. 防火墙

- 只开放 **443**（及可选 80 做证书验证）  
- **不要**把 7720 对公网开放（反代走本机 loopback 即可）  

## 6. 访问

- 网页：`https://twig.example.com/web/`  
- 健康检查：`https://twig.example.com/health`  
- Cursor MCP（若暴露）：`https://twig.example.com/mcp` + `Authorization: Bearer <MCP API Key>`  

## 7. Cloudflare 等 CDN

可以：橙色云代理 → 源站只收 Cloudflare IP，源站仍反代到 `127.0.0.1:7720`。  
WebSocket 非必须；普通 HTTPS 反代即可。

## 安全清单

- [ ] 网页已注册唯一账号，注册入口已关闭  
- [ ] MCP API Key 已设置（若暴露 `/mcp`）  
- [ ] 仅 443 对外；7720 不对公网  
- [ ] OpenRouter / LLM Key 只存在 webchat 数据库，不进仓库  
