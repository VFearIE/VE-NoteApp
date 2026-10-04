package com.vfearie.keepalive;

import de.robv.android.xposed.XposedBridge;

/**
 * 统一日志。
 *
 * 全部走 `XposedBridge.log`，输出到框架日志（过滤器 `XposedKeepAlive`）。
 * 这样两侧（system_server / 应用进程）的日志集中在一处，便于排查。
 */
final class Log {

    private static final String TAG = "XposedKeepAlive";

    private Log() { }

    static void i(String msg) {
        write("I", msg);
    }

    static void w(String msg) {
        write("W", msg);
    }

    static void e(String msg) {
        write("E", msg);
    }

    private static void write(String level, String msg) {
        try {
            XposedBridge.log("[" + TAG + "][" + level + "] " + msg);
        } catch (Throwable ignored) {
            // 框架日志不可用时静默 —— 日志失败绝不能影响主逻辑
        }
    }
}
