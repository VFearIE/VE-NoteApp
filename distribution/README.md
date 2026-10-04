# 发行版 —— 可直接分发的成品

按项目分组，每组内含 README（安装步骤 + 说明）。

## ToastStyle（全局 Toast 美化）

| 文件 | 说明 |
|---|---|
| `ToastStyle-Magisk-v1.0.0.zip` | RRO 覆盖层 Magisk 模块（必装，含看门狗） |
| `ToastHook-LSPosed-v2.0.0.apk` | LSPosed 模块（选装，补漏网应用） |
| `toast小白条.zip` | 另一分发件（同模块） |

## NoteApp（VE笔记）—— 双版本

**功能一致，按部署环境二选一（互斥）。**

### A 版：root 环境（Xposed 保活）
`NoteApp-A-root/`
- `NoteApp-root-2.0.0.apk` 应用本体
- `XposedKeepAlive-*.apk` 通用保活模块（**作用域即保护对象**，可保护任意应用）
- `com.noteVE.xml` priv-app 白名单
- `README.md` 安装步骤

### B 版：平台签名（无需 root）
`NoteApp-B-platform/`
- `NoteApp-platform-2.0.0-unsigned.apk` **需用平台密钥签名**
- `com.noteVE.xml` priv-app 白名单
- `README.md` 安装步骤

> 两版差异仅在保活方式：A 靠 Xposed 模块 Hook force-stop；
> B 靠 `sharedUserId=android.uid.system` + 平台签名（与系统时钟同级）。

*更新：2026-10-03（v2.0.0 分发版）*
