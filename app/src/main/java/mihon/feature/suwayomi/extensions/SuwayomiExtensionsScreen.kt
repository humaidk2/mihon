package mihon.feature.suwayomi.extensions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mihon.feature.suwayomi.ServerExtension

@Composable
fun SuwayomiExtensionsScreen(
    state: SuwayomiExtensionsScreenModel.State,
    onInstall: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onUpdate: (String) -> Unit,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
) {
    when (state) {
        is SuwayomiExtensionsScreenModel.State.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is SuwayomiExtensionsScreenModel.State.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRefresh) { Text("Retry") }
                }
            }
        }

        is SuwayomiExtensionsScreenModel.State.Success -> {
            LazyColumn(contentPadding = contentPadding) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Extensions",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = onRefresh) { Text("Refresh") }
                    }
                }

                if (state.installed.isNotEmpty()) {
                    stickyHeader(key = "header_installed") {
                        SectionHeader("Installed (${state.installed.size})")
                    }
                    items(state.installed, key = { it.pkgName }) { ext ->
                        ExtensionItem(
                            ext = ext,
                            isPending = ext.pkgName in state.pending,
                            trailingAction = {
                                when {
                                    ext.pkgName in state.pending -> {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                    ext.hasUpdate -> {
                                        Row {
                                            Button(onClick = { onUpdate(ext.pkgName) }) { Text("Update") }
                                            Spacer(Modifier.width(8.dp))
                                            OutlinedButton(onClick = { onUninstall(ext.pkgName) }) { Text("Remove") }
                                        }
                                    }
                                    else -> {
                                        OutlinedButton(onClick = { onUninstall(ext.pkgName) }) { Text("Uninstall") }
                                    }
                                }
                            },
                        )
                    }
                }

                if (state.available.isNotEmpty()) {
                    stickyHeader(key = "header_available") {
                        SectionHeader("Available (${state.available.size})")
                    }
                    items(state.available, key = { it.pkgName }) { ext ->
                        ExtensionItem(
                            ext = ext,
                            isPending = ext.pkgName in state.pending,
                            trailingAction = {
                                if (ext.pkgName in state.pending) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    Button(onClick = { onInstall(ext.pkgName) }) { Text("Install") }
                                }
                            },
                        )
                    }
                }

                if (state.installed.isEmpty() && state.available.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No extensions found.\nTap Refresh to fetch from repos.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ExtensionItem(
    ext: ServerExtension,
    isPending: Boolean,
    trailingAction: @Composable () -> Unit,
) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ext.name)
                if (ext.isNsfw) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "18+",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        supportingContent = {
            Text(
                text = buildString {
                    append(ext.lang.uppercase())
                    append(" · v")
                    append(ext.versionName)
                    if (ext.isObsolete) append(" · Obsolete")
                    if (ext.hasUpdate) append(" · Update available")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = trailingAction,
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
