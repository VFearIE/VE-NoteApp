package com.noteVE.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noteVE.data.Note
import com.noteVE.domain.NoteRepository
import com.noteVE.domain.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class NoteListViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = NoteRepository(app)
    private val query = MutableStateFlow("")
    private val searching = MutableStateFlow(false)
    val notes: StateFlow<List<Note>> =
        combine(query, Settings.sortBy, Settings.sortReverse) { q, _, _ -> q }
            .flatMapLatest { q -> if (q.isNotBlank()) repo.search(q) else repo.observeAll() }
            .map { list -> sortNotes(list, Settings.sortBy.value, Settings.sortReverse.value) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchQuery: StateFlow<String> = query
    val searchVisible: StateFlow<Boolean> = searching

    fun setQuery(q: String) { query.value = q }

    /** 点击放大镜：隐藏则显示；显示则清空文字与过滤规则并隐藏。 */
    fun toggleSearch() {
        if (searching.value) { query.value = ""; searching.value = false }
        else searching.value = true
    }

    fun setSort(by: String, reverse: Boolean) { Settings.setSort(by, reverse) }

    fun delete(note: Note) = viewModelScope.launch { repo.delete(note) }
    fun togglePin(note: Note) = viewModelScope.launch { repo.update(note.copy(pinned = !note.pinned)) }
    fun updateReminder(id: Long, at: Long?, rule: String?) = viewModelScope.launch { repo.updateReminder(id, at, rule) }

    private fun sortNotes(list: List<Note>, by: String, reverse: Boolean): List<Note> {
        val cmp: Comparator<Note> = when (by) {
            Settings.SORT_NAME -> compareBy { it.title.lowercase() }
            else -> compareByDescending { it.lastOpenedAt }
        }
        val effective = if (reverse) cmp.reversed() else cmp
        return list.sortedWith(compareByDescending<Note> { it.pinned }.then(effective))
    }
}
