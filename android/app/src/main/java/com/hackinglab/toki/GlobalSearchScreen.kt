package com.hackinglab.toki

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hackinglab.toki.data.SearchResult

@Composable internal fun GlobalSearchScreen(query: String, results: List<SearchResult>, onQuery: (String) -> Unit,
    onClose: () -> Unit, onSelect: (SearchResult) -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LazyColumn(Modifier.fillMaxSize().systemBarsPadding().imePadding().testTag("search-results"),
                contentPadding = PaddingValues(20.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                    Text("Search TOKI", style = MaterialTheme.typography.headlineMedium)
                    TextButton(onClick = onClose) { Text("Done") }
                } }
                item { OutlinedTextField(query, onQuery, label = { Text("Search events, birthdays and tasks") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { onQuery("") }) { Text("Clear") } }) }
                item { Text("Searches titles, descriptions and notes cached on this phone, including past events and completed tasks.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (query.isBlank()) item { Text("Type to search all your lists.") }
                else {
                    item { Text(if (results.size == 1) "1 result" else "${results.size} results", style = MaterialTheme.typography.labelLarge) }
                    if (results.isEmpty()) item { Text("No matching items.") }
                    items(results, key = { it.key }) { result ->
                        OutlinedCard(onClick = { onSelect(result) }, modifier = Modifier.fillMaxWidth().testTag("search-${result.key}")) {
                            Column(Modifier.padding(12.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                                Text(result.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(result.kind.replaceFirstChar { it.uppercase() } + (if (result.completed) " · Completed" else "") + " · ${result.detail}",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
