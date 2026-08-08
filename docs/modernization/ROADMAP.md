# Twigmark 现代化路线图（8 阶段）

本文件是有序升级的单一事实来源。每一阶段必须：**可编译、可回退、有云端验证、不部署覆盖用户正在运行的实例**。

| 阶段 | 目标 | 状态 |
|------|------|------|
| 1 | 安全基线：验证脚本、夹具、CI、验收标准 | ✅ 本 PR 落地 |
| 2 | Java 现代化：编译目标统一 **1.8**，JDK 21 可编核心链 | ✅ 本 PR 落地（完整 Java 17 运行时后续） |
| 3 | 构建现代化：Gradle 入口 + 依赖清单（Ant 仍为主构建） | ✅ 本 PR 落地 |
| 4 | 视觉设计系统：FlatLaf 默认、深浅色令牌 | ✅ 本 PR 落地 |
| 5 | 主界面统一：`ui_density` + Tab/菜单/对话框间距 | ✅ 本 PR 首轮 |
| 6 | 导图体验：画布/选中/连线默认配色现代化 | ✅ 本 PR 首轮 |
| 7 | 现代产品能力：备份脚本、运行时/国内 AI 文档 | ✅ 本 PR 首轮 |
| 8 | Twigmark 2.0：Freeplane 1.13+ 并行底座脚手架 | ✅ 脚手架；内核移植后续 PR |

## 硬性约束

- **禁止**在 Agent 结束钩子或日常验证中调用 `build-docear.bat` / 部署到用户安装目录。
- 验证只用 `scripts/verify-modernization.sh`、`scripts/compile-check.ps1`（Windows）或模块内 `ant dist/build`（写入仓库 `build/`/`dist/`）。
- 第 8 阶段**不**覆盖 1.x 内核；并行目录 `twigmark2/`，成熟后再切换。

## 验收入口

```bash
./scripts/verify-modernization.sh
```

详见 [ACCEPTANCE.md](./ACCEPTANCE.md)。
