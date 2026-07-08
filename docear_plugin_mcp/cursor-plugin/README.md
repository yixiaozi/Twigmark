# Docear Cursor 插件（随 Docear 分发）

本目录是 **Cursor / AI 客户端安装文件的权威来源**，随 `org.docear.plugin.mcp` 一起打包进 Docear。

## 自动同步

Docear 启动后（默认延迟 12 秒）会自动将本目录复制到：

`%USERPROFILE%\.cursor\plugins\local\docear\`

当 bundled `VERSION` 与上次同步版本不一致时才会重新复制。同步完成后请在 Cursor 中 **Reload Window**。

可在 Docear 偏好中关闭：`mcp.cursorPlugin.sync.enabled=false`

## 目录结构

```
cursor-plugin/
├── .cursor-plugin/plugin.json
├── mcp.json
├── rules/docear-mcp-context.mdc
├── skills/docear-mcp-context/SKILL.md
└── VERSION                      # 构建时生成，勿手改
```

## 开发说明

- 修改 skill / rule / mcp.json 请改 **本目录**，不要只改 `%USERPROFILE%\.cursor\plugins\local\docear`
- 仓库根目录 `.cursor/` 为 Docear-Desktop **开发工作区**配置；发布内容以本目录为准
- 重新编译 MCP 插件并重启 Docear 后，自动同步会推送到 Cursor

## 手动同步（备用）

若自动同步失败，可在 PowerShell 中：

```powershell
$src = "<Docear安装目录>\plugins\org.docear.plugin.mcp\cursor-plugin"
$dst = "$env:USERPROFILE\.cursor\plugins\local\docear"
New-Item -ItemType Directory -Path $dst -Force | Out-Null
Copy-Item "$src\*" $dst -Recurse -Force
```

然后在 Cursor 中 Reload Window。
