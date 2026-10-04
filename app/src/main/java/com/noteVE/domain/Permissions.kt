package com.noteVE.domain

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * 权限总控。
 *
 * 三层策略：
 *  1. priv-app → 反射 grantRuntimePermission 静默授全部运行时权限（首选，正常情况全部自动授予）
 *  2. 特殊权限（精确闹钟/电池优化/通知使用权/无障碍）→ 无法静默授予，需跳系统页面，但可**检测**状态
 *  3. 非 priv-app（二次分发）→ 退回标准运行时申请
 *
 * 本对象同时为「设置 → 授权相关」三级页面提供：状态检测 + 申请入口。
 */
object Permissions {

    // ---------------- priv-app 判定 & 静默自授 ----------------

    /**
     * 是否为系统特权应用。
     *
     * ★ 不能只靠 `sourceDir` 判断：对「被更新过的系统应用」（UPDATED_SYSTEM_APP），
     *   `ApplicationInfo.sourceDir` 会指向 `/data/app/...` 的更新副本，
     *   而 `dumpsys package` 的 codePath 仍显示 `/system/priv-app/...` —— 两者不一致，
     *   这正是「系统应用探测不生效」的原因。
     *
     * 正确做法：优先看系统标志位（FLAG_SYSTEM / PRIVATE_FLAG_PRIVILEGED），
     * 再回退到路径判断。
     */
    /**
     * 是否为系统特权应用（权限页「系统应用」开关的判据）。
     *
     * ★ 不能只靠 `sourceDir` 判断：对「被更新过的系统应用」（UPDATED_SYSTEM_APP），
     *   `ApplicationInfo.sourceDir` 会指向 `/data/app/...` 的更新副本，
     *   而 `dumpsys package` 的 codePath 仍是 `/system/priv-app/...` —— 两者不一致，
     *   这正是「系统应用探测不生效」的根因。
     *
     * 判据优先级（任一成立即为系统特权应用）：
     *   ① `privateFlags & PRIVATE_FLAG_PRIVILEGED` —— framework 判定特权的权威标志（反射）
     *   ② `flags & FLAG_SYSTEM`                 —— 公开字段，无需反射，最稳
     *   ③ sourceDir / publicSourceDir 路径前缀  —— 兜底
     */
    fun isPrivApp(context: Context): Boolean = try {
        val ai = context.packageManager.getApplicationInfo(context.packageName, 0)

        // ② FLAG_SYSTEM：公开常量，最可靠
        val isSystem = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0

        // ① PRIVATE_FLAG_PRIVILEGED：特权应用的权威标志（反射，失败不影响结果）
        val privileged = runCatching {
            val f = android.content.pm.ApplicationInfo::class.java.getField("privateFlags")
            (f.getInt(ai) and (1 shl 3)) != 0     // PRIVATE_FLAG_PRIVILEGED = 1 << 3
        }.getOrDefault(false)

        // ③ 路径兜底
        val pathHit = listOfNotNull(ai.sourceDir, ai.publicSourceDir).any {
            it.startsWith("/system/priv-app/") ||
                it.startsWith("/product/priv-app/") ||
                it.startsWith("/system_ext/priv-app/") ||
                it.startsWith("/system/app/")
        }

        isSystem || privileged || pathHit
    } catch (e: Exception) { false }

    /** 需要运行时授权的危险权限（按 SDK 区分）。 */
    fun requiredRuntimePermissions(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.VIBRATE
        )
        if (Build.VERSION.SDK_INT >= 33) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
            list.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return list.toTypedArray()
    }

    fun missing(context: Context): Array<String> =
        requiredRuntimePermissions().filter {
            context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

    /** priv-app 静默授予全部运行时权限（反射隐藏 API，需 GRANT_RUNTIME_PERMISSIONS 特权）。 */
    fun selfGrant(context: Context) {
        if (!isPrivApp(context)) return
        val pm = context.packageManager
        val user = Process.myUserHandle()
        val method = runCatching {
            pm.javaClass.getMethod(
                "grantRuntimePermission",
                String::class.java, String::class.java, android.os.UserHandle::class.java
            )
        }.getOrNull() ?: return
        for (p in requiredRuntimePermissions()) {
            if (context.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                runCatching { method.invoke(pm, context.packageName, p, user) }
            }
        }
    }

    // ---------------- 单项状态检测 ----------------

    fun runtimeGranted(context: Context, perm: String): Boolean =
        context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    /** 通知栏权限（POST_NOTIFICATIONS 或 SDK<33 时视为已授）。 */
    fun notificationsEnabled(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) runtimeGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        else NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** 精确闹钟：SDK<31 视为默认拥有。 */
    fun exactAlarmGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager)
                ?.canScheduleExactAlarms() ?: false
        } else true

    /** 电池优化：已豁免=true。 */
    fun batteryOptimizationIgnored(context: Context): Boolean = try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    } catch (e: Exception) { false }

    /** 通知使用权：本应用是否已获 NotificationListener 绑定。 */
    fun notificationListenerEnabled(context: Context): Boolean = try {
        val flat = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        flat.split(":").any { it.contains(context.packageName) }
    } catch (e: Exception) { false }

    /** 无障碍：本应用的 AccessibilityService 是否已启用。 */
    fun accessibilityEnabled(context: Context): Boolean = try {
        val flat = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        flat.split(":").any { it.contains(context.packageName) }
    } catch (e: Exception) { false }

    // ---------------- 申请入口 ----------------

    /** 电池优化豁免（弹系统对话框）。 */
    fun requestIgnoreBatteryOptimization(activity: Activity) {
        runCatching {
            @Suppress("BatteryLife")
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${activity.packageName}"))
            activity.startActivity(i)
        }
    }

    /** 精确闹钟授权页（SDK>=31）。 */
    fun requestExactAlarm(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching {
                activity.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(Uri.parse("package:${activity.packageName}"))
                )
            }
        }
    }

    /** 通知使用权设置页。 */
    fun openNotificationListenerSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** 无障碍设置页。 */
    fun openAccessibilitySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** 应用详情页（兜底）。 */
    fun openAppSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
