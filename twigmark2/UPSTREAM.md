# 上游钉选（Freeplane）

| 项 | 值 |
|----|-----|
| 上游仓库 | https://github.com/freeplane/freeplane |
| 目标稳定线 | **1.13.3**（或其后的 `release-1.13.x` 最新稳定标签） |
| 预览线 | `1.13.4-pre*`（仅评估，不作为 2.0 底座） |
| 运行时 | Java **17+**（AI 功能要求 ≥17；发行可用 21） |
| 构建 | Gradle（上游） |

## 同步策略

1. 固定标签（例：`release-1.13.3`），写入 `migration/PINNED_VERSION`。  
2. 评估功能差异时阅读上游 `freeplane/doc/history_en.txt`。  
3. Twigmark 独有功能以「移植清单」跟踪，禁止无文档的静默 cherry-pick。  
4. `.mm` 兼容：用 `testdata/mm-fixtures/` + 1.x 导出样例做双向打开/保存测试。

## 与 1.x 的分工

| 能力 | 1.x（现行） | 2.0（本目录） |
|------|-------------|---------------|
| 导图引擎 | Freeplane 1.3 fork | Freeplane 1.13+ |
| Ribbon / Substance | 保留 | 重新评估（可能菜单+FlatLaf） |
| MCP / 番茄 / 财务 | 主力 | 按清单移植 |
| FlatLaf 主题 | 已默认 | 直接继承并加强 |
