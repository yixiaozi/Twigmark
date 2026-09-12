# Twigmark 官网（twigmark.com）

静态多页落地站，部署到 VPS `/var/www/twigmark`。

| 路径 | 说明 |
|------|------|
| `/` | 首页 |
| `/features.html` | 功能一览 |
| `/about.html` | 介绍与上手 |
| `/changelog.html` | 更新历史 |

## 本地预览

```bash
cd website && python3 -m http.server 8765
# 打开 http://127.0.0.1:8765
```

## 部署

```bash
./website/deploy-to-vps.sh root@149.104.79.102
```

需本机可 SSH 到目标机；首次会配置 Nginx 并用 Certbot 申请证书（域名需已解析到该机）。
