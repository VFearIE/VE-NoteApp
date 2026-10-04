# VE笔记 (VE NoteApp)

> 轻量、无广告、可离线使用的 Android 原生笔记应用。
> 面向小尺寸屏幕（手表 / 小屏设备）优化，同时兼容常规手机。

[![Platform](https://img.shields.io/badge/Android-7.0%20~%2014-3F51B5)](https://developer.android.com)
[![minSdk](https://img.shields.io/badge/minSdk-24-blue)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-green)](LICENSE)

---

## ⚠️ 关于本项目的开发方式（请先阅读）

**本项目的全部代码与工程实现，均由 AI 编码代理完成。**

开发者 **VFearIE** 不直接编写项目代码。开发者承担的是：

| 角色 | 具体工作 |
|---|---|
| 产品定义 | 确定要做什么、不做什么，界定功能边界 |
| 交互要求 | 描述期望的操作方式与视觉/动效体验 |
| 技术取舍 | 在 AI 给出的方案之间做决策 |
| 真机验证 | 在真实设备上安装、使用、发现问题 |
| 问题反馈 | 定位现象并反馈给 AI（含日志、截图、复现步骤） |
| 持续迭代 | 驱动一轮又一轮的改进与修正 |

**因此：**

- 本项目**不是**传统意义上"人工逐行编写"的作品；
- 项目的架构、代码、注释、文档、构建脚本均由 AI 生成；
- 代码质量取决于开发者的需求描述精度与验收严格程度，而非传统编程经验；
- 本仓库**如实披露**这一点，遵循开源社区的诚实原则。

如果你在寻找"由人类工程师手写的高质量参考实现"，本项目可能不符合预期；
如果你对"AI 主导开发能达到什么程度"感兴趣，欢迎阅读源码、提交 Issue 或参与讨论。

---

## 功能

### 编辑

- **块模型编辑器**：正文由 `文字 / 图片 / 录音 / 任意文件` 四种块组成，而非单一富文本字符串
- **光标位置插入**：图片 / 录音 / 文件插入到**当前光标处**，不是固定置顶或置底
- **矢量排布**：附件是文档流中的行内节点，可随回车、删除自然上下移动
- **富文本**：下划线 / 删除线 / 无序列表 / 超链接 / 文字颜色
- **超链接**：自动识别 URL / 邮箱 / 电话；点击链接文字即可打开（命中范围严格限制在链接文字本身）
- **任意文件**：按扩展名识别视频 / 音频 / 文档；视频生成缩略图（缓存可清理）
- **只读模式**：一键锁定编辑，避免误触
- **快速定位条**：长笔记右侧拖动条快速跳转

### 数据

- **离线优先**：无账号、无网络权限、数据完全存于本机
- **附件独立文件**：图片 / 录音 / 文件存于私有目录，数据库只存相对路径（不使用 BLOB）
- **导入导出**：自定义 `.tar.gz` 包格式，含完整块位置与附件；支持单条导出与全量备份
- **文件名保留**：导入时保留原始文件名（自动去重、保留扩展名）

### 提醒

- 精确闹钟（`AlarmManager`），支持一次性 / 每日 / 每周 / 每月 / 每年重复
- 停驻通知 + 持续震动，息屏宽限、解锁或点击通知即完成
- **多提醒隔离**：多条提醒接近同时触发时互不干扰
- 开机 / 应用更新后自动重建提醒

### 界面

- 深色模式（支持 Android 12+ 动态取色）
- 中英双语
- 针对 480×480 @214dpi 手表屏优化，触控目标 ≥ 48dp

---

## 系统要求与兼容性

| 项 | 值 |
|---|---|
| 最低版本 | Android 7.0（API 24） |
| 目标版本 | Android 14（API 34） |
| 编译版本 | API 35 |

> **兼容性说明（如实声明）**
>
> 项目的目标范围是 **Android 7 ~ 14**。
> **提醒功能的真实设备验证仅覆盖 Android 9（API 28）手表**。
> 其余版本为代码层面的兼容性适配，**未在真机上逐一验证**。
> Android 15 / API 35 仅做过基础兼容性检查。

---

## 两个发行版本

本项目提供两个版本，**按需选择其一**（互斥）：

### A. Root 版（个人用户）

适用：已 Root 的设备。

| 组成 | 说明 |
|---|---|
| `NoteVE-root-*.apk` | 应用本体，已用项目密钥签名 |
| `XposedKeepAlive-*.apk` | Xposed / Vector 保活模块（**通用**，可保护任意应用） |
| `com.noteVE.xml` | priv-app 权限白名单 |

**为什么需要保活模块**：在部分设备上，「从最近任务划掉应用」实际触发的是
`force-stop`，会清空该应用的 `AlarmManager` 闹钟并置包状态为 `stopped`，
导致提醒永久失效。该模块在系统框架层拦截针对该应用的 `force-stop`，从而保住提醒。

模块为**通用设计**（不绑定任何具体应用）：

- **作用域只需勾选「系统框架」**一项 —— Hook 点在 `system_server` 内
- **保护对象**由模块内的保护名单决定（默认保护本应用，可自行增删，详见模块说明）

### B. Platform 版（ROM 集成）

适用：ROM 厂商 / 系统集成。

| 组成 | 说明 |
|---|---|
| `NoteVE-platform-*-unsigned.apk` | **未签名**，需用你的平台密钥签名 |
| `com.noteVE.xml` | 权限白名单 |

该版本声明 `android.uid.system`，**必须使用 ROM 的平台密钥签名**才能安装。

---

## 安装

详见 [docs/INSTALL.md](docs/INSTALL.md)。要点：

### Root 版

```text
1. 安装 NoteVE-root-*.apk（普通安装即可）
2. 安装 Xposed / Vector 模块 NoteVE-LSPosed-*.apk
3. 在框架管理器（LSPosed / Vector）中启用该模块
4. ★ 作用域只需勾选一项：「系统框架」（显示为 system）
5. 重启设备（Hook 点在 system_server，作用域变更只对新进程生效）
```

### Platform 版

```text
1. 用 ROM 平台密钥签名 APK
2. 部署到 /system/priv-app/NoteVE/NoteVE.apk
3. 部署白名单到 /system/etc/permissions/com.noteVE.xml
4. 重启生效
```

> **权限白名单文件名必须是 `com.noteVE.xml`**（即「包名.xml」），
> 不能是 `privapp-permissions-com.noteVE.xml`，否则系统不会识别。

### Magisk 模块（可选，Root 版）

仓库提供正式 Magisk 模块源码（`distribution/magisk/`），功能极简：
把应用与权限白名单以**静态 overlay** 方式挂载到系统分区，使应用获得 priv-app 身份。

---

## 从源码构建

```bash
# 环境：JDK 17 + Android SDK（API 34/35 平台 + build-tools 34.0.0）

cp local.properties.example local.properties
# 编辑 local.properties，写入你的 SDK 路径

# 构建两个变体（未签名产物）
./gradlew assembleRootRelease assemblePlatformRelease

# 产物位置
#   app/build/outputs/apk/root/release/app-root-release-unsigned.apk
#   app/build/outputs/apk/platform/release/app-platform-release-unsigned.apk
```

> **重要构建说明**
>
> - **Room 2.6.1 不支持 KSP2**，`gradle.properties` 中不得启用 `ksp.useKSP2=true`，
>   否则会报 `IllegalStateException: unexpected jvm signature V`。
> - `settings.gradle.kts` 中 `pluginManagement {}` 与 `dependencyResolutionManagement {}`
>   是**独立作用域**，不能引用脚本顶层变量。
> - Platform 变体通过 flavor 独立 manifest（`app/src/platform/AndroidManifest.xml`）
>   声明 `sharedUserId`，而非占位符。

---

## 项目结构

```
app/src/main/java/com/noteVE/
├── data/          Room 实体 / DAO / 数据库（含迁移）
├── domain/        块模型、仓库、导入导出、附件、设置、图片解码
├── reminder/      提醒调度 / 触发 / 服务 / 震动 / 开机重建
├── backup/        备份与恢复前台服务
└── ui/            Compose 界面（列表 / 编辑 / 权限 / 关于 / 存储…）

app/src/platform/                Platform 变体专属 manifest
distribution/
├── lsposed/                     Xposed/Vector 保活模块源码（通用）
└── magisk/                      Magisk 模块（静态 overlay）
docs/                            安装与发行文档
```

核心数据模型是 **Block 序列**（`domain/Block.kt`）：

```kotlin
sealed class Block {
    data class Text(val html: String) : Block()
    data class Image(val path: String) : Block()
    data class Audio(val path: String) : Block()
    data class File(val path: String) : Block()
}
```

正文以 JSON 序列化存入 `Note.body`，**`body` 是附件结构的唯一事实来源**。

---

## 许可证

本项目采用 **Apache License 2.0**，详见 [LICENSE](LICENSE)。

第三方依赖及其许可证见 [NOTICE.md](NOTICE.md)。

---

## 反馈

欢迎提交 Issue 反馈问题（请附设备型号、Android 版本、复现步骤与日志）。

考虑到本项目的开发方式，Issue 中描述越具体（现象 + 复现 + 期望），越可能被有效处理。

---

## English Summary

**VE NoteApp** is a lightweight, ad-free, offline Android native note-taking app,
optimized for small screens (smartwatches) while remaining usable on phones.

> **Full disclosure**: This project's code and engineering are **entirely produced by AI coding agents**.
> The developer (VFearIE) does not write code directly — their role is product definition,
> interaction requirements, technical trade-offs, real-device testing, and iterative feedback.
> Architecture, source code, comments, documentation, and build scripts are all AI-generated.

- **Features**: block-based rich text editor, inline image/audio/file attachments at cursor,
  exact alarms, custom `.tar.gz` import/export, dark mode, Chinese/English UI.
- **Compatibility**: targets Android 7–14 (API 24–34). Alarm behavior was verified on a real
  **Android 9** smartwatch only; other versions are code-level adaptations, not device-verified.
- **Editions**: Root (with an Xposed/Vector keep-alive module) and Platform (signed with your ROM key).
- **License**: Apache-2.0.
