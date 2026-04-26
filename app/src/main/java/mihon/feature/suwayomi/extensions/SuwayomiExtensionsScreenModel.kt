package mihon.feature.suwayomi.extensions

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.launch
import mihon.feature.suwayomi.ServerExtension
import mihon.feature.suwayomi.SuwayomiSource
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SuwayomiExtensionsScreenModel(
    sourceManager: SourceManager = Injekt.get(),
) : StateScreenModel<SuwayomiExtensionsScreenModel.State>(State.Loading) {

    private val source = sourceManager.get(SuwayomiSource.ID) as? SuwayomiSource

    init {
        screenModelScope.launch { load() }
    }

    private suspend fun load() {
        mutableState.value = State.Loading
        mutableState.value = try {
            source?.login()
            val all = source?.api?.getExtensions() ?: emptyList()
            all.toSuccessState()
        } catch (e: Exception) {
            State.Error(e.message ?: "Failed to load extensions")
        }
    }

    fun refresh() {
        screenModelScope.launch {
            mutableState.value = State.Loading
            mutableState.value = try {
                val all = source?.api?.refreshExtensions() ?: emptyList()
                all.toSuccessState()
            } catch (e: Exception) {
                State.Error(e.message ?: "Failed to refresh extensions")
            }
        }
    }

    fun install(pkgName: String) = mutate(pkgName) { api -> api.installExtension(pkgName) }
    fun uninstall(pkgName: String) = mutate(pkgName) { api -> api.uninstallExtension(pkgName) }
    fun update(pkgName: String) = mutate(pkgName) { api -> api.updateExtension(pkgName) }

    private fun mutate(pkgName: String, action: suspend (mihon.feature.suwayomi.SuwayomiApi) -> ServerExtension?) {
        val current = mutableState.value as? State.Success ?: return
        mutableState.value = current.copy(pending = current.pending + pkgName)

        screenModelScope.launch {
            try {
                val updated = source?.api?.let { action(it) }
                val refreshed = source?.api?.getExtensions() ?: emptyList()
                mutableState.value = refreshed.toSuccessState(
                    pending = (mutableState.value as? State.Success)?.pending?.minus(pkgName) ?: emptySet(),
                )
            } catch (e: Exception) {
                val s = mutableState.value as? State.Success ?: return@launch
                mutableState.value = s.copy(pending = s.pending - pkgName)
            }
        }
    }

    private fun List<ServerExtension>.toSuccessState(pending: Set<String> = emptySet()) = State.Success(
        installed = filter { it.isInstalled }.sortedBy { it.name },
        available = filter { !it.isInstalled }.sortedBy { it.name },
        pending = pending,
    )

    sealed class State {
        data object Loading : State()
        data class Success(
            val installed: List<ServerExtension>,
            val available: List<ServerExtension>,
            val pending: Set<String> = emptySet(),
        ) : State()
        data class Error(val message: String) : State()
    }
}
