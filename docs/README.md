# Twigmark 站点（GitHub Pages）

本目录是项目主页源码，目标地址：

**https://yixiaozi.github.io/Twigmark/**

## 首次启用（需仓库管理员点一次）

云端 CI token **无法**代你打开 Pages，请在 GitHub 网页操作一次：

1. 打开仓库 **Settings → Pages**
2. **Build and deployment → Source** 选 **Deploy from a branch**
3. Branch：`master`，Folder：`/docs` → Save

数分钟后即可访问上述 URL。

（可选）若改用 **GitHub Actions** 作为 Source，可手动跑 workflow `Deploy GitHub Pages (Actions)`。

## 发版时如何自动更新

1. **发布 GitHub Release** → workflow `Update Pages release data` 重写 `docs/data/release.json` 并提交（带 `[skip ci]`）
2. 页面脚本会再请求 **Releases API**，即使 seed 稍旧也能显示最新 tag / 资源下载链接
3. 也可手动：Actions → `Update Pages release data` → Run workflow

本地刷新 seed：

```bash
python3 scripts/update-pages-release.py --out docs/data/release.json
```

## 本地预览

```bash
python3 -m http.server 8080 --directory docs
```

访问 http://127.0.0.1:8080/
