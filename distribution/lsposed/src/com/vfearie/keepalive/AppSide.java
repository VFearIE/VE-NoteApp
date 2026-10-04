package com.vfearie.keepalive;

import android.app.Application;
import android.os.Process;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import android.content.Intent;

/**
 * 应用侧（作用域内）：向 system_server 自报包名。
 *
 * ## 判定依据
 *
 * 能走到这里，就说明**模块被注入进了本进程** ——
 * 而模块只会注入到「作用域内」的进程。
 * 所以「我被加载」≡「我在作用域内」≡「我应受保护」。
 *
 * ## 自报时机
 *
 * 挂钩 `Instrumentation.callApplicationOnCreate(Application)`：
 * - 它在 `Application.onCreate` **之后**执行，此时 Context 可用，可发广播
 * - 每个进程**只调用一次**，天然不会重复自报
 *
 * ## 过滤系统组件进程
 *
 * system_server 拉起的系统组件（`com.android.providers.settings`、
 * `com.android.server.telecom`、`com.android.location.fused` 等）
 * 也会各自触发 Xposed 回调。它们的 uid 是 `system`(1000)，
 * 属于「系统框架自身」的一部分而非独立应用，**不应纳入保护名单**
 * （否则名单会被污染，且拦截它们没有意义）。
 *
 * 判定方式：真实应用 uid ≥ {@link Process#FIRST_APPLICATION_UID}(10000)。
 *
 * ## 一个不需要担心的限制
 *
 * 若应用**在模块启用前已处于运行状态**，它不会立刻自报。
 * 但这不成问题 —— 因为装载拦截器本身**必须重启设备**，
 * 而重启会终结所有应用进程，它们会在下次启动时正常自报。
 */
final class AppSide {

    private AppSide() { }

    /** 在作用域内的应用进程中安装自报钩子。 */
    static void report(XC_LoadPackage.LoadPackageParam lpparam) {
        final String pkg = lpparam.packageName;

        // 过滤系统组件进程（uid = system(1000) 等）
        final int uid;
        try {
            uid = Process.myUid();
        } catch (Throwable t) {
            return;
        }
        if (uid < Process.FIRST_APPLICATION_UID) {
            Log.i("跳过系统进程（uid=" + uid + "）: " + pkg);
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.Instrumentation", lpparam.classLoader,
                    "callApplicationOnCreate", Application.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            sendReport(pkg, param.args[0]);
                        }
                    });
        } catch (Throwable t) {
            Log.e("挂钩 callApplicationOnCreate 失败（" + pkg + "）: " + t);
        }
    }

    /** 发送自报广播（定向投递给 system_server）。 */
    private static void sendReport(String pkg, Object appArg) {
        try {
            if (!(appArg instanceof Application)) return;
            Application app = (Application) appArg;

            Intent intent = new Intent(Constants.ACTION_REGISTER);
            intent.putExtra(Constants.EXTRA_PKG, pkg);
            // ★ 定向到 system_server：
            //   ① 避免广播被其它应用截获/干扰
            //   ② 不受隐式广播相关的任何限制影响
            intent.setPackage(KeepAliveHook.SYS_PKG);

            app.sendBroadcast(intent);
            Log.i("已自报（在作用域内）: " + pkg);
        } catch (Throwable t) {
            Log.w("自报失败 " + pkg + ": " + t);
        }
    }
}
