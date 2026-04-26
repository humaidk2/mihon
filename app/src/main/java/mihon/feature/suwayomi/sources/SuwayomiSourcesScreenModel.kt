package mihon.feature.suwayomi.sources

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.launch
import mihon.feature.suwayomi.ServerSource
import mihon.feature.suwayomi.SuwayomiSource
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SuwayomiSourcesScreenModel(
    sourceManager: SourceManager = Injekt.get(),
) : StateScreenModel<SuwayomiSourcesScreenModel.State>(State.Loading) {

    private val source = sourceManager.get(SuwayomiSource.ID) as? SuwayomiSource

    init {
        screenModelScope.launch { load() }
    }

    fun refresh() {
        screenModelScope.launch { load() }
    }

    private suspend fun load() {
        mutableState.value = State.Loading
        mutableState.value = try {
            source?.login()
            val sources = source?.api?.getSources() ?: emptyList()
            State.Success(sources.sortedWith(compareBy({ it.lang }, { it.displayName })))
        } catch (e: Exception) {
            State.Error(e.message ?: "Failed to load sources")
        }
    }

    sealed class State {
        data object Loading : State()
        data class Success(val sources: List<ServerSource>) : State()
        data class Error(val message: String) : State()
    }
}
