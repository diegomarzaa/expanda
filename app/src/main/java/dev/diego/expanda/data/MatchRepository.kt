package dev.diego.expanda.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class MatchRepository(
    private val database: ExpandaDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutableMatches = MutableStateFlow<List<TextMatch>>(emptyList())
    val matches: StateFlow<List<TextMatch>> = mutableMatches.asStateFlow()
    private val mutableReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = mutableReady.asStateFlow()

    suspend fun refresh() = withContext(io) {
        try {
            mutableMatches.value = database.readMatches()
        } finally {
            mutableReady.value = true
        }
    }

    suspend fun save(match: TextMatch): Long = withContext(io) {
        val id = database.upsert(match)
        mutableMatches.value = database.readMatches()
        id
    }

    suspend fun replaceSource(sourceFile: String, matches: List<TextMatch>): Int = withContext(io) {
        val count = database.replaceSourceMatches(sourceFile, matches)
        mutableMatches.value = database.readMatches()
        count
    }

    suspend fun import(matches: List<TextMatch>): Int = withContext(io) {
        val count = database.importMatches(matches)
        mutableMatches.value = database.readMatches()
        count
    }

    suspend fun replace(matches: List<TextMatch>) = withContext(io) {
        database.replaceMatches(matches)
        mutableMatches.value = database.readMatches()
    }

    suspend fun resetStatistics() = withContext(io) {
        database.resetStatistics()
        mutableMatches.value = database.readMatches()
    }

    suspend fun delete(id: Long) = withContext(io) {
        database.delete(id)
        mutableMatches.value = database.readMatches()
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = withContext(io) {
        val match = matches.value.firstOrNull { it.id == id } ?: return@withContext
        database.setEnabled(match, enabled)
        mutableMatches.value = database.readMatches()
    }

    suspend fun recordExpansion(match: TextMatch, packageName: String, collectStatistics: Boolean = true) = withContext(io) {
        database.recordExpansion(
            match.id,
            packageName,
            advanceSequence = match.selectionMode == TemplateSelectionMode.SEQUENTIAL,
            collectStatistics = collectStatistics,
        )
        mutableMatches.value = database.readMatches()
    }

    fun stats(): DashboardStats {
        val all = matches.value
        val expansions = all.sumOf(TextMatch::usageCount)
        val saved = all.sumOf { match ->
            (match.replace.length - match.trigger.length).coerceAtLeast(0).toLong() * match.usageCount
        }
        return DashboardStats(expansions, saved, (saved / 5.0).toLong())
    }
}
