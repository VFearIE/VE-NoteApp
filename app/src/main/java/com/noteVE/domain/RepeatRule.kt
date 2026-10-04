package com.noteVE.domain

import java.util.Calendar

/**
 * 提醒重复规则（领域模型）。
 *
 * 从 [com.noteVE.reminder.ReminderScheduler] 提取而来：重复规则是**业务概念**，
 * 应属于 domain 层。这样 UI 层构造重复选项时无需依赖 reminder 子系统。
 *
 * 存储形式：Note.repeatRule 字段存下述常量字符串。
 */
object RepeatRule {

    const val NONE = "NONE"
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"
    const val YEARLY = "YEARLY"

    /** 全部可选规则（UI 生成选项用）。 */
    val ALL = listOf(NONE, DAILY, WEEKLY, MONTHLY, YEARLY)

    /**
     * 计算下一次重复时间。
     * @return null 表示不重复或规则无效
     */
    fun next(triggerAt: Long, rule: String?): Long? {
        if (rule.isNullOrBlank() || rule == NONE) return null
        val cal = Calendar.getInstance().apply { timeInMillis = triggerAt }
        when (rule) {
            DAILY -> cal.add(Calendar.DAY_OF_MONTH, 1)
            WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            MONTHLY -> cal.add(Calendar.MONTH, 1)
            YEARLY -> cal.add(Calendar.YEAR, 1)
            else -> return null
        }
        return cal.timeInMillis
    }

    /** 规则是否有效。 */
    fun isValid(rule: String?): Boolean = rule != null && rule in ALL
}
