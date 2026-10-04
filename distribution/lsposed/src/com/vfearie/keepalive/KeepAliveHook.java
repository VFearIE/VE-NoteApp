package com.vfearie.keepalive;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 通用保活模块（Xposed / LSPosed / Vector）。
 *
 * ## 解决的问题
 *
 * 实测：「最近任务划卡」在本设备上会调用
 * `ActivityManagerService.forceStopPackage(pkg, userId)`，副作用：
 *   ① 杀掉应用进程
 *   ② **清空该应用的全部 AlarmManager 闹钟**   ← 提醒彻底失效
 *   ③ 置包状态 `stopped=true`（此后收不到隐式广播、不能起前台服务）
 *
 * ## 做法
 *
 * 在 system_server 内 Hook `forceStopPackage`，目标在保护名单内时**短路返回**。
 *
 * ## 使用
 *
 * 1. Vector / LSPosed 中启用本模块
 * 2. **★ 作用域必须勾选「系统框架」** —— 在 Vector 的 scope 配置里这一项的名字是
 *    **`system`**（不是 `android`）；
 *    代码里回调的 `lpparam.packageName` 才表现为 `"android"`。二者不是一个东西。
 * 3. 要保护哪些 app → 改 {@link ProtectList#BUILT_IN} 重新编译，
 *    或用 root 往 `/data/local/tmp/xposed_keepalive_list` 追加包名（每行一个）
 */
public class KeepAliveHook implements IXposedHookLoadPackage {

    /** system_server 在 Xposed 回调中的 packageName 标识。 */
    private static final String SYS_PKG = "android";
    private static final String AMS = "com.android.server.am.ActivityManagerService";
    private static final String TAG = "XposedKeepAlive";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || lpparam.packageName == null) return;
        log("加载: pkg=" + lpparam.packageName + " cl=" + (lpparam.classLoader != null));
        if (!SYS_PKG.equals(lpparam.packageName)) return;
        if (lpparam.classLoader == null) return;

        log("=== 在 system_server 中装载 ===");

        // 诊断：列出 AMS 所有 forceStopPackage 方法的真实签名
        dumpMethods(lpparam.classLoader);

        // Android 9+ 签名：forceStopPackage(String packageName, int userId)
        boolean ok1 = hook(lpparam.classLoader, "forceStopPackage", String.class, int.class);
        // 兼容旧签名：forceStopPackage(String packageName)
        boolean ok2 = hook(lpparam.classLoader, "forceStopPackage", String.class);

        log("挂钩结果: (String,int)=" + ok1 + "  (String)=" + ok2);
    }

    /** 诊断用：打印 AMS 中所有 forceStopPackage 重载的签名。 */
    private void dumpMethods(ClassLoader cl) {
        try {
            Class<?> ams = XposedHelpers.findClass(AMS, cl);
            if (ams == null) { log("!! 找不到 AMS 类"); return; }
            for (Method m : ams.getDeclaredMethods()) {
                if ("forceStopPackage".equals(m.getName())) {
                    StringBuilder sb = new StringBuilder("  发现: forceStopPackage(");
                    Class<?>[] ps = m.getParameterTypes();
                    for (int i = 0; i < ps.length; i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(ps[i].getSimpleName());
                    }
                    sb.append(")");
                    log(sb.toString());
                }
            }
        } catch (Throwable t) {
            log("dumpMethods 失败: " + t);
        }
    }

    private boolean hook(ClassLoader cl, String method, Object... paramTypes) {
        try {
            XposedHelpers.findAndHookMethod(AMS, cl, method,
                concat(paramTypes, new StopInterceptor()));
            return true;
        } catch (Throwable t) {
            log("挂钩 " + method + "(" + paramTypes.length + "参) 失败: " + t);
            return false;
        }
    }

    /** 拦截器：命中保护名单则短路，不执行原方法。 */
    private static final class StopInterceptor extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                if (param.args == null || param.args.length == 0) return;
                Object a0 = param.args[0];
                if (!(a0 instanceof String)) return;
                String pkg = (String) a0;
                if (ProtectList.contains(pkg)) {
                    param.setResult(null);        // 吞掉这次强制停止
                    log("★ 已拦截 force-stop: " + pkg);
                }
            } catch (Throwable t) {
                log("拦截器异常: " + t);
            }
        }
    }

    private static Object[] concat(Object[] a, Object b) {
        Object[] r = new Object[a.length + 1];
        System.arraycopy(a, 0, r, 0, a.length);
        r[a.length] = b;
        return r;
    }

    private static void log(String msg) {
        try { XposedBridge.log("[" + TAG + "] " + msg); } catch (Throwable ignored) { }
    }
}
