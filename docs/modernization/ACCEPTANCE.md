# 现代化验收标准

`./scripts/verify-modernization.sh` 通过，即视为当前迭代的基线验收通过。

## 阶段门槛

### 1 安全基线
- [x] `scripts/verify-modernization.sh` 存在且可在 Linux/云端运行
- [x] `.mm` 夹具 XML 结构校验
- [x] GitHub Actions `modernization-verify` workflow
- [x] 明确禁止部署脚本进入验证路径

### 2 Java 现代化
- [x] 全部 Ant 模块 `java_source_version` / `java_target_version` = **1.8**
- [ ] （后续）JDK 17 运行时兼容矩阵通过
- [ ] （后续）移除阻塞 Java 17 的内部 API

### 3 构建现代化
- [x] 根目录 Gradle 入口（校验/清单任务，不替代 Ant 完整打包）
- [x] `docs/modernization/DEPENDENCIES.md` 依赖治理清单

### 4 视觉设计系统
- [x] 默认 Look&Feel = FlatLaf Light
- [x] FlatLaf Dark 时 chrome 令牌切换（非仅换 L&F 名）
- [x] 主题令牌集中在 `DocearUiTheme`

### 5 主界面统一
- [x] UI 密度属性 `ui_density`（comfortable / compact）
- [x] Tab / 菜单 / 按钮间距随密度调整

### 6 导图体验
- [x] 默认画布/选中/连线配色对齐主题令牌
- [x] 默认正文字号提升到更易读级别

### 7 现代产品能力
- [x] 导图备份脚本（只读复制，不关进程）
- [x] 国内 AI 提供商推荐写入设置文档/默认提示
- [x] 自带 JRE / Java 17 打包路径说明

### 8 Twigmark 2.0
- [x] `twigmark2/` 并行脚手架与上游钉选说明
- [ ] （后续）可编译的 Freeplane 1.13 底座
- [ ] （后续）MCP / 工作区功能移植完成

## 非目标（本迭代不做）
- 一次性 rebase 整个仓库到 Freeplane 1.13
- 自动部署到用户 `E:\` 安装目录
- 用 Electron/Compose 重写导图引擎
