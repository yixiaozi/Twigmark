# 依赖治理清单（阶段 3）

Ant 仍是主构建；本清单用于分批升级，避免「一次性换库」炸掉发行版。

## 运行时（产品）

| 组件 | 当前 | 目标 | 风险 |
|------|------|------|------|
| Java | 8（脚本硬性） | 17 LTS（自带 JRE） | Draw.io / JavaFX、内部 API |
| FlatLaf | 3.5.4 | 跟随安全补丁 | 低 |
| Substance / Flamingo | 6.3 | 保留至 Twigmark 2.0 | Ribbon 深度耦合 |
| sqlite-jdbc | 3.21.0 | ≥ 3.45 | 中（加密/驱动行为） |
| commons-io | 2.4 | ≥ 2.15 | 低 |
| commons-lang | 2.0/2.1 | commons-lang3 | 中（API 迁移） |

## 网络 / 服务（插件）

| 组件 | 当前 | 目标 | 备注 |
|------|------|------|------|
| Jersey | 1.1x / 1.12 | 评估移除或 JAX-RS 2+ | `docear_plugin_services` / search |
| commons-httpclient | 3.1 | HttpClient 5 或 JDK HttpClient | 高优先级替换候选 |
| JabRef | Beta 2.7 fork | 保持隔离；2.0 再评估 | 勿与主 UI 升级绑死 |

## 构建工具

| 工具 | 当前 | 策略 |
|------|------|------|
| Apache Ant | 1.10.14（`tools/`） | 继续作为完整 dist 主路径 |
| Gradle | 根入口（校验） | 逐步承担模块编译/测试；不抢 Ant 打包 |
| GitHub Actions | Pages + modernization-verify | 云端结构/夹具/编译探测 |

## 升级顺序（勿打乱）

1. 编译目标 1.8（已完成）
2. 替换已废弃 HTTP 客户端（不改 UI）
3. sqlite-jdbc 小版本升级 + 审计库回归
4. FlatLaf 小版本
5. Java 17 运行探测 → 自带 JRE
6. Twigmark 2.0 换核时再处理 Substance/Flamingo
