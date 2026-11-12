package io.github.michael3dprint.mybambuddy.ui.alerts

import io.github.michael3dprint.mybambuddy.domain.alerts.AlertSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertSettingsViewModelTest {
    private class FakeStore(var saved: AlertSettings = AlertSettings(), var url: String = "http://100.64.0.10:8000/api/v1") :
        AlertSettingsStore {
        val applied = mutableListOf<AlertSettings>()
        var hidden = false

        override fun load() = saved

        override fun apply(settings: AlertSettings) {
            saved = settings
            applied += settings
        }

        override fun serverUrl() = url

        override fun isHelpHidden() = hidden

        override fun setHelpHidden(hidden: Boolean) {
            this.hidden = hidden
        }
    }

    @Test
    fun `it starts from the saved choices`() {
        val vm = AlertSettingsViewModel(FakeStore(AlertSettings(enabled = true, printerOffline = true)))
        assertTrue(vm.settings.value.enabled)
        assertTrue(vm.settings.value.printerOffline)
    }

    @Test
    fun `each change is shown and saved through the store`() {
        val store = FakeStore()
        val vm = AlertSettingsViewModel(store)
        vm.update { it.copy(enabled = true) }
        vm.update { it.copy(printFinished = false) }
        assertEquals(true, vm.settings.value.enabled)
        assertEquals(false, vm.settings.value.printFinished)
        assertEquals(2, store.applied.size)
        assertEquals(vm.settings.value, store.saved)
    }

    @Test
    fun `the help card is shown until hidden, and the choice is saved and can be undone`() {
        val store = FakeStore()
        val vm = AlertSettingsViewModel(store)
        assertEquals(false, vm.helpHidden.value)
        vm.setHelpHidden(true)
        assertEquals(true, vm.helpHidden.value)
        assertEquals(true, store.hidden)
        assertEquals(true, AlertSettingsViewModel(store).helpHidden.value)
        vm.setHelpHidden(false)
        assertEquals(false, store.hidden)
    }

    @Test
    fun `the Open Bambuddy address comes from the saved server address, or is null when unusable`() {
        assertEquals("http://100.64.0.10:8000", AlertSettingsViewModel(FakeStore()).webAddress)
        assertEquals(null, AlertSettingsViewModel(FakeStore(url = "")).webAddress)
    }
}
