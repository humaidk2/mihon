package mihon.feature.suwayomi.sources

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.manga.model.toDomainManga
import mihon.feature.suwayomi.BrowseManga
import mihon.feature.suwayomi.SuwayomiSource
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SuwayomiBrowseScreenModel(
    private val sourceId: String,
    private val supportsLatest: Boolean,
    sourceManager: SourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
) : StateScreenModel<SuwayomiBrowseScreenModel.State>(State(supportsLatest = supportsLatest)) {

    private val source = sourceManager.get(SuwayomiSource.ID) as? SuwayomiSource

    init {
        screenModelScope.launch {
            source?.login()
            loadPage(BrowseType.POPULAR, 1)
        }
    }

    fun loadTab(type: BrowseType) {
        val tab = mutableState.value.tabFor(type)
        if (tab.page > 0 || tab.isLoading) return
        screenModelScope.launch { loadPage(type, 1) }
    }

    fun loadMore(type: BrowseType) {
        val tab = mutableState.value.tabFor(type)
        if (!tab.hasNextPage || tab.isLoading) return
        val query = if (type == BrowseType.SEARCH) mutableState.value.searchQuery else null
        screenModelScope.launch { loadPage(type, tab.page + 1, query) }
    }

    fun updateSearch(query: String) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun executeSearch() {
        val query = mutableState.value.searchQuery.trim()
        if (query.isEmpty()) return
        mutableState.update { it.copy(search = TabState()) }
        screenModelScope.launch { loadPage(BrowseType.SEARCH, 1, query) }
    }

    fun addToLibrary(mangaId: Int) {
        screenModelScope.launch {
            try {
                source?.api?.addToLibrary(mangaId)
                mutableState.update { s ->
                    fun List<BrowseManga>.mark() = map { if (it.id == mangaId) it.copy(inLibrary = true) else it }
                    s.copy(
                        popular = s.popular.copy(mangas = s.popular.mangas.mark()),
                        latest = s.latest.copy(mangas = s.latest.mangas.mark()),
                        search = s.search.copy(mangas = s.search.mangas.mark()),
                    )
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun networkToLocal(m: BrowseManga): Long? {
        val s = source ?: return null
        return networkToLocalManga(s.api.browseToSManga(m).toDomainManga(SuwayomiSource.ID))?.id
    }

    private suspend fun loadPage(type: BrowseType, page: Int, query: String? = null) {
        mutableState.update { s -> s.updateTab(type) { it.copy(isLoading = true, error = null) } }
        try {
            val result = source?.api?.fetchSourceManga(sourceId, type.name, page, query) ?: return
            mutableState.update { s ->
                s.updateTab(type) { tab ->
                    tab.copy(
                        mangas = if (page == 1) result.mangas else tab.mangas + result.mangas,
                        isLoading = false,
                        hasNextPage = result.hasNextPage,
                        page = page,
                    )
                }
            }
        } catch (e: Exception) {
            mutableState.update { s ->
                s.updateTab(type) { it.copy(isLoading = false, error = e.message ?: "Error") }
            }
        }
    }

    enum class BrowseType(val label: String) {
        POPULAR("Popular"),
        LATEST("Latest"),
        SEARCH("Search"),
    }

    data class TabState(
        val mangas: List<BrowseManga> = emptyList(),
        val isLoading: Boolean = false,
        val hasNextPage: Boolean = false,
        val page: Int = 0,
        val error: String? = null,
    )

    data class State(
        val popular: TabState = TabState(),
        val latest: TabState = TabState(),
        val search: TabState = TabState(),
        val searchQuery: String = "",
        val supportsLatest: Boolean,
    ) {
        fun tabFor(type: BrowseType) = when (type) {
            BrowseType.POPULAR -> popular
            BrowseType.LATEST -> latest
            BrowseType.SEARCH -> search
        }

        fun updateTab(type: BrowseType, update: (TabState) -> TabState) = when (type) {
            BrowseType.POPULAR -> copy(popular = update(popular))
            BrowseType.LATEST -> copy(latest = update(latest))
            BrowseType.SEARCH -> copy(search = update(search))
        }
    }
}
