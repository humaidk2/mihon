package mihon.feature.suwayomi

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.launch
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SuwayomiLibraryScreenModel(
    sourceManager: SourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
) : StateScreenModel<SuwayomiLibraryScreenModel.State>(State.Loading) {

    internal val source = sourceManager.get(SuwayomiSource.ID) as? SuwayomiSource

    init {
        screenModelScope.launch {
            mutableState.value = try {
                val manga = source?.api?.getLibrary() ?: emptyList()
                State.Success(manga)
            } catch (e: Exception) {
                State.Error(e.message ?: "Failed to load library")
            }
        }
    }

    suspend fun networkToLocal(serverManga: ServerManga): Manga? {
        val s = source ?: return null
        return networkToLocalManga(s.api.toSManga(serverManga).toDomainManga(SuwayomiSource.ID))
    }

    sealed class State {
        data object Loading : State()
        data class Success(val manga: List<ServerManga>) : State()
        data class Error(val message: String) : State()
    }
}