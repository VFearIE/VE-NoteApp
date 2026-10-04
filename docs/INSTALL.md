# 安装指南

VE笔记提供两个版本，**按需选择其一**。

> ⚠️ 两个版本**不可同时安装**（同一包名）。切换版本前请先卸载，或使用各自的更新方式。

---

## 一、Root 版（个人用户）

### 需要什么

- 已 Root 的设备（Magisk）
- Xposed 框架：**LSPosed** 或 **Vector**（二者其一）
- 三个文件（见 Release）：
  - `NoteVE-root-<版本>.apk`
  - `NoteVE-LSPosed-<版本>.apk`
  - `com.noteVE.xml`

### 步骤

**1. 安装应用**

```text
直接安装 NoteVE-root-<版本>.apk 即可（普通安装，无需特殊操作）。
```

**2. 安装保活模块**

```text
安装 NoteVE-LSPosed-<版本>.apk
```

**3. 在框架管理器中启用**

打开 LSPosed / Vector 管理器 → 模块列表 → 找到「VE笔记 保活」→ 启用。

**4. 设置作用域（关键）**

必须勾选**两项**：

| 作用域 | 为什么必须 |
|---|---|
| **系统框架**（显示为 `system`） | Hook 点在 `system_server` 内，不勾选则拦截不生效 |
| **com.noteVE** | 模块需要在该进程内登记保护名单 |

**5. 重启设备**

> 作用域变更只对**新启动的进程**生效，而 Hook 点在 `system_server` 中，
> 因此**必须重启设备**（重启应用无效）。

### 验证是否生效

```text
1. 打开 VE笔记，新建一条 2 分钟后的提醒
2. 回到桌面，从「最近任务」把应用划掉
3. 等待提醒时间到达
4. 若正常弹出停驻通知并震动 → 保活生效
```

也可用命令检查（需要 root）：

```sh
dumpsys package com.noteVE | grep 'User 0:'
# 关注 stopped=true / false —— 划卡后若仍为 false，说明拦截成功
```

### 可选：Magisk 模块

如果希望应用获得 **priv-app（特权应用）** 身份（自动获得权限、提醒更可靠），
可使用 `distribution/magisk/` 提供的模块：

```sh
sh pack.sh NoteVE-root-<版本>.apk     # 打包出 zip
# 在 Magisk 中刷入该 zip，然后重启
```

模块做的事非常简单：把 APK 与权限白名单以静态 overlay 方式挂到系统分区。

---

## 二、Platform 版（ROM 集成）

### 需要什么

- ROM 的**平台签名密钥**（platform key）
- 两个文件：
  - `NoteVE-platform-<版本>-unsigned.apk`（未签名）
  - `com.noteVE.xml`

### 步骤

**1. 用平台密钥签名**

```bash
apksigner sign \
  --ks platform.jks \
  --ks-key-alias platform \
  --out NoteVE-platform-signed.apk \
  NoteVE-platform-<版本>-unsigned.apk
```

**2. 部署应用**

```text
/system/priv-app/NoteVE/NoteVE.apk
```

**3. 部署权限白名单**

```text
/system/etc/permissions/com.noteVE.xml
```

> **★ 文件名必须是 `com.noteVE.xml`**（即「包名.xml」）。
> 不能写成 `privapp-permissions-com.noteVE.xml` —— 那样系统**不会识别**，
> 所有特权权限都将无法授予。

**4. 权限**

```sh
chmod 0644 /system/priv-app/NoteVE/NoteVE.apk
chmod 0644 /system/etc/permissions/com.noteVE.xml
```

**5. 重启**

系统在开机时扫描 `priv-app` 并读取白名单，重启后生效。

### 为什么 Platform 版需要声明 sharedUserId

Platform 版声明 `android.uid.system`，与系统进程同 UID 运行，
从而避免被系统按普通应用回收，提醒可靠性显著提升。
**该声明要求 APK 必须用平台密钥签名**，否则无法安装。

---

## 三、常见问题

### Q：为什么划掉应用后提醒就不响了？

在部分设备上，「从最近任务划掉」实际执行的是 `force-stop`，它会：

1. 杀掉应用进程
2. **清空该应用的全部 `AlarmManager` 闹钟**
3. 把包状态置为 `stopped`（此后无法接收隐式广播、不能启动前台服务）

普通应用无法从应用层规避这一行为，因此需要：

- **Root 版**：使用保活模块在系统层拦截 `force-stop`
- **Platform 版**：以系统应用身份运行，系统不会如此回收

### Q：保活模块能用于其它应用吗？

可以。该模块是**通用设计**：作用域即保护名单 —— 勾选哪个应用，就保护哪个应用。

### Q：提醒不准时？

本项目使用 `AlarmManager` 精确闹钟。请确认：

- 应用已获得精确闹钟权限（`USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM`）
- 若为 priv-app，`com.noteVE.xml` 已正确部署且文件名正确
- 系统未对该应用施加额外的电池限制

### Q：数据存在哪里？

全部存于应用私有目录，完全离线：

```text
/data/data/com.noteVE/
├── databases/notes.db       笔记数据库
├── files/attachments/       图片与任意文件附件
├── files/recordings/        录音
└── cache/thumbnails/        视频缩略图（可清理，会自动重建）
```

### Q：如何备份？

应用内「设置 → 导出」或笔记列表右上角菜单 →「全量备份」，产出 `.tar.gz` 包，
包含全部笔记与附件。恢复时选择「导入备份包」即可（自动识别单条/全量）。
