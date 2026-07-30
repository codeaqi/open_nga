package gov.anzong.androidnga.activity.compose.note.data

import com.alibaba.fastjson.JSON
import gov.anzong.androidnga.base.util.ContextUtils
import gov.anzong.androidnga.base.utils.Files
import gov.anzong.androidnga.common.util.LogUtils
import java.io.File

/**
 * 「我的思考」的本地存储。
 *
 * 笔记是自由文本，可能含逗号和换行，所以不能用自选股那种分隔符拼接的紧凑格式，
 * 这里用 JSON。数据也可能变多，放 filesDir 而不是 SharedPreferences。
 */
object NoteRepository {

    private const val TAG = "NoteRepository"

    private const val NOTE_FILE_NAME = "my_notes.json"

    private fun noteFile(): File = File(ContextUtils.getContext().filesDir, NOTE_FILE_NAME)

    /** 按创建时间倒序返回，最新的在最上面 */
    fun loadNotes(): List<NoteEntity> {
        val file = noteFile()
        if (!file.exists()) {
            return emptyList()
        }
        return try {
            val json = Files.readFile(file)
            if (json.isNullOrEmpty()) {
                emptyList()
            } else {
                JSON.parseArray(json, NoteEntity::class.java)
                    .orEmpty()
                    .sortedByDescending { it.createTime }
            }
        } catch (e: Exception) {
            // 文件损坏时返回空列表，不让首页崩掉
            LogUtils.e(TAG, "loadNotes failed: ${e.message}")
            emptyList()
        }
    }

    private fun saveNotes(notes: List<NoteEntity>) {
        try {
            Files.writeFile(noteFile(), JSON.toJSONString(notes))
        } catch (e: Exception) {
            LogUtils.e(TAG, "saveNotes failed: ${e.message}")
        }
    }

    fun addNote(content: String, createTime: Long): List<NoteEntity> {
        val text = content.trim()
        if (text.isEmpty()) {
            return loadNotes()
        }
        val notes = loadNotes().toMutableList()
        notes.add(NoteEntity().apply {
            this.id = createTime
            this.content = text
            this.createTime = createTime
        })
        val sorted = notes.sortedByDescending { it.createTime }
        saveNotes(sorted)
        return sorted
    }

    fun updateNote(id: Long, content: String): List<NoteEntity> {
        val text = content.trim()
        if (text.isEmpty()) {
            return loadNotes()
        }
        val notes = loadNotes().toMutableList()
        notes.firstOrNull { it.id == id }?.content = text
        saveNotes(notes)
        return notes
    }

    fun removeNote(id: Long): List<NoteEntity> {
        val notes = loadNotes().toMutableList()
        notes.removeAll { it.id == id }
        saveNotes(notes)
        return notes
    }
}
