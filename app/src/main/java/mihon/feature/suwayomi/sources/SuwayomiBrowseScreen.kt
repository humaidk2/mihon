package mihon.feature.suwayomi.sources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import eu.kanade.presentation.util.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.launch
import mihon.feature.suwayomi.BrowseManga
import mihon.feature.suwayomi.sources.SuwayomiBrowseScreenModel.BrowseType

data class SuwayomiBrowseScreen(
    private val sourceId: String,
    private val sourceName: String,
    private val supportsLatest: Boolean,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val screenModel = rememberScreenModel { SuwayomiBrowseScreenModel(sourceId, supportsLatest) }
        val state by screenModel.state.collectAsState()

        val tabs = remember(supportsLatest) {
            buildList {
                add(BrowseType.POPULAR)
                if (supportsLatest) add(BrowseType.LATEST)
                add(BrowseType.SEARCH)
            }
        }
        val pagerState = rememberPagerState(pageCount = { tabs.size })

        LaunchedEffect(pagerState.currentPage) {
            val type = tabs[pagerState.currentPage]
            if (type != BrowseType.SEARCH) screenModel.loadTab(type)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(sourceName) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                )
            },
        ) { scaffoldPadding ->
            Column(
                modifier = Modifier
                    .padding(scaffoldPadding)
                    .fillMaxSize(),
            ) {
                TabRow(selectedTabIndex = pagerState.currentPage) {
                    tabs.forEachIndexed { index, type ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(type.label) },
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val type = tabs[page]
                    val tabState = state.tabFor(type)

                    if (type == BrowseType.SEARCH) {
                        SearchTab(
                            tabState = tabState,
                            searchQuery = state.searchQuery,
                            onQueryChange = screenModel::updateSearch,
                            onSearch = screenModel::executeSearch,
                            onLoadMore = { screenModel.loadMore(BrowseType.SEARCH) },
                            onMangaClick = { manga ->
                                scope.launch {
                                    val id = screenModel.networkToLocal(manga) ?: return@launch
                                    navigator.push(MangaScreen(id, fromSource = true))
                                }
                            },
                            onAddToLibrary = screenModel::addToLibrary,
                        )
                    } else {
                        BrowseTab(
                            tabState = tabState,
                            onLoadMore = { screenModel.loadMore(type) },
                            onMangaClick = { manga ->
                                scope.launch {
                                    val id = screenModel.networkToLocal(manga) ?: return@launch
                                    navigator.push(MangaScreen(id, fromSource = true))
                                }
                            },
                            onAddToLibrary = screenModel::addToLibrary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseTab(
    tabState: SuwayomiBrowseScreenModel.TabState,
    onLoadMore: () -> Unit,
    onMangaClick: (BrowseManga) -> Unit,
    onAddToLibrary: (Int) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = gridState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 4
        }
    }

    LaunchedEffect(nearEnd) {
        if (nearEnd) onLoadMore()
    }

    when {
        tabState.page == 0 && tabState.isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        tabState.page == 0 && tabState.error != null -> {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(tabState.error, color = MaterialTheme.colorScheme.error)
            }
        }
        else -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                state = gridState,
                contentPadding = PaddingValues(4.dp),
            ) {
                items(tabState.mangas, key = { it.id }) { manga ->
                    MangaCard(manga = manga, onClick = { onMangaClick(manga) }, onAddToLibrary = { onAddToLibrary(manga.id) })
                }
                if (tabState.isLoading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTab(
    tabState: SuwayomiBrowseScreenModel.TabState,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onMangaClick: (BrowseManga) -> Unit,
    onAddToLibrary: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            placeholder = { Text("Search…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        BrowseTab(
            tabState = tabState,
            onLoadMore = onLoadMore,
            onMangaClick = onMangaClick,
            onAddToLibrary = onAddToLibrary,
        )
    }
}

@Composable
private fun MangaCard(
    manga: BrowseManga,
    onClick: () -> Unit,
    onAddToLibrary: () -> Unit,
) {
    Card(modifier = Modifier.padding(4.dp)) {
        Box {
            Column(
                modifier = Modifier
                    .clickable(onClick = onClick),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        onClick = onAddToLibrary,
                        enabled = !manga.inLibrary,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (manga.inLibrary) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (manga.inLibrary) "In library" else "Add to library",
                            tint = if (manga.inLibrary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
