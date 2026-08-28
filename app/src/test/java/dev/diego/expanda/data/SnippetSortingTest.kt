package dev.diego.expanda.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SnippetSortingTest {
    private val snippets = listOf(
        match(id = 1, trigger = ";z", label = "", created = 10, updated = 30, uses = 2),
        match(id = 2, trigger = ";b", label = "Alpha", created = 30, updated = 10, uses = 8),
        match(id = 3, trigger = ";a", label = "Beta", created = 20, updated = 20, uses = 4),
    )

    @Test fun `sort modes use stable user-facing fields`() {
        assertEquals(listOf(1L, 3L, 2L), snippets.sortedForDisplay(SnippetSortMode.RECENTLY_EDITED).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), snippets.sortedForDisplay(SnippetSortMode.OLDEST_EDITED).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), snippets.sortedForDisplay(SnippetSortMode.NAME_ASCENDING).map { it.id })
        assertEquals(listOf(1L, 3L, 2L), snippets.sortedForDisplay(SnippetSortMode.NAME_DESCENDING).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), snippets.sortedForDisplay(SnippetSortMode.NEWEST_CREATED).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), snippets.sortedForDisplay(SnippetSortMode.MOST_USED).map { it.id })
    }

    private fun match(
        id: Long,
        trigger: String,
        label: String,
        created: Long,
        updated: Long,
        uses: Long,
    ) = TextMatch(
        id = id,
        triggers = listOf(MatchTrigger(trigger)),
        replacements = listOf("replacement"),
        label = label,
        createdAt = created,
        updatedAt = updated,
        usageCount = uses,
    )
}
