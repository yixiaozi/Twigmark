# Twigmark

**Twigmark** 是一款本地优先的桌面思维导图工作台。由 **Mantou（馒头）** 个人维护，基于开源项目 [Freeplane](https://www.freeplane.org) 与历史项目 Docear（GPL）演进而来。

- 产品名：Twigmark  
- 维护者：Mantou（馒头）· [yixiaozi](https://github.com/yixiaozi)  
- 当前版本：**1.2.0**（stable）  
- 许可证：GNU GPL v2（见 [LICENSE](LICENSE)）  
- 仓库：https://github.com/yixiaozi/docear-desktop  

## 这是什么

Twigmark 面向个人知识整理与日常工作：思维导图、报表、剪切板历史、快捷键、番茄钟、MCP（供 Cursor 等 AI 读写导图）等。数据默认保存在本机，**不依赖 docear.org 云服务**。

## 3 分钟上手

1. 解压 Windows 便携包（或安装包），运行 `docear.exe`（兼容旧启动器文件名）。  
2. 首次启动选择主目录（导图与数据放这里）。  
3. 打开欢迎图：`Insert` 新建节点，`Enter` 编辑，`Ctrl+S` 保存。  
4. 菜单 **帮助 → 教程** 可再次打开上手图。  

更完整的说明见欢迎导图与 [CHANGELOG.md](CHANGELOG.md) / [RELEASE_NOTES.md](RELEASE_NOTES.md)。

## 运行环境

- **JDK / JRE 8**（推荐）  
- Draw.io 相关功能需要带 JavaFX 的 JRE（打包脚本可注入）  
- Windows 为当前主要发行目标  

## 从源码打包（可复现）

不要使用本机硬编码路径。推荐：

```powershell
# 仅编译检查（不部署、不关进程）
powershell -ExecutionPolicy Bypass -File .\scripts\compile-check.ps1

# 产出便携 zip 到仓库内 dist/（不写 E:\，不启动程序）
powershell -ExecutionPolicy Bypass -File .\scripts\package-twigmark.ps1
```

产物：`docear_framework/dist/twigmark_windows.zip`（或同目录下 ant 生成的 windows zip）。

可选本地部署（仅当你明确需要）：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-docear-to-dist.ps1 -NoLaunch
```

可通过环境变量覆盖路径：`DOCEAR_DIST_DIR`、`DOCEAR_DAILY_INSTALL`、`DOCEAR_WORK_DIR`。

## 上游致谢

- Freeplane — 思维导图核心  
- Docear — 文献/工作区等历史扩展  
- JabRef — 参考文献管理集成  

详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 反馈

请到 GitHub Issues：https://github.com/yixiaozi/docear-desktop/issues  
