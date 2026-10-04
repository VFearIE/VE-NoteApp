package de.robv.android.xposed;

/**
 * Xposed API stub —— 仅供编译期使用（运行时由框架提供）。
 *
 * ★ 关键：findAndHookMethod 的返回类型必须是 Unhook（不是 void），
 *   否则编译通过但运行时抛 NoSuchMethodError（方法描述符不匹配）。
 */
public class XposedHelpers {

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        return null;
    }

    public static Class<?> findClass(String className, ClassLoader classLoader, boolean initialize) {
        return null;
    }

    public static Object getObjectField(Object obj, String name) { return null; }

    public static void setObjectField(Object obj, String name, Object value) { }

    public static Object getStaticObjectField(Class<?> clazz, String name) { return null; }

    public static Object callMethod(Object obj, String methodName, Object... args) { return null; }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        return null;
    }

    public static Object callStaticMethod(String className, ClassLoader cl,
                                          String methodName, Object... args) {
        return null;
    }

    public static void callStaticMethodReturnVoid(Class<?> clazz, String methodName, Object... args) { }

    public static XC_MethodHook.Unhook findAndHookMethod(
            Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        return null;
    }

    public static XC_MethodHook.Unhook findAndHookMethod(
            String className, ClassLoader cl, String methodName,
            Object... parameterTypesAndCallback) {
        return null;
    }
}
