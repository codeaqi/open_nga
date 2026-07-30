package gov.anzong.androidnga.activity.compose.note

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import gov.anzong.androidnga.activity.compose.note.data.NoteEntity
import gov.anzong.androidnga.activity.compose.note.data.NoteRepository

class NoteViewModel : ViewModel() {

    val noteLiveData: MutableLiveData<List<NoteEntity>> = MutableLiveData(emptyList())

    init {
        noteLiveData.value = NoteRepository.loadNotes()
    }

    fun addNote(content: String) {
        noteLiveData.value = NoteRepository.addNote(content, System.currentTimeMillis())
    }

    fun updateNote(id: Long, content: String) {
        noteLiveData.value = NoteRepository.updateNote(id, content)
    }

    fun removeNote(id: Long) {
        noteLiveData.value = NoteRepository.removeNote(id)
    }
}
