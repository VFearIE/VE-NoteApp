package com.vfearie.keepalive;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * system_server 侧：安装 force-stop 拦截器 + 收集作用域内应用的自报。
 *
 * ## 为什么需要「应用自报」
 *
 * `handleLoadPackage` 是**按目标进程分别回调**的：
 * 作用域勾了 `系统框架 + A + B`，则会在三个进程里各回调一次。
 * 而拦截器位于 system_server，它**看不到**用户还勾选了谁 ——
 * 故 A、B 需各自把包名告知 system_server。
 *
 * ## 为什么用广播而非共享文件
 *
 * 三种文件方案在本机均不可行（已实测）：
 *
 * | 位置 | 不可行原因 |
 * |---|---|
 * | `/data/local/tmp` | 权限 `shell:shell 771`，普通应用（uid ≥ 10000）**无写权限** |
 * | 应用私有目录 | system_server（uid 1000）**读不到**（DAC 限制） |
 * | `/data/adb`（框架配置所在） | root-only，system_server 同样读不到 |
 *
 * 动态注册的接收器不受 Android 8+ 隐式广播限制，故广播方案可行。
 *
 * ## ★ 两个必须处理好的时序/环境问题（均已踩坑验证）
 *
 * ### 1. 接收器必须等 AMS 就绪后再注册
 *
 * `handleLoadPackage("android")` 发生在 system_server 启动的**极早期**，
 * 此时 `IActivityManager` 尚未注册进 `ServiceManager`，
 * 直接 `registerReceiver` 会抛 `NullPointerException` 且静默失效。
 * → 改为挂钩 `AMS.systemReady` / `finishBooting`（枚举全部重载，
 *   不依赖具体签名），在其后注册。
 *
 * ### 2. Context 不能假设某个 getter 存在
 *
 * 不同 ROM 的 AMS 实现差异很大（例如本机为厂商子类
 * `ActivityManagerServiceEx`，**没有** `getUiContext()`）。
 * 若「一个失败即整体中断」，会导致注册永远不成功。
 * → 逐个尝试候选方法/字段，任一成功即可。
 */
final class SystemServerSide {

    private static final String AMS = "com.android.server.am.ActivityManagerService";

    private static volatile boolean installed = false;
    private static volatile boolean receiverInstalled = false;

    private SystemServerSide() { }

    /** 安装（幂等）。 */
    static void install(ClassLoader cl) {
        if (installed) return;
        synchronized (SystemServerSide.class) {
            if (installed) return;
            Log.i("=== 在 system_server 中装载 ===");
            installForceStopHook(cl);
            installBootTimeHooks(cl);
            installed = true;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 拦截器
    // ═══════════════════════════════════════════════════════════

    /**
     * Hook `forceStopPackage`。
     *
     * 枚举所有重载并逐个尝试，避免依赖特定版本的签名：
     * - `forceStopPackage(String, int)` —— Android 9+ 主用
     * - `forceStopPackage(String)`       —— 更早版本
     */
    private static void installForceStopHook(ClassLoader cl) {
        try {
            Class<?> ams = XposedHelpers.findClass(AMS, cl);
            if (ams == null) {
                Log.e("找不到 AMS，无法安装拦截器");
                return;
            }
            int n = 0;
            for (Method m : ams.getDeclaredMethods()) {
                if (!"forceStopPackage".equals(m.getName())) continue;
                Class<?>[] ps = m.getParameterTypes();
                // 只处理首参为 String（包名）的重载
                if (ps.length == 0 || ps[0] != String.class) continue;

                Object[] args = new Object[ps.length + 1];
                System.arraycopy(ps, 0, args, 0, ps.length);
                args[ps.length] = new StopInterceptor();
                try {
                    XposedHelpers.findAndHookMethod(ams, m.getName(), args);
                    n++;
                    Log.i("  拦截器挂钩: forceStopPackage(" + ps.length + " 参)");
                } catch (Throwable t) {
                    Log.w("  挂钩 forceStopPackage(" + ps.length + " 参) 失败: " + t);
                }
            }
            Log.i("拦截器挂钩总数: " + n);
        } catch (Throwable t) {
            Log.e("安装拦截器异常: " + t);
        }
    }

    /** 命中保护名单（= 作用域内应用）则短路，不执行原方法。 */
    private static final class StopInterceptor extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                if (param.args == null || param.args.length == 0) return;
                Object a0 = param.args[0];
                if (!(a0 instanceof String)) return;
                String pkg = (String) a0;
                if (ProtectList.contains(pkg)) {
                    param.setResult(null);   // 吞掉这次强制停止（原方法返回 void）
                    Log.i("★ 已拦截 force-stop: " + pkg
                            + "（名单 " + ProtectList.size() + " 个）");
                }
            } catch (Throwable t) {
                Log.w("拦截器异常: " + t);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 延迟注册（等 AMS 就绪）
    // ═══════════════════════════════════════════════════════════

    /**
     * 挂钩所有 `systemReady*` / `finishBooting` 重载，
     * 在其 after 回调里注册自报接收器（幂等，故多挂几个也无妨）。
     */
    private static void installBootTimeHooks(ClassLoader cl) {
        try {
            Class<?> ams = XposedHelpers.findClass(AMS, cl);
            if (ams == null) return;
            int n = 0;
            for (Method m : ams.getDeclaredMethods()) {
                String name = m.getName();
                if (!"systemReady".equals(name) && !"finishBooting".equals(name)) continue;
                Class<?>[] ps = m.getParameterTypes();
                Object[] args = new Object[ps.length + 1];
                System.arraycopy(ps, 0, args, 0, ps.length);
                args[ps.length] = new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        ClassLoader declCl = p.method.getDeclaringClass().getClassLoader();
                        installSelfReportReceiver(p.thisObject, declCl);
                    }
                };
                try {
                    XposedHelpers.findAndHookMethod(ams, name, args);
                    n++;
                } catch (Throwable ignored) {
                    // 该重载无法挂钩，跳过
                }
            }
            Log.i("延迟注册挂钩数: " + n);
        } catch (Throwable t) {
            Log.e("安装延迟注册异常: " + t);
        }
    }

