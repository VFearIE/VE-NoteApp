# 发行物说明

本目录收录 **VE笔记** 的发行相关内容。

> 正式二进制（APK / 模块 / 校验和）发布在
> [GitHub Releases](https://github.com/VFearIE/VE-NoteApp/releases)，
> 不放入 Git 历史。本目录只保留**可复现的打包源码**。

---

## 发行物一览

| 发行物 | 面向 | 位置 |
|---|---|---|
| **Root 版 APK** | 已 Root 的个人用户 | Release |
| **LSPosed / Vector 保活模块** | 配合 Root 版 | Release + [`lsposed/`](lsposed/) 源码 |
| **Platform 版 APK**（未签名） | ROM 开发者 | Release |
| **Magisk 模块** | 希望装为系统应用的用户 | Release + [`magisk/`](magisk/) 源码 |
| **权限白名单** | Root 版 / Platform 版共用 | 仓库根目录 `com.noteVE.xml` |

---

## Root 版

**适用**：已 Root 的设备。

需要三个文件：

```text
NoteVE-root-<版本>.apk          应用本体（已签名）
XposedKeepAlive-<版本>.apk      Xposed / Vector 保活模块
com.noteVE.xml                  权限白名单（配合 Magisk 模块使用，可选）
```

安装步骤见 [../docs/INSTALL.md](../docs/INSTALL.md)。

**保活模块的作用**：在部分 ROM 上，「从最近任务划掉应用」会执行 `force-stop`，
连带**清空该应用的 `AlarmManager` 闹钟**（提醒永久失效）。
模块在系统框架层拦截该调用，从而保住提醒。

> 模块是**通用**的：**作用域即保护对象** —— 勾选哪些应用就保护哪些，
> 无需改代码或维护名单文件。源码见 [`lsposed/`](lsposed/)。

---

## Platform 版

**适用**：ROM 厂商 / 系统集成。

需要两个文件：

```text
NoteVE-platform-<版本>-unsigned.apk    未签名，需用平台密钥签名
com.noteVE.xml                         权限白名单
```

```text
1. 用 ROM 平台密钥签名 APK
2. 部署到 /system/priv-app/NoteVE/NoteVE.apk          （权限 0644）
3. 部署白名单到 /system/etc/permissions/com.noteVE.xml （权限 0644）
4. 重启生效
```

> **★ 白名单文件名必须是 `com.noteVE.xml`**（即「包名.xml」）。
> 若写成 `privapp-permissions-com.noteVE.xml`，系统**不会识别**，特权权限无法授予。

该版本声明 `android.uid.system`，**必须用平台密钥签名**才能安装。

---

## Magisk 模块

**适用**：已 Root，希望应用获得 **priv-app（特权应用）** 身份。

模块做的事非常简单：把 APK 与权限白名单以**静态 overlay** 方式挂载到系统分区。

```
system/priv-app/NoteVE/NoteVE.apk
system/etc/permissions/com.noteVE.xml
```

**不含任何保活脚本或常驻服务** —— 历史上尝试过的保活手段
（常驻服务、看门狗、定时拉起等）已被验证会引入系统级问题，故正式模块不再包含。

刷入后**重启**生效。源码见 [`magisk/`](magisk/)。

### 自行打包

```sh
cd magisk
sh pack.sh /path/to/NoteVE-root-<版本>.apk
```

`pack.sh` 会校验 APK 版本与 `module.prop` 中声明的版本是否一致，
不一致会拒绝打包 —— 避免产出「文件名写着 A 版本、内容却是 B 版本」的包。

---

## 模块源码

| 目录 | 内容 |
|---|---|
| [`lsposed/`](lsposed/) | Xposed / Vector 保活模块（通用，可保护任意应用） |
| [`magisk/`](magisk/) | 系统应用安装模块（纯静态 overlay） |

两者均可独立构建，构建脚本见各自目录下的 `README.md` / `build.sh`。

---

## 版本约定

- 应用版本号在 `app/build.gradle.kts` 的 `versionName` / `versionCode`
- Magisk 模块版本在 `distribution/magisk/module.prop`
- 二者的发布版本号应保持一致（`pack.sh` 会校验）

*本文件最后更新：2026-10-04*
