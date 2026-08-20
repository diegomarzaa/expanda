package dev.diego.expanda.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SnippetRepository(
    private val database: ExpandaDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutableSnippets = MutableStateFlow<List<Snippet>>(emptyList())
    val snippets: StateFlow<List<Snippet>> = mutableSnippets.asStateFlow()

    suspend fun refresh() = withContext(io) {
        mutableSnippets.value = database.readSnippets()
    }

    suspend fun save(snippet: Snippet): Long = withContext(io) {
        require(snippet.shortcut.isNotBlank()) { "Shortcut cannot be empty" }
        require(snippet.content.isNotEmpty()) { "Content cannot be empty" }
        val id = database.upsert(snippet)
        mutableSnippets.value = database.readSnippets()
        id
    }

    suspend fun delete(id: Long) = withContext(io) {
        database.delete(id)
        mutableSnippets.value = database.readSnippets()
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val snippet = snippets.value.firstOrNull { it.id == id } ?: return
        save(snippet.copy(enabled = enabled))
    }

    suspend fun recordExpansion(snippet: Snippet, packageName: String, collectStatistics: Boolean = true) = withContext(io) {
        database.recordExpansion(snippet.id, packageName, collectStatistics)
        mutableSnippets.value = database.readSnippets()
    }

    fun stats(): DashboardStats {
        val all = snippets.value
        val expansions = all.sumOf(Snippet::usageCount)
        val saved = all.sumOf { snippet ->
            (snippet.content.length - snippet.shortcut.length).coerceAtLeast(0).toLong() * snippet.usageCount
        }
        return DashboardStats(expansions, saved, (saved / 5.0).toLong())
    }
}
