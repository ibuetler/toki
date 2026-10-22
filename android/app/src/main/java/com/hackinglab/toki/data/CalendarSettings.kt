package com.hackinglab.toki.data

import java.net.URI
import java.util.Locale

data class CalendarSource(val id: String = "", val name: String = "", val url: String = "", val enabled: Boolean = true,
    val lastSuccess: String = "", val lastError: String = "") {
    fun validate() {
        require(name.isNotBlank() && name.length <= 120 && !name.contains('·')) { "Enter a calendar name (up to 120 characters, without ·)." }
        val uri = runCatching { URI(url) }.getOrNull()
        require(url.length <= 4096 && uri?.scheme == "https" && uri.host != null && uri.userInfo == null && uri.fragment == null && uri.port in listOf(-1, 443)) { "Enter a published HTTPS calendar URL." }
    }
}
data class CalendarRule(val id: String = "", val pattern: String = "", val source: String = "", val enabled: Boolean = true, val matchType: String = "wildcard") {
    fun validate() { require(pattern.isNotBlank() && pattern.length <= 200) { "Enter a title pattern (up to 200 characters)." } }
}
data class CalendarSettings(val sources: List<CalendarSource> = emptyList(), val rules: List<CalendarRule> = emptyList())

object TitlePattern {
    fun matches(title: String, pattern: String, exact: Boolean = false): Boolean {
        val text = title.trim().lowercase(Locale.ROOT)
        val expression = pattern.trim().lowercase(Locale.ROOT)
        if (expression.isEmpty() || expression.length > 200) return false
        if (exact) return text == expression
        var previous = BooleanArray(text.length + 1).apply { this[0] = true }
        for (token in expression) {
            val current = BooleanArray(text.length + 1)
            current[0] = previous[0] && token == '*'
            for (index in 1..text.length) current[index] = if (token == '*') previous[index] || current[index-1]
                else previous[index-1] && (token == '?' || token == text[index-1])
            previous = current
        }
        return previous.last()
    }
    fun preview(events: List<Deadline>, rule: CalendarRule, sources: List<CalendarSource>): Int {
        val sourceName = sources.find { it.id == rule.source }?.name
        return events.count { it.kind == "calendar" && (rule.source.isBlank() || sourceName in it.sourceNames.split(" · ")) && matches(it.title, rule.pattern, rule.matchType == "exact") }
    }
}
