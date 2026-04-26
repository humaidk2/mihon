package mihon.feature.suwayomi

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.launch
import mihon.feature.suwayomi.extensions.SuwayomiExtensionsScreen
import mihon.feature.suwayomi.extensions.SuwayomiExtensionsScreenModel
import tachiyomi.i18n.MR

@Composable
fun Screen.suwayomiTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })

    val libraryModel = rememberScreenModel { SuwayomiLibraryScreenModel() }
    val libraryState by libraryModel.state.collectAsState()

    val extensionsModel = rememberScreenModel { SuwayomiExtensionsScreenModel() }
    val extensionsState by extensionsModel.state.collectAsState()

    return TabContent(
        titleRes = MR.strings.pref_category_suwayomi,
        content = { contentPadding, _ ->
            val layoutDirection = LocalLayoutDirection.current
            val innerPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top = 0.dp,
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding(),
            )

            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = pagerState.currentPage) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text("Library") },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text("Extensions") },
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (page) {
                        0 -> SuwayomiLibraryScreen(
                            state = libraryState,
                            onMangaClick = { serverManga ->
                                scope.launch {
                                    val localManga = libraryModel.networkToLocal(serverManga)
                                    if (localManga != null) {
                                        navigator.push(MangaScreen(localManga.id, fromSource = true))
                                    }
                                }
                            },
                            contentPadding = innerPadding,
                        )
                        1 -> SuwayomiExtensionsScreen(
                            state = extensionsState,
                            onInstall = extensionsModel::install,
                            onUninstall = extensionsModel::uninstall,
                            onUpdate = extensionsModel::update,
                            onRefresh = extensionsModel::refresh,
                            contentPadding = innerPadding,
                        )
                        else -> Unit
                    }
                }
            }
        },
    )
}
