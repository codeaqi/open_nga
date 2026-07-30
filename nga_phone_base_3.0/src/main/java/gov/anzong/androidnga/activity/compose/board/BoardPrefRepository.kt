package gov.anzong.androidnga.activity.compose.board

import gov.anzong.androidnga.base.util.PreferenceUtils

/**
 * 版面的隐藏状态与自定义排序。
 *
 * 只存版面 id，不存版面本身——版面数据会随服务端增量更新变化，存 id 才不会和它冲突。
 * id 与 [ForumBoardModel] 的 generateBoardId 一致（fid 或 "fid_stid"）。
 */
object BoardPrefRepository {

    private const val KEY_HIDDEN_BOARDS = "hidden_board_ids"

    /** 每个分组一份排序，key 拼上分组 id */
    private const val KEY_ORDER_PREFIX = "board_order_"

    private const val SEPARATOR = ","

    fun loadHiddenIds(): Set<String> {
        val saved = PreferenceUtils.getData(KEY_HIDDEN_BOARDS, "")
        if (saved.isNullOrEmpty()) {
            return emptySet()
        }
        return saved.split(SEPARATOR).filter { it.isNotBlank() }.toSet()
    }

    private fun saveHiddenIds(ids: Set<String>) {
        PreferenceUtils.putData(KEY_HIDDEN_BOARDS, ids.joinToString(SEPARATOR))
    }

    fun hide(boardId: String): Set<String> {
        val ids = loadHiddenIds().toMutableSet()
        ids.add(boardId)
        saveHiddenIds(ids)
        return ids
    }

    fun show(boardId: String): Set<String> {
        val ids = loadHiddenIds().toMutableSet()
        ids.remove(boardId)
        saveHiddenIds(ids)
        return ids
    }

    fun isHidden(boardId: String): Boolean = loadHiddenIds().contains(boardId)

    /**
     * 读取某个分组的自定义顺序。返回的是 id 列表，可能包含已不存在的版面，
     * 也可能缺少新增的版面——[applyOrder] 负责兼容这两种情况。
     */
    fun loadOrder(groupId: String): List<String> {
        val saved = PreferenceUtils.getData(KEY_ORDER_PREFIX + groupId, "")
        if (saved.isNullOrEmpty()) {
            return emptyList()
        }
        return saved.split(SEPARATOR).filter { it.isNotBlank() }
    }

    fun saveOrder(groupId: String, boardIds: List<String>) {
        PreferenceUtils.putData(KEY_ORDER_PREFIX + groupId, boardIds.joinToString(SEPARATOR))
    }
}
