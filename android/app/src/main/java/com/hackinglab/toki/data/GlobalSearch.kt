package com.hackinglab.toki.data

import java.util.Locale

data class SearchResult(val id: String, val kind: String, val title: String, val detail: String, val completed: Boolean = false) {
    val key get() = "$kind:$id"
}
object GlobalSearch {
    fun find(events: List<Deadline>, tasks: List<TokiTask>, query: String): List<SearchResult> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        val results = events.filterNot { it.hidden }.filter { e -> listOf(e.title, e.description, e.groupName, e.sourceNames).any { it.contains(term, ignoreCase = true) } }.map {
            SearchResult(it.id, if (it.kind == "birthday") "birthday" else "event", it.title, it.sourceNames.ifBlank { it.groupName.ifBlank { "Private" } })
        } + tasks.filter { it.title.contains(term, true) || it.notes.contains(term, true) }.map {
            SearchResult(it.id, "task", it.title, it.priority.replaceFirstChar { letter -> letter.uppercase() }, it.completed)
        }
        return results.sortedWith(compareBy<SearchResult> { it.title.lowercase(Locale.ROOT) }.thenBy { it.kind }.thenBy { it.id })
    }
}
