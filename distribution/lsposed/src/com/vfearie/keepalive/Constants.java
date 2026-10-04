package com.vfearie.keepalive;

/**
 * 跨进程通信的约定常量。
 *
 * 集中定义，避免两侧写错字符串导致静默失效。
 */
final class Constants {

    /**
     * 自报广播的 action。
     *
     * 命名带包名（`com.vfearie.keepalive.`）以避免与其它模块冲突。
     */
    static final String ACTION_REGISTER = "com.vfearie.keepalive.action.REGISTER";

    /** 广播中携带的包名。 */
    static final String EXTRA_PKG = "pkg";

    private Constants() { }
}
