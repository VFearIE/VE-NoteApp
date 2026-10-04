package com.noteVE.domain

/**
 * 块序列的纯函数操作。
 *
 * ## 为什么单独抽出
 * 编辑器的「插入 / 拆分 / 删除」是**核心业务逻辑**（矢量排布的基础），
 * 但原先内联在 `NoteEditViewModel`（AndroidViewModel）里，
 * 依赖 Android 框架，无法用纯 JVM 单元测试覆盖。
 *
 * 这里把它们提取为**无副作用、无 Android 依赖**的纯函数：
 * - 相同输入必得相同输出，便于测试与推理
 * - ViewModel 只负责调用与状态承载，不再包含算法
 *
 * 行为与原实现**逐字一致**，仅为可测性做结构化拆分。
 */
object BlockOps {

    /**
     * 在指定索引处插入一个块（无光标信息时的兜底路径：插入到最下方）。
     *
     * ★ 不变式：序列末尾**始终存在一个文字块**。
     *   否则附件垫底后，下方空白不属于任何编辑框，点击无响应、无光标。
     */
    fun insertAt(blocks: List<Block>, index: Int, block: Block): List<Block> {
        val list = blocks.toMutableList()
        val i = index.coerceIn(0, list.size)
        list.add(i, block)
        if (list.lastOrNull() !is Block.Text) list.add(Block.Text(""))
        return list
    }

    /**
     * 矢量排布的核心：在文字块 [index] 的光标处插入块。
     *
     * 把该文字块按「光标前 / 光标后」拆成两个文字块，新块插入其间 ——
     * 附件因此成为文档流中的行内原子节点，位置来自当前光标而非固定置顶/置底。
     *
     * @param beforeHtml 光标之前的 HTML
     * @param afterHtml  光标之后的 HTML
     * @return 新的块序列；若 [index] 不是文字块（或越界）则原样返回
     */
    fun splitAndInsert(
        blocks: List<Block>,
        index: Int,
        beforeHtml: String,
        block: Block,
        afterHtml: String,
    ): List<Block> {
        if (index !in blocks.indices || blocks[index] !is Block.Text) return blocks
        val list = blocks.toMutableList()
        list[index] = Block.Text(beforeHtml)
        list.add(index + 1, block)
        list.add(index + 2, Block.Text(afterHtml))
        return list
    }

    /**
     * 删除指定索引的块。
     *
     * ★ 不变式：删除后若序列为空，补一个空文字块（保证始终可输入）。
     *
     * @return 新的块序列
     */
    fun removeAt(blocks: List<Block>, index: Int): List<Block> {
        val list = blocks.toMutableList()
        if (index in list.indices) list.removeAt(index)
        if (list.isEmpty()) list.add(Block.Text(""))
        return list
    }

    /**
     * 确保末尾存在文字块（用于从磁盘载入后的规范化）。
     */
    fun ensureTrailingText(blocks: List<Block>): List<Block> {
        if (blocks.isEmpty()) return listOf(Block.Text(""))
        return if (blocks.last() is Block.Text) blocks else blocks + Block.Text("")
    }
}
