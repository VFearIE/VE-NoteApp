package com.vfearie.keepalive;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 保护名单 —— **由框架作用域驱动**。
 *
 * ## 名单从哪来
 *
 * 唯一来源：**作用域内的应用在进程启动时自报的包名**。
 *
 * 不需要人工维护任何列表：
 * - 想保护某应用 → 在框架管理器里把它加进作用域 → 该应用下次启动时自动登记
 * - 不想保护 → 移出作用域 → 其进程重启后即自动失效
 *
 * ## 为什么只存内存（刻意不做持久化）
 *
 * 1. **自动跟随作用域变化** —— 移出作用域的应用无需人工清理，
 *    不会留下「幽灵保护」。
 * 2. **不存在过期数据** —— 名单随 `system_server` 生命周期存续，
 *    每次开机都是干净状态。
 *
 * ### 「重启后名单为空」是否有风险？
 *
 * 没有实际损失：
 * - 应用在本次开机后若**从未启动**，它同样不会有已注册的闹钟
 *   （`AlarmManager` 闹钟不跨重启保留，需应用自行重建）；
 * - 应用一旦启动就会立即自报，此后即受保护。
 *
 * ## 线程安全
 *
 * 读（拦截器，system_server 的 Binder 线程）与写（接收器）
 * 可能并发，故使用并发集合并用 `add` 的返回值判断是否新增。
 */
final class ProtectList {

    private static final Set<String> PROTECTED =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private ProtectList() { }

    /**
     * 登记一个受保护的应用。
     *
     * @return true 表示本次新加入
     */
    static boolean register(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        return PROTECTED.add(pkg);
    }

    /** 包名是否受保护。 */
    static boolean contains(String pkg) {
        return pkg != null && PROTECTED.contains(pkg);
    }

    /** 当前受保护的应用数量。 */
    static int size() {
        return PROTECTED.size();
    }
}
