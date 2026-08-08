# Twigmark 2.0（并行底座）

本目录是 **阶段 8** 脚手架：以 **Freeplane 1.13+** + **Java 17** + **Gradle** 为新内核，与当前 1.x（Freeplane 1.3 系 fork）并行演进。

## 原则

1. **不覆盖** 现有 `freeplane/`、`docear_plugin_*` 日常产品路径。  
2. 先做到：打开/保存 `.mm`、FlatLaf、基础编辑；再移植 MCP → 工作区 → 番茄/提醒 → 报表/财务。  
3. 1.x 继续发版，直到 2.0 通过 [ACCEPTANCE](../docs/modernization/ACCEPTANCE.md) 第 8 阶段门槛。

## 目录约定

```
twigmark2/
  README.md          ← 本文件
  UPSTREAM.md        ← 上游版本钉选与同步策略
  migration/         ← 功能移植清单与状态
  scripts/           ← 仅脚手架验证（不部署）
```

## 当前状态

- 脚手架与上游钉选文档已就绪  
- 完整 Freeplane 1.13 源码 **不** vendoring 进主仓库（体积过大）；按 `UPSTREAM.md` 在独立 worktree/子模块拉取  

## 本地启动上游对照（可选）

详见 `UPSTREAM.md`。切勿用对照构建覆盖用户正在使用的 Twigmark 1.x 安装目录。
