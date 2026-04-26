package mihon.feature.suwayomi.sources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mihon.feature.suwayomi.ServerSource

@Composable
fun SuwayomiSourcesScreen(
    state: SuwayomiSourcesScreenModel.State,
    onSourceClick: (ServerSource) -> Unit,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
) {
    when (state) {
        is SuwayomiSourcesScreenModel.State.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is SuwayomiSourcesScreenModel.State.Error -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRefresh) { Text("Retry") }
                }
            }
        }

        is SuwayomiSourcesScreenModel.State.Success -> {
            if (state.sources.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No sources found.\nInstall extensions in the Extensions tab first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(contentPadding = contentPadding) {
                    items(state.sources, key = { it.id }) { source ->
                        SourceItem(source = source, onClick = { onSourceClick(source) })
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceItem(source: ServerSource, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(source.displayName) },
        supportingContent = {
            Text(
                text = buildString {
                    append(source.lang.uppercase())
                    if (source.isNsfw) append(" · 18+")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
