package de.robv.android.xposed;

import java.lang.reflect.Member;

/**
 * Xposed API stub —— 仅供**编译期**使用。
 *
 * ★ 关键：`findAndHookMethod` 的返回类型是 `Unhook`（不是 void），
 *   若 stub 写成 void，编译能过但运行时会抛 NoSuchMethodError
 *   （方法描述符不匹配）—— 这个坑已踩过。
 */
public class XC_MethodHook {

    /** 挂钩句柄（用于取消挂钩）。 */
    public static abstract class Unhook {
        public abstract void unhook();
    }

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        public Object getResult() { return null; }
        public void setResult(Object r) { }
        public Throwable getThrowable() { return null; }
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable { }
    protected void afterHookedMethod(MethodHookParam param) throws Throwable { }
}
