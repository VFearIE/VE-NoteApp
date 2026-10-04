package de.robv.android.xposed.callbacks;

/** Xposed API stub —— 仅供编译期使用（运行时由框架提供）。 */
public class XC_LoadPackage {
    public static class LoadPackageParam {
        public String packageName;
        public String processName;
        public ClassLoader classLoader;
        public boolean isFirstApplication;
    }
}