    /** 注册广播接收器（幂等）。 */
    private static void installSelfReportReceiver(Object amsInstance, ClassLoader cl) {
        if (receiverInstalled) return;
        synchronized (SystemServerSide.class) {
            if (receiverInstalled) return;
            try {
                Context ctx = resolveContext(amsInstance, cl);
                if (ctx == null) {
                    Log.e("取不到 Context，接收器注册失败（待下次时机重试）");
                    return;
                }

                BroadcastReceiver receiver = new SelfReportReceiver(ctx);
                IntentFilter filter = new IntentFilter(Constants.ACTION_REGISTER);
                try {
                    // Android 13+ 需显式指定导出标志；失败则回退旧重载
                    XposedHelpers.callMethod(ctx, "registerReceiver", receiver, filter,
                            Context.RECEIVER_EXPORTED);
                } catch (Throwable t1) {
                    ctx.registerReceiver(receiver, filter);
                }
                receiverInstalled = true;
                Log.i("自报接收器注册: true");
            } catch (Throwable t) {
                Log.e("注册自报接收器失败（待下次时机重试）: " + t);
            }
        }
    }

    /**
     * 逐个尝试获取 system_server 的 Context。
     *
     * 接口/字段名在不同 ROM 上不一致（本机 `ActivityManagerServiceEx` 就没有
     * `getUiContext`），故**每一项单独 try**，任一成功即可。
     */
    private static Context resolveContext(Object ams, ClassLoader cl) {
        Context ctx;
        ctx = byMethod(ams, "getUiContext");
        if (ctx == null) ctx = byMethod(ams, "getSystemContext");
        if (ctx == null) ctx = byMethod(ams, "getContext");
        if (ctx == null) ctx = byField(ams, "mContext");
        if (ctx == null) ctx = byField(ams, "mUiContext");
        if (ctx == null) ctx = byField(ams, "mSystemContext");
        if (ctx == null) ctx = byActivityThread(cl);
        if (ctx != null) {
            Log.i("  Context 来源: " + ctx.getClass().getName());
        }
        return ctx;
    }

