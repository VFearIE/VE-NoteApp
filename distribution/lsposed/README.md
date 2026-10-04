# Xposed 保活模块（通用）

让指定应用**免疫「最近任务划卡」**，解决「划卡后提醒失效」的问题。

> **本模块是通用的**，不绑定任何具体应用。默认保护 VE笔记，可自行增删保护对象。

---

## 原理

实测：本设备上的「最近任务划卡」等价于
`ActivityManagerService.forceStopPackage(pkg, userId)`，副作用有三：

1. 杀掉应用进程
2. **清空该应用的全部 `AlarmManager` 闹钟** ← 提醒彻底失效的根本原因
3. 置包状态 `stopped=true`（此后收不到隐式广播、不能启动前台服务）

`persistent`、`excludeFromRecents`、无障碍保活等方案**都挡不住**
（前台瞬间划卡即可完成，`persistent` 还会拖垮 system_server）。

本模块在 `system_server` 内 Hook `forceStopPackage`，
**目标包名在保护名单内时直接短路**（不执行原方法）
→ 进程、闹钟、包状态全部不受影响。

---

## 安装与作用域（★ 请按实际实现配置）

```text
1. 安装模块 APK
2. 在 LSPosed / Vector 管理器中【启用】本模块
3. ★ 作用域只需勾选一项：「系统框架」
      · 在 Vector 的 scope 配置里，这一项显示为  system
      · 代码回调中它的 packageName 表现为      android
      （二者指同一个东西，只是两处命名不同）
4. 重启设备
```

> **注意**：Hook 点在 `system_server` 内，因此**只需勾选「系统框架」**。
> 被保护的应用**不需要**加入作用域 —— 保护范围由下方「保护名单」决定。

为什么必须重启：作用域变更只对**新启动的进程**生效，而 Hook 点在 `system_server`，
重启应用无用。

---

## 保护名单（决定保护谁）

名单来自两处，**任一命中即保护**：

### ① 内置名单（编译期，开箱即用）

见 `src/com/vfearie/keepalive/ProtectList.java` 的 `BUILT_IN`：

```java
private static final String[] BUILT_IN = {
    "com.noteVE",     // 默认：VE笔记
};
```

要保护其它应用：改这里 → 重新编译（`sh build.sh`）→ 重装模块 → 重启。

### ② 外部名单文件（可选，需 root，免重编译）

```sh
# 每行一个包名，# 开头为注释
echo "com.example.app" >> /data/local/tmp/xposed_keepalive_list
```

文件变更后需重启（或让 system_server 重载）才生效。

> 为什么用 `/data/local/tmp`：该目录对 `shell`(2000) 与 `root`(0) 可写，
> 而 `system_server` 可读，是 root 场景下最合适的交换位置。
> （曾经设想让被勾选的 app 各自写名单文件，**实测不可行** ——
> 该目录权限为 `shell:shell 771`，普通应用 uid ≥ 10000 无写权限。）

---

## 验证是否生效

```sh
# 1. 打开受保护的应用，然后从最近任务划掉
# 2. 检查包状态
dumpsys package <包名> | grep 'User 0:'
#    关注 stopped=true / false —— 划卡后仍为 false 说明拦截成功

# 3. 检查闹钟是否还在
dumpsys alarm | grep <包名>

# 4. 查看模块日志（过滤器 XposedKeepAlive）
logcat -s XposedKeepAlive
#    应能看到：★ 已拦截 force-stop: <包名>
```

---

## 与 Platform 版的关系

**二者互斥**，只需其一：

| | Root 版 | Platform 版 |
|---|---|---|
| 组成 | 应用 APK + **本模块** | 应用 APK（平台签名） |
| 需要 root | 是 | 否 |
| 需要平台签名 | 否 | 是 |
| 保活原理 | 系统层拦截 `force-stop` | 以 `system` 身份运行，系统不如此回收 |

---

## 构建

```sh
sh build.sh          # 完整构建（aapt2 → javac → d8 → zipalign → apksigner）
# 产物：XposedKeepAlive.apk
```

**★ 四个已验证的坑**（改代码时勿重蹈）：

| # | 坑 | 说明 |
|---|---|---|
| 1 | `meta-data` 层级 | 必须放在 `<application>` **内**，否则管理器识别不出模块 |
| 2 | 桩的返回类型 | `findAndHookMethod` 的 stub **必须返回 `XC_MethodHook.Unhook`**，写成 `void` 会编译通过但运行时 `NoSuchMethodError` |
| 3 | scope 命名 | Vector 的 scope 配置项叫 **`system`**，而代码里 `lpparam.packageName` 是 `android` —— 不是一回事 |
| 4 | 桩不进 dex | API 桩必须用 `d8 --lib` 引用，不能编译进 `classes.dex` |

---

## 目录

```
AndroidManifest.xml                    模块清单（含 xposed meta-data）
assets/xposed_init                     框架入口声明
src/com/vfearie/keepalive/
├── KeepAliveHook.java                 钩子实现（Hook AMS.forceStopPackage）
└── ProtectList.java                   保护名单（内置 + 可选外部文件）
stubs/                                 Xposed API 编译期桩（不进 dex）
build.sh                              构建脚本
```
