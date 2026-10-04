package com.noteVE.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.noteVE.domain.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 系统事件广播：重建全部未过期提醒。
 *
 * ## 处理的 Action（**必须与 AndroidManifest 声明严格一致**）
 *
 * | Action | 何时触发 | 处理 | 说明 |
 * |---|---|---|---|
 * | `BOOT_COMPLETED` | 开机完成（用户已解锁） | 是 | 主路径，此时可访问数据库 |
 * | `QUICKBOOT_POWERON` | 部分 OEM 的快速启动 | 是 | 语义同 BOOT_COMPLETED |
 * | `MY_PACKAGE_REPLACED` | 本应用被覆盖安装/更新后 | 是 | 更新会清空 AlarmManager 闹钟，需重建 |
 *
 * **不处理** `LOCKED_BOOT_COMPLETED`：该广播在解锁前投递，
 * 而数据库在 credential-protected 存储中不可读，处理也无意义，
 * 故 Manifest 中亦未声明（详见 AndroidManifest 注释）。
 *
 * ## 幂等性
 * `rescheduleAllReminders()` 内部对每条提醒执行「先取消后重排」，
 * 因此重复收到广播（如 QUICKBOOT 与 BOOT 同时到达）不会产生重复闹钟。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) {
            Log.i(TAG, "忽略未处理的 action=$action")
            return
        }
        Log.i(TAG, "收到 $action → 重建提醒")
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NoteRepository(context).rescheduleAllReminders()
            } catch (t: Throwable) {
                Log.w(TAG, "重建提醒失败: $t")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "NoteReminder"

        /**
         * 与 AndroidManifest 的 intent-filter **逐项对应**。
         * 新增/删除 Action 时必须同时修改两处。
         */
        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
