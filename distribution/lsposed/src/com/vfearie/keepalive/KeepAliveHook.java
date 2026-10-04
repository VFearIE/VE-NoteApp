package com.vfearie.keepalive;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 通用保活模块入口（Xposed / LSPosed / Vector）。
 *
 * ## 解决的场景
 *
 * 在部分 ROM 上，「从最近任务划掉应用」实际执行的是
 * `ActivityManagerService.forceStopPackage(pkg, userId)`。它有三个副作用：
 *
 * | # | 副作用 | 影响 |
 * |---|---|---|
 * | 1 | 杀掉应用进程 | 可恢复（下次启动即可） |
 * | 2 | **清空该应用的全部 `AlarmManager` 闹钟** | **不可恢复** —— 提醒永久丢失 |
 * | 3 | 置包状态 `stopped=true` | 收不到隐式广播、不能启动前台服务 |
 *
 * 其中只有第 2 项是**不可自愈**的，因此本模块只针对它：
 * 拦截 `forceStopPackage`，使闹钟与包状态不被破坏。
 *
 * > **为什么不需要拦截其它杀进程路径**（`killBackgroundProcesses`、
 * > `killApplication`、LMK 回收 等）：
 * > 闹钟存放在 **AMS 内部**，不在应用进程里。上述路径只杀进程，
 * > 闹钟不受影响；应用下次启动可自行重建。
 * > **只有 force-stop 会连带清空闹钟** —— 这正是拦截目标。
 *
 * ## ★ 保护名单 = 框架作用域（核心设计）
 *
 * **在 LSPosed / Vector 里勾选哪些应用，就保护哪些应用。**
 * 无需改源码、无需维护名单文件。
 *
 * 依据一条事实：**模块只会被注入到「作用域内」的进程。**
 *
 * ```text
 *   作用域: [系统框架] + [A] + [B]
 *                   │
 *       ┌───────────┼───────────┐
 *       ▼           ▼           ▼
 *  system_server   进程 A      进程 B
 *  ┌────────────┐  ┌────────┐  ┌────────┐
 *  │ 装拦截器    │  │ 自报 A │  │ 自报 B │
 *  │ 收自报      │◄─┤ (定向) │  │ (定向) │
 *  └─────┬──────┘  └────────┘  └────────┘
 *        ▼
 *   名单 = {A, B}  →  划卡时短路
 * ```
 *
 * 于是「在作用域内」≡「受保护」。
 *
 * ## 使用
 *
 * 1. 安装模块 → 在框架管理器中**启用**
 * 2. **作用域勾选**：「系统框架」+ 要保护的应用
 * 3. 重启设备（拦截器位于 system_server，作用域变更只对新进程生效）
 *
 * ## 代码结构
 *
 * | 类 | 职责 |
 * |---|---|
 * | `KeepAliveHook`（本类） | 入口分发 |
 * | {@link SystemServerSide} | system_server 侧：装拦截器、收自报 |
 * | {@link AppSide} | 应用侧（作用域内）：向 system_server 自报 |
 * | {@link ProtectList} | 保护名单（内存注册表） |
 */
public class KeepAliveHook implements IXposedHookLoadPackage {

    /** system_server 在 Xposed 回调中的 packageName 标识（所有版本恒为 "android"）。 */
    static final String SYS_PKG = "android";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || lpparam.packageName == null || lpparam.classLoader == null) {
            return;
        }
        if (SYS_PKG.equals(lpparam.packageName)) {
            SystemServerSide.install(lpparam.classLoader);
        } else {
            // 能执行到这里 ⇒ 模块被注入到该应用 ⇒ 它必然在作用域内
            AppSide.report(lpparam);
        }
    }
}
