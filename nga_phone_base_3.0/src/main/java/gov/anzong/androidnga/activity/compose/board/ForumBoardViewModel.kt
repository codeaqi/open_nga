package gov.anzong.androidnga.activity.compose.board

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gov.anzong.androidnga.arouter.ARouterConstants
import gov.anzong.androidnga.base.util.PreferenceUtils
import gov.anzong.androidnga.base.util.ToastUtils
import gov.anzong.androidnga.core.board.data.BoardEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import sp.phone.param.ParamKey
import sp.phone.util.ARouterUtils
import java.util.concurrent.TimeUnit

object ForumBoardViewModel : ViewModel() {

    val boardLiveData: MutableLiveData<List<BoardEntity>> = MutableLiveData()

    val bookmarkSizeLiveData: MutableLiveData<Int> = MutableLiveData(0)

    private val forumBoardModel = ForumBoardModel()

    const val BOARD_REMOTE_REQUEST_TIME_KEY = "board_remote_request_time"

    init {
        boardLiveData.postValue(forumBoardModel.loadBoardData())
        bookmarkSizeLiveData.postValue(forumBoardModel.bookmarkBoard.children?.size)
    }

    fun getBoardData(index: Int = 0): BoardEntity {
        return boardLiveData.value!![index]
    }

    /** 收藏板块，始终存在 */
    val bookmarkBoard: BoardEntity
        get() = forumBoardModel.bookmarkBoard

    /** 第一个真实版面分类（当前数据下即「网事杂谈」），板块数据为空时为 null */
    val forumBoard: BoardEntity?
        get() = boardLiveData.value?.firstOrNull {
            it.type != BoardEntity.BoardType.BOOKMARK
        }

    /** 被隐藏的版面 id，变化时驱动各个九宫格刷新 */
    val hiddenIdsLiveData: MutableLiveData<Set<String>> =
        MutableLiveData(BoardPrefRepository.loadHiddenIds())

    /** 自定义排序发生变化的信号，值本身无意义，只用来触发重组 */
    val orderVersionLiveData: MutableLiveData<Int> = MutableLiveData(0)

    fun hideBoard(board: BoardEntity) {
        hiddenIdsLiveData.value = BoardPrefRepository.hide(board.id)
    }

    fun showBoard(boardId: String) {
        hiddenIdsLiveData.value = BoardPrefRepository.show(boardId)
    }

    /**
     * 所有被隐藏的版面实体。隐藏夹要展示名字、图标，所以得从版面树里找回来。
     * 找不到的（版面已从数据中移除）直接跳过，避免隐藏夹里出现空条目。
     */
    fun hiddenBoards(): List<BoardEntity> {
        val ids = hiddenIdsLiveData.value ?: emptySet()
        if (ids.isEmpty()) {
            return emptyList()
        }
        val result = mutableListOf<BoardEntity>()
        collectBoards(boardLiveData.value ?: emptyList(), result)
        return result.filter { ids.contains(it.id) }.distinctBy { it.id }
    }

    private fun collectBoards(source: List<BoardEntity>, out: MutableList<BoardEntity>) {
        source.forEach { board ->
            // 分组本身不是可隐藏的版面，只收集叶子节点
            if (board.type == BoardEntity.BoardType.BOARD ||
                board.type == BoardEntity.BoardType.ASSEMBLE
            ) {
                out.add(board)
            }
            board.children?.let { collectBoards(it, out) }
        }
    }

    /**
     * 把 [group] 下的版面按当前可见顺序整体保存，并广播一次变更。
     * 传进来的是拖拽后的完整可见列表，隐藏的版面不在其中——保存时会把它们
     * 追加在末尾，恢复显示后不至于丢掉相对位置。
     */
    fun saveBoardOrder(group: BoardEntity, visibleBoards: List<BoardEntity>) {
        val visibleIds = visibleBoards.map { it.id }
        val restIds = group.children.orEmpty()
            .map { it.id }
            .filter { !visibleIds.contains(it) }
        BoardPrefRepository.saveOrder(group.id, visibleIds + restIds)
        orderVersionLiveData.value = (orderVersionLiveData.value ?: 0) + 1
    }

    /**
     * 取某个分组下应当显示的版面：先套用自定义顺序，再滤掉隐藏的。
     * 自定义顺序里没记录的版面（后来新增的）排在最后，保证新版面不会因为
     * 旧顺序而消失。
     */
    fun visibleChildren(group: BoardEntity): List<BoardEntity> {
        val children = group.children ?: return emptyList()
        val hidden = hiddenIdsLiveData.value ?: emptySet()
        val order = BoardPrefRepository.loadOrder(group.id)
        val ordered = if (order.isEmpty()) {
            children
        } else {
            val indexOf = order.withIndex().associate { (i, id) -> id to i }
            children.sortedBy { indexOf[it.id] ?: Int.MAX_VALUE }
        }
        return ordered.filter { !hidden.contains(it.id) }
    }

    fun addBookmarkBoard(name: String, fid: Int, stid: Int, head: String? = null) {
        bookmarkSizeLiveData.value = forumBoardModel.addBookmarkBoard(name, fid, stid,head)
        // 之前被隐藏过的版面重新收藏时要取消隐藏，否则加了却看不见
        forumBoardModel.findBoard(fid, stid)?.let {
            if (BoardPrefRepository.isHidden(it.id)) {
                showBoard(it.id)
            }
        }
    }

    fun isBookmarkBoard(fid: Int, stid: Int): Boolean {
        return forumBoardModel.isBookmarkBoard(fid, stid)
    }

    fun getBoardName(fid: Int, stid: Int): String {
        return forumBoardModel.getBoardName(fid, stid)
    }

    fun findBoard(fid: Int, stid: Int = 0): BoardEntity? {
        return forumBoardModel.findBoard(fid, stid)
    }

    fun addBookmarkBoard(name: String, fid: String, stid: String) {
        try {
            if (name.isEmpty()) {
                addBookmarkBoard(name, fid.toInt(), stid.toInt())
            }
        } catch (e: NumberFormatException) {
            ToastUtils.show("请输入正确的版面名称、ID或者合集ID")
        }
    }

    fun removeBookmarkBoard(fid: Int, stid: Int) {
        bookmarkSizeLiveData.value = forumBoardModel.removeBookmarkBoard(fid, stid)
    }

    fun removeAllBookmarkBoard() {
        forumBoardModel.removeAllBookmarkBoard()?.let {
            bookmarkSizeLiveData.value = it
        }
    }

    fun showTopicList(board: BoardEntity) {
        val fid = board.fid
        val stid = board.stid
        ARouterUtils.build(ARouterConstants.ACTIVITY_TOPIC_LIST)
            .withInt(ParamKey.KEY_FID, fid)
            .withInt(ParamKey.KEY_STID, stid)
            .withString(ParamKey.BOARD_HEAD, board.head)
            .withString(ParamKey.KEY_TITLE, board.name)
            .navigation()
        // todo 先临时放在这里，后面再统一定时处理
        requestRemoteBoardList()
    }

    private fun requestRemoteBoardList() {
        val long = PreferenceUtils.getData(BOARD_REMOTE_REQUEST_TIME_KEY, 0L)

        if (System.currentTimeMillis() - long < TimeUnit.DAYS.toMillis(1)) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val job = async {
                return@async forumBoardModel.loadIncrementalBoardList()
            }
            val result = job.await()
            if (result.isNotEmpty()) {
                forumBoardModel.mergeBoardList(result)
            }
            PreferenceUtils.putData(BOARD_REMOTE_REQUEST_TIME_KEY, System.currentTimeMillis())
        }
    }
}