package io.github.michael3dprint.mybambuddy.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.michael3dprint.mybambuddy.domain.network.BambuddyFinder
import io.github.michael3dprint.mybambuddy.domain.network.NetworkInfo
import io.github.michael3dprint.mybambuddy.domain.network.NetworkVerdictRule
import io.github.michael3dprint.mybambuddy.domain.network.Subnet
import io.github.michael3dprint.mybambuddy.domain.network.SubnetRules
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the phone could see about its own network; a null value means it could not tell. */
data class WelcomeChecks(val wifi: Subnet?, val vpnActive: Boolean?, val tailscaleInstalled: Boolean?)

/** Where "Find Bambuddy" is. */
sealed interface FindState {
    /** Nothing started. */
    data object Idle : FindState

    /** A sweep is running: [done] of [total] addresses checked. */
    data class Scanning(val done: Int, val total: Int) : FindState

    /** The sweep found Bambuddy at these addresses. */
    data class Found(val hosts: List<String>) : FindState

    /** The sweep finished and found nothing. */
    data object NotFound : FindState

    /** The sweep could not run, with the reason to show. */
    data class CouldNotScan(val reason: String) : FindState
}

/** What the welcome screen shows. */
data class WelcomeUiState(val checks: WelcomeChecks = WelcomeChecks(null, null, null), val find: FindState = FindState.Idle)

/** Runs the welcome screen's read-only checks and the "Find Bambuddy" sweep of the phone's own network. */
class WelcomeViewModel(private val network: NetworkInfo, private val finder: BambuddyFinder) : ViewModel() {
    private val _uiState = MutableStateFlow(WelcomeUiState())

    /** The checks and the sweep state. */
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    init {
        refreshChecks()
    }

    /** Reads the phone's network state again. */
    fun refreshChecks() {
        _uiState.update {
            it.copy(
                checks = WelcomeChecks(
                    wifi = network.wifiSubnet(),
                    vpnActive = network.vpnActive(),
                    tailscaleInstalled = network.isInstalled(NetworkVerdictRule.TAILSCALE_PACKAGE),
                ),
            )
        }
    }

    /** Sweeps the phone's own home network for Bambuddy. [localNetworkAllowed] is false when Android's local-network permission was refused. */
    fun find(localNetworkAllowed: Boolean) {
        if (!localNetworkAllowed) {
            return fail(
                "Android blocked access to the local network. Allow it in this app's Android settings, or type the address.",
            )
        }
        val subnet =
            network.wifiSubnet()
                ?: return fail("This phone is not on Wi-Fi, so there is no home network to look at. Type the address instead.")
        val hosts = SubnetRules.hosts(subnet)
        if (hosts.isEmpty()) return fail("This network is not a home-network range, so it was not scanned. Type the address instead.")
        job?.cancel()
        job = viewModelScope.launch {
            _uiState.update { it.copy(find = FindState.Scanning(0, hosts.size)) }
            val found = finder.find(hosts) { done, total -> _uiState.update { s -> s.copy(find = FindState.Scanning(done, total)) } }
            _uiState.update { it.copy(find = if (found.isEmpty()) FindState.NotFound else FindState.Found(found)) }
        }
    }

    /** Stops a running sweep. */
    fun stopFind() {
        job?.cancel()
        _uiState.update { it.copy(find = FindState.Idle) }
    }

    private fun fail(reason: String) = _uiState.update { it.copy(find = FindState.CouldNotScan(reason)) }

    /** Builds the view model with its two collaborators. */
    class Factory(private val network: NetworkInfo, private val finder: BambuddyFinder) : ViewModelProvider.Factory {
        // Safe: this factory is only asked for the one ViewModel class it builds.
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WelcomeViewModel(network, finder) as T
    }
}
