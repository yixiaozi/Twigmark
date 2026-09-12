# 本地密钥（不入库）

此目录整目录被 `.gitignore` 忽略。

从 VPS 同步示例：

```bash
scp root@HOST:/opt/docear/mcp-api-key.txt deploy/vps/secrets.local/
scp root@HOST:/opt/docear-dify-kb/env deploy/vps/secrets.local/docear-dify-kb.env
```