    private static Context byMethod(Object ams, String method) {
        if (ams == null) return null;
        try {
            Object c = XposedHelpers.callMethod(ams, method);
            return (c instanceof Context) ? (Context) c : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Context byField(Object ams, String field) {
        if (ams == null) return null;
        try {
            Object c = XposedHelpers.getObjectField(ams, field);
            return (c instanceof Context) ? (Context) c : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 兜底：`ActivityThread.currentActivityThread().getSystemContext()`。 */
    private static Context byActivityThread(ClassLoader cl) {
        try {
            Object at = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", cl),
                    "currentActivityThread");
            if (at == null) return null;
            Object sc = XposedHelpers.callMethod(at, "getSystemContext");
            return (sc instanceof Context) ? (Context) sc : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 自报接收 + 身份校验
    // ═══════════════════════════════════════════════════════════

    /**
     * 接收作用域内应用的自报，**并校验发送方身份**。
     *
     * 接收器是 `RECEIVER_EXPORTED`（必须如此才能跨应用投递），
     * 因此理论上任何应用都能发送该广播。
     * 这里用发送方 uid 反查其包名做校验，**拒绝伪造自报**：
     * 声称的包名必须确实属于发送方 uid。
     */
    private static final class SelfReportReceiver extends BroadcastReceiver {

        private final Context sysCtx;

        /** 当前正在处理的 Intent（供 uid 解析使用）。 */
        private Intent reportedIntent;

        SelfReportReceiver(Context sysCtx) {
            this.sysCtx = sysCtx;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (intent == null || !Constants.ACTION_REGISTER.equals(intent.getAction())) return;
                String pkg = intent.getStringExtra(Constants.EXTRA_PKG);
                if (pkg == null || pkg.isEmpty()) return;

                reportedIntent = intent;
                if (!verifySender(pkg)) {
                    Log.w("拒绝来源不明的自报: " + pkg);
                    return;
                }

                boolean isNew = ProtectList.register(pkg);
                Log.i((isNew ? "＋ 登记受保护应用: " : "＝ 重复自报，忽略: ") + pkg
                        + "（名单 " + ProtectList.size() + " 个）");
            } catch (Throwable t) {
                Log.w("处理自报异常: " + t);
            }
        }

        /** 无法确定 uid 时的哨兵值。 */
        private static final int UID_UNKNOWN = Integer.MIN_VALUE;

        /** 按「新 → 旧」顺序尝试取得发送方 uid；全部不可用返回 {@link #UID_UNKNOWN}。 */
        private int resolveSenderUid() {
            // ① API 34+：官方公开 API
            try {
                int uid = getSentFromUid();
                Log.i("  发送方 uid 来源: getSentFromUid() = " + uid);
                return uid;
            } catch (Throwable ignored) {
                // 该版本没有此方法 → 继续尝试
            }
            // ② 隐藏 API（API 19+）：Intent.getSenderUid()
            try {
                Object v = XposedHelpers.callMethod(reportedIntent, "getSenderUid");
                if (v instanceof Integer) {
                    Log.i("  发送方 uid 来源: Intent.getSenderUid() = " + v);
                    return (Integer) v;
                }
            } catch (Throwable ignored) {
                // 隐藏 API 被屏蔽 → 无法确定
            }
            return UID_UNKNOWN;
        }

        /**
         * 校验「声称的包名」确实属于「发送方 uid」。
         *
         * ## 为什么需要多级回退
         *
         * 取「发送方 uid」的公开 API 各版本不一：
         *
         * | 方式 | 可用版本 |
         * |---|---|
         * | `BroadcastReceiver.getSentFromUid()` | API 34+ |
         * | `Intent.getSenderUid()`（隐藏 API） | API 19+（需反射） |
         *
         * 故按「新 → 旧」顺序尝试；两者都不可用时**无法校验**。
         *
         * ## 无法校验时的取舍（fail-open）
         *
         * 本模块**选择放行并记录警告**，理由是伪造的实际影响很低：
         * 伪造者只能让自己的应用「免疫划卡」，**无法获取任何权限或数据**。
         * 而若采取 fail-closed（一律拒绝），一旦某 ROM 屏蔽了隐藏 API，
         * 模块就会**整体失效** —— 代价远大于收益。
         *
         * 在 API 34+ 上该校验是完整生效的。
         *
         * @return false 表示校验不通过（应忽略该自报）
         */
        private boolean verifySender(String pkg) {
            int uid = resolveSenderUid();
            if (uid == UID_UNKNOWN) {
                Log.w("无法确定发送方 uid（本 ROM 未开放相关 API），放行: " + pkg);
                return true;   // fail-open，理由见上
            }
            if (uid < 0) {
                Log.w("发送方 uid 无效，拒绝: pkg=" + pkg);
                return false;
            }
            try {
                PackageManager pm = sysCtx.getPackageManager();
                String[] pkgs = pm.getPackagesForUid(uid);
                if (pkgs == null) {
                    Log.w("uid " + uid + " 无对应包名，拒绝: " + pkg);
                    return false;
                }
                for (String p : pkgs) {
                    if (pkg.equals(p)) return true;
                }
                Log.w("包名与发送方 uid 不匹配（uid=" + uid + "）: " + pkg);
                return false;
            } catch (Throwable t) {
                // 查不到就无法证明合法性 → 保守拒绝（避免放行伪造自报）
                Log.w("校验发送方失败，保守拒绝: " + pkg + " / " + t);
                return false;
            }
        }
    }
}
