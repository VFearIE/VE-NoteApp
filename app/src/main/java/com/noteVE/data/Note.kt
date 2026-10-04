package com.noteVE.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 笔记实体。富文本正文以 HTML 字符串存储；图片以相对文件名列表存储（禁止 BLOB）。
 */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",          // 块 JSON（矢量排布：文字/图片/录音块序列）
    val plainText: String = "",     // 去标签纯文本，供 FTS 检索与列表预览
    val createdAt: Long = 0L,       // 时间戳（毫秒）
    val modifiedAt: Long = 0L,
    val lastOpenedAt: Long = 0L,    // 最后一次打开时间
    val archived: Boolean = false,
    val pinned: Boolean = false,
    /**
     * **保留字段 / 未实现**。
     *
     * 本项目**没有**任何加密实现 —— 此列不属于任何业务逻辑，
     * UI 也不得据此宣称「已加密」，以免造成「看起来有加密、实际没有」的伪安全印象。
     * 保留仅为数据库 schema 向后兼容（旧库已有此列，删除会触发额外迁移）。
     *
     * 若将来真正实现端到端加密，必须同时提供：密钥管理 / 密钥生命周期 /
     * 导入导出适配 / 异常恢复 / 迁移 / 测试 —— 不做半吊子实现。
     */
    val encrypted: Boolean = false,
    val reminderAt: Long? = null,   // 提醒时间戳（毫秒），null 表示无提醒
    val repeatRule: String? = null, // NONE / DAILY / WEEKLY / MONTHLY / YEARLY
    val imagePaths: List<String> = emptyList(), // 派生：块中的图片路径
    val voiceRecords: List<String> = emptyList(), // 派生：块中的录音路径
    val filePaths: List<String> = emptyList(), // 派生：块中的任意文件路径
    // v2.1.0：编辑页已移除标签入口（该功能只可设不可筛，属死功能）。
    // 字段**保留**仅为向后兼容：旧笔记数据与旧导出包可能含此内容，
    // 直接删除会导致导入时信息丢失。新代码不应再写入非空值。
    val labels: List<String> = emptyList(),
    val color: Int? = null                       // 强调色（ARGB）
)
