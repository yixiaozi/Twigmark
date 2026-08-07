# Twigmark

**Twigmark** — 本地优先的桌面思维导图与个人知识工作台。

由 **Mantou（馒头）** 个人维护。基于开源项目 [Freeplane](https://www.freeplane.org) 与历史上游 Docear（GNU GPL）演进而来，面向日常整理、专注与 AI 协作，而不是云端学术套件。

| | |
|---|---|
| 产品名 | **Twigmark** |
| 维护者 | Mantou（馒头）· [yixiaozi](https://github.com/yixiaozi) |
| 版本 | **1.2.0**（stable） |
| 许可证 | [GNU GPL v2](LICENSE) |
| 仓库 | https://github.com/yixiaozi/Twigmark |
| 主页 | https://yixiaozi.github.io/Twigmark/ |
| 平台 | Windows（当前主要发行目标） |

---

## 它解决什么问题

把想法、任务、文献线索、专注记录和 AI 读写能力收进**同一套本地导图**里：

- 导图仍是主角：节点、链接、图标、折叠、样式  
- 工作流补齐：提醒、待办、番茄钟、报表、剪切板历史  
- AI 可接入：本机 MCP，让 Cursor 等助手读写你的导图（数据仍在本机）  
- **不依赖 docear.org**；没有强制账号与云同步  

适合个人知识库、项目规划、学习笔记、日常待办与「第二大脑」式整理。

---

## 3 分钟上手

1. 解压便携包（或安装包），运行 **`docear.exe`**（启动器文件名保留，兼容旧脚本与快捷方式）。  
2. 首次启动选择**主目录**（导图与数据放这里）。  
3. 在欢迎图里：`Insert` 新建子节点，`Enter` 编辑，`Ctrl+S` 保存。  
4. 之后可用 **帮助 → 教程** 再次打开上手图。  

更细的说明见应用内欢迎导图，以及 [RELEASE_NOTES.md](RELEASE_NOTES.md)、[CHANGELOG.md](CHANGELOG.md)。

---

## 主要能力

### 思维导图与工作区

- 完整的桌面导图编辑（节点、备注、属性、样式、图标、链接、筛选、导入导出）  
- 左侧工作区 / 项目管理本地项目与 `.mm` 文件  
- Ribbon 功能带：文件、节点、格式、链接、视图等  
- 导图快速切换；多文档 / 报表 Tab（含分组、Ctrl+W 关闭）  

### 效率与时间

- **提醒与待办**：一次性 / 周期提醒、紧急度、侧栏时间线  
- **安排中心**：日历 / 日程视口，与提醒、计划工时联动  
- **番茄钟**：节点级计时、暂停与补记、历史与皮肤  
- **报表中心**：活动、专注、MCP 审计等；先开 Tab 再加载，可复用聚焦  
- **剪切板历史**：相同内容合并，并记录每次出现时间  
- **快捷键编辑器**：查看默认键、按 Ribbon 分类改绑（Windows 可读修饰键文案）  

### 知识组织

- 标签分组（可嵌套）、收藏与钉选  
- 关系图谱：导图 / 节点 / 标签关联浏览  
- 节点隐私级别、加密导图（按需）  

### AI 与自动化

- **MCP（本机 / 可公网）**：默认 `127.0.0.1:7720`；可生成 API Key、绑定 `0.0.0.0`；可选网页版 `/web/`（服务端配置大模型 Key，浏览器对话操作导图）；带访问审计  
- **AI 助手侧栏**：围绕选中节点生成内容（可选，依赖你配置的 CLI / 服务）  
- **Git 侧栏**：对工作区导图仓库查看状态、提交与同步（可选）  
- **Todoist 同步**（可选）  

### 文献、图示与扩展

- PDF 批注导入与监控文件夹  
- 参考文献（BibTeX / JabRef 集成）  
- **Draw.io** 内嵌编辑（需带 JavaFX 的 JRE）  
- LaTeX / 公式、脚本、SVG、地图等上游插件能力  

### 个人财务（可选）

- 账本导图 + 侧栏：收支、转账、预算、订阅等；也可经 MCP 记账  

---

## 运行环境

- **Java 8**（JRE / JDK；推荐完整 JDK 以便从源码编译）  
- Draw.io：需要 **JavaFX**（`scripts/package-twigmark.ps1` / 部署脚本可注入缓存 JRE）  
- 可选：Cursor（MCP）、Git、Todoist、Copilot CLI 等，按你启用的功能准备  

---

## 获取与打包

### 使用已有安装

在方便时自行运行本机部署流程（**会关闭正在运行的实例并覆盖安装目录**，请避开正在使用的时段）：

```powershell
# 可选：完整构建并部署到本地目录
powershell -ExecutionPolicy Bypass -File .\scripts\build-docear-to-dist.ps1
```

环境变量（均可选）：

| 变量 | 作用 |
|------|------|
| `DOCEAR_DIST_DIR` | 解压 / 部署目标（默认：仓库内 `dist\TwigmarkDist`） |
| `DOCEAR_DAILY_INSTALL` | 额外同步到的日常安装目录 |
| `DOCEAR_WORK_DIR` | 写入 `working-directory.txt` 的主目录 |

### 只打便携包（推荐发给别人 / 归档）

不关进程、不写本机 `E:\` 硬编码路径：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-twigmark.ps1
```

产物位于 `docear_framework/dist/`（如 `docear_windows.zip`，脚本会同步别名 `twigmark_windows.zip`）。

### 仅验证编译

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\compile-check.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\compile-check.ps1 -Modules freeplane,docear_plugin_core
```

---

## 架构简述

Twigmark 是挂在 Freeplane 上的 OSGi 插件套件：

- `freeplane` / `freeplane_plugin_*` — 导图核心与通用插件  
- `docear_plugin_core` — 产品壳、工作区、About、欢迎图、设置等  
- `docear_plugin_mcp`、`docear_plugin_ai`、`docear_plugin_drawio` 等 — MCP、AI、Draw.io 等扩展  

模块目录名仍保留历史上游前缀，**不影响对外产品名 Twigmark**。

---

## 隐私与联网

- 默认**本地优先**：导图、设置、剪切板库、番茄与 MCP 审计等保存在你选择的目录 / 本机配置区  
- **1.2.0 起**：启动不再请求 docear.org 状态接口；帮助入口指向本仓库  
- 你主动点击的链接、或自行启用的第三方（AI CLI、Todoist、远程 Git）才会产生额外网络访问  

条款摘要见应用内许可说明，以及仓库中的本地优先文案。

---

## 文档与版本

- 项目主页：[yixiaozi.github.io/Twigmark](https://yixiaozi.github.io/Twigmark/)（源码在 `docs/`；首次需在 Settings → Pages 选择 `master` / `/docs`；发版时 Actions 自动更新版本信息）  
- [RELEASE_NOTES.md](RELEASE_NOTES.md) — 1.2.0 发布说明  
- [CHANGELOG.md](CHANGELOG.md) — 变更记录  
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) — 第三方与上游声明  
- [LICENSE](LICENSE) — GPL v2  

---

## 上游致谢

感谢并保留对下列项目的 GPL 义务与致谢：

- **Freeplane** — 思维导图核心  
- **Docear** — 工作区与文献相关历史扩展  
- **JabRef** — 参考文献管理集成  

Twigmark 是个人维护的衍生发行版，不是 docear.org 官方产品。

---

## 反馈与贡献

- Issues：https://github.com/yixiaozi/Twigmark/issues  
- 讨论：https://github.com/yixiaozi/Twigmark/discussions  

欢迎报告缺陷与改进建议。提交代码前请先开 Issue 对齐范围，并遵守 GPL。

---

<p align="center">
  <b>Twigmark</b> · by Mantou · local-first mind maps for real work
</p>
