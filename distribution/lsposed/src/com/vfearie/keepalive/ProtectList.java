package com.vfearie.keepalive;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 保护名单。
 *
 * ## 为什么不用"app 写文件、system_server 读"的方案
 *
 * 初版设想：被勾选的 app 各自把包名写进共享文件，system_server 读取判定。
 * **实测不可行**：`/data/local/tmp` 权限为 `shell:shell 771`，
 * 普通应用（uid ≥ 10000）对其**无写权限**，文件根本创建不出来。
 *
 * ## 实际采用的方案
 *
 * 两级来源，任一命中即保护：
 *
 *  ① **内置名单**（{@link #BUILT_IN}）—— 编译期确定，开箱即用
 *  ② **外部名单文件**（可选）—— 若存在则额外读取，便于动态增补
 *     路径：`/data/local/tmp/xposed_keepalive_list`（需 root 创建/维护）
 *
 * 之所以把 root 创建的文件路径放在 `/data/local/tmp`：
 * 该目录对 `shell`(2000) 与 `root`(0) 可写，system_server 可读，
 * 是 root 场景下最合适的交换位置。
 *
 * ## 通用性说明
 *
 * 本模块的定位是**通用保活**：想保护哪些 app，改 {@link #BUILT_IN} 重新编译即可，
 * 或用 root 往外部名单文件里追加包名（每行一个）。
 * **作用域仍需勾选「系统框架」(system)**，否则 Hook 不装载。
 */
public final class ProtectList {

    /**
     * 内置保护名单（编译期）。
     *
     * ★ 增删要保护的 app，改这里并重新构建即可。
     */
    private static final String[] BUILT_IN = {
        "com.noteVE",
    };

    /** 可选的外部名单文件（root 可创建/维护，每行一个包名）。 */
    private static final String EXTRA_FILE = "/data/local/tmp/xposed_keepalive_list";

    /** 缓存（system_server 生命周期内只读一次文件即可）。 */
    private static volatile Set<String> cache = null;

    private ProtectList() { }

    /** 包名是否受保护。 */
    public static boolean contains(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        if (cache == null) cache = build();
        return cache.contains(pkg);
    }

    /** 重新加载（外部名单变更后，由调用方触发）。 */
    public static void invalidate() {
        cache = null;
    }

    // ---------------- 内部 ----------------

    private static Set<String> build() {
        Set<String> set = new HashSet<>(Arrays.asList(BUILT_IN));
        set.addAll(readExtra());
        return set;
    }

    /** 读外部名单；文件不存在或不可读时返回空集（保守：不额外保护任何东西）。 */
    private static Set<String> readExtra() {
        Set<String> out = new HashSet<>();
        File f = new File(EXTRA_FILE);
        if (!f.exists() || !f.canRead()) return out;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) out.add(line);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }
}
