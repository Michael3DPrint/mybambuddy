package io.github.michael3dprint.mybambuddy.ui.filament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.toUiErrorState
import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.DryingPresets
import io.github.michael3dprint.mybambuddy.domain.repo.AmsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The drying settings being edited for one AMS unit. */
data class DryingDraft(val material: String, val tempC: Int, val hours: Int) {
    companion object {
        fun forMaterial(material: String): DryingDraft {
            val preset = DryingPresets.forMaterial(material)
            return DryingDraft(preset.material, preset.tempC, preset.hours)
        }
    }
}

/** Drying settings and start/stop. Single attempts; the server's refusal message is shown as-is. [changed] fires after a success. */
class DryingViewModel(private val ams: AmsRepository) : ViewModel() {
    private val _drafts = MutableStateFlow<Map<String, DryingDraft>>(emptyMap())
    val drafts: StateFlow<Map<String, DryingDraft>> = _drafts.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _changed = MutableSharedFlow<Unit>()

    /** Emits after drying was started or stopped, so the screen can refresh the AMS list. */
    val changed: SharedFlow<Unit> = _changed.asSharedFlow()

    fun draftFor(unit: AmsUnit): DryingDraft {
        val firstMaterial = unit.trays.firstOrNull { !it.isEmpty }?.material.orEmpty()
        return _drafts.value[key(unit)] ?: DryingDraft.forMaterial(firstMaterial)
    }

    fun updateDraft(unit: AmsUnit, draft: DryingDraft) {
        _drafts.update { it + (key(unit) to draft) }
    }

    fun startDrying(unit: AmsUnit) {
        val draft = draftFor(unit)
        run("Drying started (${draft.tempC} °C, ${draft.hours} h)") {
            ams.startDrying(unit.printerId, unit.amsId, draft.tempC, draft.hours, draft.material.substringBefore('/'))
        }
    }

    fun stopDrying(unit: AmsUnit) = run("Drying stopped") { ams.stopDrying(unit.printerId, unit.amsId) }

    private fun run(successMessage: String, action: suspend () -> ApiResult<Unit>) {
        viewModelScope.launch {
            when (val result = action()) {
                is ApiResult.Success -> {
                    _messages.emit(successMessage)
                    _changed.emit(Unit)
                }
                is ApiResult.Error -> _messages.emit(result.toUiErrorState().message)
            }
        }
    }

    private fun key(unit: AmsUnit) = "${unit.printerId}:${unit.amsId}"

    class Factory(private val ams: AmsRepository) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DryingViewModel(ams) as T
    }
}
