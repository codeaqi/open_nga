package gov.anzong.androidnga.activity.compose.note.data

import gov.anzong.androidnga.common.base.JavaBean

/**
 * 一条「我的思考」。
 * 无参构造 + var 字段是 fastjson 反序列化的要求，不要改成 data class。
 */
class NoteEntity : JavaBean {

    /** 用创建时间戳作为 id，够用且天然不重复 */
    var id: Long = 0

    var content: String = ""

    var createTime: Long = 0
}
