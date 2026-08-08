# 国内可用 AI 提供商（阶段 7）

Twigmark 桌面 AI / MCP Web 聊天支持 OpenAI 兼容接口。国内用户推荐默认顺序：

| 优先级 | 提供商 | Base URL（示例） | 说明 |
|--------|--------|------------------|------|
| 1 | **DeepSeek** | `https://api.deepseek.com/v1` | 国内直连稳、编程强、成本低 |
| 2 | 通义千问 Qwen | 阿里云百炼 OpenAI 兼容端点 | 企业/备案友好 |
| 3 | Kimi（Moonshot） | `https://api.moonshot.cn/v1` | 长上下文 |
| 4 | 智谱 GLM | 智谱 OpenAI 兼容端点 | 备选 |
| — | OpenRouter | `https://openrouter.ai/api/v1` | 高级选项，非国内默认 |

## Cursor 改代码时

- 能稳定用 Cursor 官方最强 Agent（Opus 级）时优先。  
- 国内网络不稳：自定义模型接 **DeepSeek V4 Pro**。  

## 产品内设置

在 MCP / AI 设置页配置 API Key 与 Base URL；密钥写入本机（可参考 Freeplane 1.13 的 `secrets.properties` 思路，后续 1.x 也可隔离密钥文件）。
