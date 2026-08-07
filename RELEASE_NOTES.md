# Twigmark 1.2.0 发布说明

**Twigmark** 是 Mantou（馒头）维护的本地优先桌面思维导图工作台，基于 Freeplane / Docear（GPL）。

## 这一版是什么

把「别人的 Docear 壳」换成可对外展示的产品表皮与发布工程：

1. 清晰的品牌与归属（Twigmark · Mantou）  
2. 正式版号 `1.2.0` + 本页发布说明  
3. 根级 LICENSE 与第三方声明  
4. 切断 docear.org 联网与死链  
5. 可复现的 Windows 便携打包脚本  
6. 3 分钟上手欢迎图  

## 如何获取

- 源码：https://github.com/yixiaozi/docear-desktop  
- 自行打包：`powershell -ExecutionPolicy Bypass -File .\scripts\package-twigmark.ps1`  

## 运行要求

- Windows + Java 8（JRE/JDK）  
- Draw.io 插件需要 JavaFX（打包流程可注入）  

## 致谢

感谢 Freeplane、Docear、JabRef 及所有上游贡献者。
