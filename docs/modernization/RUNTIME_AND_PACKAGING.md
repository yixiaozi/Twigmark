# 运行时与打包（阶段 7）

## 现状（1.x）

- 构建/运行脚本仍以 **JDK 8** 为主（Draw.io 需要带 JavaFX 的 JRE）。  
- 便携包可通过 `scripts/package-twigmark.ps1` / `setup-drawio-javafx.ps1` 注入 Liberica Full JRE。  
- **禁止**在验证流程中调用会停止进程并覆盖安装目录的部署脚本。

## 目标

1. 用户安装包 **自带 JRE**（最终 Java 17/21 + 如需则独立 JavaFX）。  
2. 开发机可用 JDK 8 或更新的 JDK（编译 `--release 8` / source&target 1.8）。  
3. Twigmark 2.0 直接跟上游 jpackage / 嵌入式 Java 21。

## 云端验证

```bash
./scripts/verify-modernization.sh
```

该脚本只写仓库内 `build/`/`dist/`，不部署到用户目录。

## Windows 日常

- 验证编译：`compile-check.bat`  
- 真正安装到日常目录：用户自行在方便时运行 `build-docear.bat`（会关进程）。
