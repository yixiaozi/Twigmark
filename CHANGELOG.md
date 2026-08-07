# Changelog

本项目产品名 **Twigmark**（维护者 Mantou）。格式大致遵循 Keep a Changelog。

## [1.2.0] — 2026-08-07

### 国际化

- 主要功能界面（报表/剪切板/日历/财务/Git/番茄/快捷键/导图切换/快速命令/工作区侧栏）硬编码中文改为 EN/zh_CN 资源键
- 报表生成内容同步 i18n；About/设置品牌文案改为 Twigmark
- 收紧 Freeplane→Twigmark 字符串替换，避免破坏 URL 与路径占位符
- 补齐 `docear_plugin_core` 中文 About/许可/向导等缺失键

### 产品化

- 品牌：对外名称改为 **Twigmark**，署名为 Mantou（yixiaozi）；About / README / 欢迎页重写
- 版本：`1.2.0` 标记为 **stable**（去掉 devel）
- 许可证：根目录增加 `LICENSE`（GPL-2）与 `THIRD_PARTY_NOTICES.md`
- 离线：启动时不再请求 `docear.org/services/status.php`；帮助链接改为 GitHub；安装完成页不再打开 docear.org
- 打包：新增 `scripts/package-twigmark.ps1`；本地部署脚本默认改为仓库内 / 环境变量路径，不再写死 `E:\`
- 上手：欢迎导图改为「3 分钟上手」路径
- 图标：新 Twigmark 应用图标（启动器 / 安装包 / 闪屏）

### 近期已合入能力（基线）

- 报表：先开 Tab 再加载、多 Tab、复用聚焦、短标题、Ctrl+W、快速切换
- 快捷键编辑器：Windows 可读修饰键、默认快捷键列、Ribbon 分类
- 剪切板历史：每次出现时间记录

## 更早历史

Freeplane / Docear 上游变更见各自文档（如 `freeplane/doc/history_en.txt`）。
