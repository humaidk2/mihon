package mihon.feature.suwayomi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR

@Composable
fun Screen.suwayomiTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()
    val screenModel = rememberScreenModel { SuwayomiLibraryScreenModel() }
    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = MR.strings.pref_category_suwayomi,
        content = { contentPadding, _ ->
            SuwayomiLibraryScreen(
                state = state,
                onMangaClick = { serverManga ->
                    scope.launch {
                        val localManga = screenModel.networkToLocal(serverManga)
                        if (localManga != null) {
                            navigator.push(MangaScreen(localManga.id, fromSource = true))
                        }
                    }
                },
                contentPadding = contentPadding,
            )
        },
    )
}