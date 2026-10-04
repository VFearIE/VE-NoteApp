# Xposed 保活模块（通用）

让应用**免疫「最近任务划卡」**，解决「划卡后提醒失效」的问题。

> **作用域 = 保护对象**：在框架管理器里勾选哪些应用，就保护哪些应用。
> 不需要改代码，也不需要维护任何名单文件。

---

## 一分钟说明

```text
1. 安装模块 → 在 LSPosed / Vector 中【启用】
2. 作用域勾选：
     ☑ 「系统框架」       ← 必须（拦截器运行在 system_server 内）
     ☑ 要保护的应用       ← 勾选谁就保护谁
3. 重启设备
```

---

## 为什么需要它

在部分 ROM 上，「从最近任务划掉应用」实际执行的是
`ActivityManagerService.forceStopPackage(pkg, userId)`，它有三个副作用：

| # | 副作用 | 能否自愈 |
|---|---|---|
| 1 | 杀掉应用进程 | ✅ 下次启动即可恢复 |
| 2 | **清空该应用的全部 `AlarmManager` 闹钟** | ❌ **不可恢复 —— 提醒永久丢失** |
| 3 | 置包状态 `stopped=true` | ❌ 收不到隐式广播、无法启动前台服务 |

其中**只有第 2 项不可自愈**，因此本模块只针对它：拦截 `forceStopPackage`，
让闹钟与包状态不被破坏。

> **为什么不需要拦截其它杀进程路径**（`killBackgroundProcesses`、LMK 回收等）：
> 闹钟存放在 **AMS 内部**，不在应用进程里。上述路径只杀进程，闹钟不受影响；
> 应用下次启动可自行重建。**只有 force-stop 会连带清空闹钟。**

其它常见保活手段为什么不行：

| 手段 | 结果 |
|---|---|
| `android:persistent` | 挡不住划卡，且会让应用崩溃波及 `system_server` |
| `android:excludeFromRecents` | 前**台瞬间**划卡仍可完成 |
| 无障碍 / 通知监听保活 | force-stop 会解除绑定 |
| 电池优化豁免 | 与此无关 |

---

## 原理

### 名单从哪来：作用域

核心依据一条事实：

> **模块只会被注入到「作用域内」的进程。**

```text
     作用域: [系统框架] + [A] + [B]
                    │
      ┌─────────────┼─────────────┐
      ▼             ▼             ▼
 system_server     进程 A        进程 B
┌──────────────┐ ┌──────────┐ ┌──────────┐
│ 装拦截器      │ │ 自报 "A" │ │ 自报 "B" │
│ 收自报        │◄┤ (定向广播)│ │ (定向广播)│
└──────┬───────┘ └──────────┘ └──────────┘
       ▼
  名单 = {A, B}
       ▼
划卡 → forceStopPackage("A") → 命中 → 短路（不执行）
```

于是「在作用域内」≡「受保护」。

### 为什么要跨进程自报

`handleLoadPackage` 是**按目标进程分别回调**的：上例中会在三个进程里各回调一次。
而拦截器位于 `system_server`，它**看不到**用户还勾选了谁 —— 故 A、B 需各自把包名告知它。

**为什么用广播而不是共享文件**（三种文件方案均已实测不可行）：

| 位置 | 不可行原因 |
|---|---|
| `/data/local/tmp` | 权限 `shell:shell 771`，普通应用（uid ≥ 10000）**无写权限** |
| 应用私有目录 | `system_server`（uid 1000）**读不到**（DAC 限制） |
| `/data/adb`（框架配置所在） | root-only，`system_server` 同样读不到 |

### 名单生命周期

名单只存在于**内存**，随 `system_server` 进程存续：

- 应用进程启动 → 自报 → 加入名单
- 设备重启 → 名单清空 → 各应用下次启动时重新自报

**不做持久化是刻意的**：这样把某应用移出作用域后，其保护会在进程重启时自动失效，
不会留下幽灵条目，也不需要人工清理。

> 「重启后名单为空」没有实际损失：应用若在本次开机后从未启动，
> 它本来也不会有已注册的闹钟（`AlarmManager` 闹钟不跨重启保留）。

---

## 验证

```sh
# 1. 打开受保护的应用，再从最近任务划掉

# 2. 看模块日志
logcat -s XposedKeepAlive
#    应用启动时: 已自报（在作用域内）: <包名>
#    system 侧 : ＋ 登记受保护应用: <包名>
#    划卡时    : ★ 已拦截 force-stop: <包名>

# 3. 检查包状态 —— 应为 stopped=false
dumpsys package <包名> | grep 'User 0:'
```

---

## 安全说明

自报接收器必须 `RECEIVER_EXPORTED`（否则跨应用广播收不到），
因此**理论上任何应用都能发送该广播**。模块对此做**发送方身份校验**：

| 取 uid 的方式 | 可用版本 |
|---|---|
| `BroadcastReceiver.getSentFromUid()` | Android 14+ |
| `Intent.getSenderUid()`（隐藏 API，反射调用） | Android 4.4+ |

按「新 → 旧」回退尝试，并校验**声称的包名确实属于发送方 uid**。

两者都不可用时（个别 ROM 屏蔽隐藏 API）**放行并记录警告** —— 这是刻意取舍：
伪造的实际影响很低（只能让某应用免疫划卡，**无法获取任何权限或数据**），
而若改为一律拒绝，一旦隐藏 API 不可用就会**整个模块失效**，代价更大。

---

## 与 Platform 版的关系

**二者互斥**，按需选一：

| | Root 版 | Platform 版 |
|---|---|---|
| 组成 | 应用 APK + **本模块** | 应用 APK（平台签名） |
| 需要 root | 是 | 否 |
| 需要平台签名 | 否 | 是 |
| 保活原理 | 系统层拦截 `force-stop` | 以 `system` 身份运行，系统不如此回收 |

---

## 构建

```sh
sh build.sh        # aapt2 → javac → d8 → zipalign → apksigner
# 产物: XposedKeepAlive.apk
```

工具链与签名可通过环境变量覆盖：

```sh
BT=/path/to/build-tools AJ=/path/to/android.jar \
KS=~/my.jks KS_PASS=xxx KS_ALIAS=myalias sh build.sh
```

### API 桩（`stubs/`）

`de.robv.android.xposed.*` 是 **Xposed 旧版 API 的编译期桩**，运行时由框架提供。
必须用 `d8 --lib` 引用，**不能编译进 `classes.dex`**。

> ⚠️ `XposedHelpers.findAndHookMethod` 的桩**必须返回 `XC_MethodHook.Unhook`**，
> 写成 `void` 会「编译通过但运行时抛 `NoSuchMethodError`」。

---

## 代码结构

```
src/com/vfearie/keepalive/
├── KeepAliveHook.java      入口：按目标进程分发
├── SystemServerSide.java   system_server 侧：装拦截器 + 收自报（含身份校验）
├── AppSide.java            应用侧（作用域内）：向 system_server 定向自报
├── ProtectList.java        保护名单（内存注册表，线程安全）
├── Constants.java          跨进程约定（action / extra）
└── Log.java                统一日志
```

设计要点：入口只做分发，两侧逻辑完全隔离 —— 便于单独阅读与修改。
