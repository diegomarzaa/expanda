package dev.diego.expanda.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class ClipboardRepository(
    private val database: ExpandaDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutableEntries = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    val entries: StateFlow<List<ClipboardEntry>> = mutableEntries.asStateFlow()

    suspend fun refresh() = withContext(io) { mutableEntries.value = database.readClipboardHistory() }
    suspend fun add(text: String) = withContext(io) {
        database.addClipboardText(text)
        mutableEntries.value = database.readClipboardHistory()
    }
    suspend fun delete(id: Long) = withContext(io) {
        database.deleteClipboardEntry(id)
        mutableEntries.value = database.readClipboardHistory()
    }
    suspend fun clear() = withContext(io) {
        database.clearClipboardHistory()
        mutableEntries.value = database.readClipboardHistory()
    }
    suspend fun setPinned(id: Long, pinned: Boolean) = withContext(io) {
        database.setClipboardPinned(id, pinned)
        mutableEntries.value = database.readClipboardHistory()
    }
}
