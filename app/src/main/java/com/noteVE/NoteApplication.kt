package com.noteVE

import android.app.Application
import com.noteVE.domain.Permissions
import com.noteVE.domain.Settings
import com.noteVE.reminder.NotificationHelper
import com.noteVE.reminder.ReminderTicker

/**
 * 应用入口。
 *
 * 保活策略（精简后只剩两条，均为应用自身机制，**不依赖 Magisk 模块**）：
 *
 *  ① `android:persistent="true"`（Manifest）
 *     系统应用设为 persistent，由 system_server 托管，进程常驻、不被内存杀手回收。
 *
 *  ② [ReminderTicker] 进程内计时器
 *     「最近任务划卡」会让包进入 stopped 状态，**系统不再投递 AlarmManager**；
 *     但 persistent 保证了进程活着，故由进程内协程直接计时触发，绕开该限制。
 *
 * 分发方式：作为系统应用固定在 ROM 中（`/system/priv-app/com.noteVE/`）。
 */
class NoteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        NotificationHelper.createChannels(this)

        // priv-app 静默授予全部运行时权限（非 priv-app 时由 MainActivity 走标准申请）
        if (Permissions.isPrivApp(this)) Permissions.selfGrant(this)

        // 进程内提醒计时器
        ReminderTicker.start(this)
    }
}
