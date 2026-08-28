package dev.diego.expanda.data

enum class SnippetSortMode {
    RECENTLY_EDITED,
    OLDEST_EDITED,
    NAME_ASCENDING,
    NAME_DESCENDING,
    NEWEST_CREATED,
    MOST_USED,
}

fun List<TextMatch>.sortedForDisplay(mode: SnippetSortMode): List<TextMatch> {
    val displayName: (TextMatch) -> String = { match ->
        match.label.ifBlank { match.trigger.trimStart { !it.isLetterOrDigit() } }.trim().lowercase()
    }
    val comparator = when (mode) {
        SnippetSortMode.RECENTLY_EDITED -> compareByDescending<TextMatch> { it.updatedAt }
        SnippetSortMode.OLDEST_EDITED -> compareBy<TextMatch> { it.updatedAt }
        SnippetSortMode.NAME_ASCENDING -> compareBy(displayName)
        SnippetSortMode.NAME_DESCENDING -> compareByDescending(displayName)
        SnippetSortMode.NEWEST_CREATED -> compareByDescending<TextMatch> { it.createdAt }
        SnippetSortMode.MOST_USED -> compareByDescending<TextMatch> { it.usageCount }
    }
    return sortedWith(comparator.thenBy { it.id })
}
