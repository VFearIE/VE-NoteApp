package com.noteVE.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 提醒重复规则单元测试。
 *
 * 覆盖：一次性、每日、每周、每月、每年、跨月、跨年、闰年边界、无效规则。
 * 用固定时间戳构造，避免依赖当前时间。
 */
class RepeatRuleTest {

    private fun ms(y: Int, mo: Int, d: Int, h: Int = 9, mi: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(y, mo - 1, d, h, mi, 0)
        }.timeInMillis

    private fun parts(t: Long): IntArray =
        Calendar.getInstance().apply { timeInMillis = t }.let {
            intArrayOf(
                it.get(Calendar.YEAR),
                it.get(Calendar.MONTH) + 1,
                it.get(Calendar.DAY_OF_MONTH),
                it.get(Calendar.HOUR_OF_DAY),
                it.get(Calendar.MINUTE)
            )
        }

    // ── 不重复 ─────────────────────────────────────────────────

    @Test
    fun `NONE 返回 null`() {
        assertNull(RepeatRule.next(ms(2026, 3, 15), RepeatRule.NONE))
    }

    @Test
    fun `null 与空串返回 null`() {
        assertNull(RepeatRule.next(ms(2026, 3, 15), null))
        assertNull(RepeatRule.next(ms(2026, 3, 15), ""))
    }

    @Test
    fun `未知规则返回 null`() {
        assertNull(RepeatRule.next(ms(2026, 3, 15), "HOURLY"))
    }

    // ── 各周期 ─────────────────────────────────────────────────

    @Test
    fun `每日重复`() {
        val next = RepeatRule.next(ms(2026, 3, 15, 9, 30), RepeatRule.DAILY)!!
        assertEquals(listOf(2026, 3, 16, 9, 30), parts(next).toList())
    }

    @Test
    fun `每周重复`() {
        val next = RepeatRule.next(ms(2026, 3, 15, 9, 30), RepeatRule.WEEKLY)!!
        assertEquals(listOf(2026, 3, 22, 9, 30), parts(next).toList())
    }

    @Test
    fun `每月重复`() {
        val next = RepeatRule.next(ms(2026, 3, 15, 9, 30), RepeatRule.MONTHLY)!!
        assertEquals(listOf(2026, 4, 15, 9, 30), parts(next).toList())
    }

    @Test
    fun `每年重复`() {
        val next = RepeatRule.next(ms(2026, 3, 15, 9, 30), RepeatRule.YEARLY)!!
        assertEquals(listOf(2027, 3, 15, 9, 30), parts(next).toList())
    }

    // ── 边界 ───────────────────────────────────────────────────

    @Test
    fun `跨月边界 - 1月31日加一天`() {
        val next = RepeatRule.next(ms(2026, 1, 31), RepeatRule.DAILY)!!
        assertEquals(listOf(2026, 2, 1, 9, 0), parts(next).toList())
    }

    @Test
    fun `跨年边界 - 12月31日加一天`() {
        val next = RepeatRule.next(ms(2026, 12, 31), RepeatRule.DAILY)!!
        assertEquals(listOf(2027, 1, 1, 9, 0), parts(next).toList())
    }

    @Test
    fun `每月重复在短月自动顺延（1月31日 → 2月）`() {
        val next = RepeatRule.next(ms(2026, 1, 31), RepeatRule.MONTHLY)!!
        val p = parts(next)
        // Calendar 语义：1月31日 + 1月 = 2月末（非闰年 2 月 28 日）
        assertEquals(2026, p[0])
        assertEquals(2, p[1])
        assertTrue("应落在 2 月末而不是溢出到 3 月", p[2] <= 29)
    }

    @Test
    fun `闰年 2月29日加一年`() {
        // 2028 是闰年
        val next = RepeatRule.next(ms(2028, 2, 29), RepeatRule.YEARLY)!!
        val p = parts(next)
        assertEquals(2029, p[0])
        // 2029 非闰年 → 顺延到 2 月末或 3 月 1 日（Calendar 语义）
        assertTrue("不应崩坏", p[1] in 2..3)
    }

    @Test
    fun `结果严格大于输入时间`() {
        val base = ms(2026, 6, 10, 12, 0)
        for (rule in listOf(RepeatRule.DAILY, RepeatRule.WEEKLY, RepeatRule.MONTHLY, RepeatRule.YEARLY)) {
            val next = RepeatRule.next(base, rule)!!
            assertTrue("$rule 的下一次应晚于当前", next > base)
        }
    }

    // ── 有效性 ─────────────────────────────────────────────────

    @Test
    fun `isValid 判定`() {
        assertTrue(RepeatRule.isValid(RepeatRule.NONE))
        assertTrue(RepeatRule.isValid(RepeatRule.DAILY))
        assertTrue(RepeatRule.isValid(RepeatRule.WEEKLY))
        assertTrue(RepeatRule.isValid(RepeatRule.MONTHLY))
        assertTrue(RepeatRule.isValid(RepeatRule.YEARLY))
        assertTrue(!RepeatRule.isValid(null))
        assertTrue(!RepeatRule.isValid("BOGUS"))
    }

    @Test
    fun `ALL 包含全部规则且顺序稳定`() {
        assertEquals(
            listOf("NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY"),
            RepeatRule.ALL
        )
    }
}
