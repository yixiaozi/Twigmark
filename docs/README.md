# Twigmark 站点（GitHub Pages）

本目录是项目主页源码，发布地址：

**https://yixiaozi.github.io/Twigmark/**

## 发版时如何自动更新

`.github/workflows/pages.yml` 会在以下情况重新构建并部署：

1. **GitHub Release 发布**（`release: published`）— 写入最新 tag、日期与下载链接  
2. **推送到 `master`** 且改动了 `docs/`、`version.properties` 或本工作流  
3. **手动** `workflow_dispatch`

构建时运行 `scripts/update-pages-release.py`，生成 `_site/data/release.json`（部署产物内）。页面也会尝试读取 GitHub Releases API，作为二次刷新。

## 本地预览

用任意静态服务器打开 `docs/`，例如：

```bash
python3 -m http.server 8080 --directory docs
```

然后访问 http://127.0.0.1:8080/

可选刷新 seed 数据：

```bash
python3 scripts/update-pages-release.py --out docs/data/release.json
```

## 仓库设置（首次）

在 GitHub → **Settings → Pages**：

- Source：**GitHub Actions**

首次 Actions 成功后即可访问上述 URL。
