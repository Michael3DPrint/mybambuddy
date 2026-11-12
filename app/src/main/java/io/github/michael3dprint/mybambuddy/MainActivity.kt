package io.github.michael3dprint.mybambuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.michael3dprint.mybambuddy.data.prefs.ThemeMode
import io.github.michael3dprint.mybambuddy.domain.nfc.DetectedTag
import io.github.michael3dprint.mybambuddy.domain.policy.PollInterval
import io.github.michael3dprint.mybambuddy.domain.policy.ScreenWidthPolicy
import io.github.michael3dprint.mybambuddy.ui.alerts.AlertSettingsScreen
import io.github.michael3dprint.mybambuddy.ui.alerts.AlertSettingsViewModel
import io.github.michael3dprint.mybambuddy.ui.common.OfflineBanner
import io.github.michael3dprint.mybambuddy.ui.detail.PrinterDetailPane
import io.github.michael3dprint.mybambuddy.ui.detail.printerTabSources
import io.github.michael3dprint.mybambuddy.ui.filament.AmsFilamentViewModel
import io.github.michael3dprint.mybambuddy.ui.filament.AmsViewModel
import io.github.michael3dprint.mybambuddy.ui.filament.BarcodeIntakeViewModel
import io.github.michael3dprint.mybambuddy.ui.filament.DryingViewModel
import io.github.michael3dprint.mybambuddy.ui.filament.FilamentScreen
import io.github.michael3dprint.mybambuddy.ui.filament.FilamentViewModel
import io.github.michael3dprint.mybambuddy.ui.filament.NfcTagViewModel
import io.github.michael3dprint.mybambuddy.ui.filament.SlotActions
import io.github.michael3dprint.mybambuddy.ui.filament.SlotAssignmentViewModel
import io.github.michael3dprint.mybambuddy.ui.filament.SlotNfcScanViewModel
import io.github.michael3dprint.mybambuddy.ui.filament.SlotWeightSyncViewModel
import io.github.michael3dprint.mybambuddy.ui.filament.SpoolWeighViewModel
import io.github.michael3dprint.mybambuddy.ui.history.HistoryScreen
import io.github.michael3dprint.mybambuddy.ui.history.HistoryViewModel
import io.github.michael3dprint.mybambuddy.ui.history.StatsViewModel
import io.github.michael3dprint.mybambuddy.ui.navigation.BackPolicy
import io.github.michael3dprint.mybambuddy.ui.navigation.BackStep
import io.github.michael3dprint.mybambuddy.ui.navigation.DeepLink
import io.github.michael3dprint.mybambuddy.ui.navigation.DeepLinks
import io.github.michael3dprint.mybambuddy.ui.navigation.Routes
import io.github.michael3dprint.mybambuddy.ui.navigation.ToolsRequest
import io.github.michael3dprint.mybambuddy.ui.print.PrintFlowViewModel
import io.github.michael3dprint.mybambuddy.ui.print.ProvidePrintFlow
import io.github.michael3dprint.mybambuddy.ui.printers.PrintersTab
import io.github.michael3dprint.mybambuddy.ui.printers.PrintersViewModel
import io.github.michael3dprint.mybambuddy.ui.queue.QueueScreen
import io.github.michael3dprint.mybambuddy.ui.queue.QueueViewModel
import io.github.michael3dprint.mybambuddy.ui.settings.SettingsScreen
import io.github.michael3dprint.mybambuddy.ui.settings.SettingsViewModel
import io.github.michael3dprint.mybambuddy.ui.setup.SetupGate
import io.github.michael3dprint.mybambuddy.ui.setup.SetupViewModel
import io.github.michael3dprint.mybambuddy.ui.theme.MyBambuddyTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    /** Set when a notification was tapped: the printer to open (validated, numeric only). */
    private var requestedLink by mutableStateOf<DeepLink?>(null)

    /** Set when a tag was tapped outside the app: shown in the scan dialog, read only. */
    private var tappedTag by mutableStateOf<DetectedTag?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        requestedLink = linkFrom(intent)
        tappedTag = (application as PrintDeckApp).container.nfcGateway.tagFrom(intent)
    }

    private fun linkFrom(intent: android.content.Intent?): DeepLink? =
        intent?.let { DeepLinks.parse(it.getIntExtra(DeepLinks.EXTRA_PRINTER_ID, -1), it.getIntExtra(DeepLinks.EXTRA_SPOOL_ID, -1)) }

    override fun onResume() {
        super.onResume()
        (application as PrintDeckApp).container.nfcGateway.attach(this)
    }

    override fun onPause() {
        (application as PrintDeckApp).container.nfcGateway.detach()
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedLink = linkFrom(intent)
        // Only on a fresh start: after a rotation the same intent would otherwise reopen the dialog.
        if (savedInstanceState == null) tappedTag = (application as PrintDeckApp).container.nfcGateway.tagFrom(intent)

        val container = (application as PrintDeckApp).container
        val settingsRepository = container.settings
        val printerRepository = container.printers
        val printControlRepository = container.printControl
        val historyRepository = container.history
        val spoolRepository = container.spools
        val catalogRepository = container.catalog
        val amsRepository = container.ams
        val cameraClient = container.cameraClient
        val imageLoader = container.imageLoader

        val pollIntervalMs: suspend () -> Long = { PollInterval.toMillis(settingsRepository.pollingIntervalSeconds.first()) }

        val setupViewModel: SetupViewModel by viewModels {
            SetupViewModel.Factory(settingsRepository)
        }

        val printersViewModel: PrintersViewModel by viewModels {
            PrintersViewModel.Factory(printerRepository, pollIntervalMs, container.maintenance)
        }

        val filamentViewModel: FilamentViewModel by viewModels {
            FilamentViewModel.Factory(spoolRepository, catalogRepository)
        }

        val nfcGateway = container.nfcGateway
        val nfcViewModel: NfcTagViewModel by viewModels {
            NfcTagViewModel.Factory(nfcGateway, spoolRepository)
        }

        val feedViewModel: AmsFilamentViewModel by viewModels { AmsFilamentViewModel.Factory(container.printControl) }
        val scanViewModel: SlotNfcScanViewModel by viewModels { SlotNfcScanViewModel.Factory(container.nfcGateway, spoolRepository) }
        val syncViewModel: SlotWeightSyncViewModel by viewModels { SlotWeightSyncViewModel.Factory(spoolRepository) }
        val slotActions = SlotActions(feedViewModel, scanViewModel, syncViewModel)

        val weighViewModel: SpoolWeighViewModel by viewModels {
            SpoolWeighViewModel.Factory(spoolRepository, container.connectivity)
        }

        val barcodeViewModel: BarcodeIntakeViewModel by viewModels {
            BarcodeIntakeViewModel.Factory(container.barcodeScanner, container.barcodeResolver)
        }

        val printFlowViewModel: PrintFlowViewModel by viewModels {
            container.printFlowFactory
        }

        val queueViewModel: QueueViewModel by viewModels {
            QueueViewModel.Factory(container.queue, container.connectivity, container.readiness)
        }

        val alertSettingsViewModel: AlertSettingsViewModel by viewModels {
            AlertSettingsViewModel.Factory(container.alertSettingsStore)
        }

        val slotViewModel: SlotAssignmentViewModel by viewModels {
            SlotAssignmentViewModel.Factory(amsRepository)
        }

        val dryingViewModel: DryingViewModel by viewModels {
            DryingViewModel.Factory(amsRepository)
        }

        val amsViewModel: AmsViewModel by viewModels {
            AmsViewModel.Factory(amsRepository, spoolRepository)
        }

        val historyViewModel: HistoryViewModel by viewModels {
            HistoryViewModel.Factory(historyRepository)
        }

        val statsViewModel: StatsViewModel by viewModels {
            StatsViewModel.Factory(container.stats)
        }

        val settingsViewModel: SettingsViewModel by viewModels {
            SettingsViewModel.Factory(settingsRepository)
        }

        setContent {
            val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dynamicColor by settingsRepository.dynamicColor.collectAsStateWithLifecycle(initialValue = false)

            // null until DataStore has emitted, so we never pick the start screen from a placeholder value.
            val serverUrl by produceState<String?>(initialValue = null) {
                settingsRepository.serverUrl.collect { value = it }
            }

            val offlineState by container.connectivity.state.collectAsStateWithLifecycle()

            MyBambuddyTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                val loadedUrl = serverUrl
                if (loadedUrl == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    return@MyBambuddyTheme
                }
                io.github.michael3dprint.mybambuddy.ui.help.ProvideHelp(onUseAddress = settingsViewModel::onServerUrlChanged) {
                    ProvidePrintFlow(printFlowViewModel, container.queueJobFactory, container::previousPrintsFactory) {
                        // The API key is optional (only needed when Bambuddy auth is on), so only the URL counts.
                        var currentScreen by rememberSaveable { mutableStateOf(if (loadedUrl.isNotBlank()) Routes.MAIN else Routes.SETUP) }
                        var selectedTab by rememberSaveable { mutableStateOf(AppTab.PRINTERS) }
                        var selectedPrinterId by rememberSaveable { mutableStateOf<String?>(null) }
                        // The printer whose page should open on its Tools tab, set by tapping the Remote control line and cleared once used.
                        var toolsFor by rememberSaveable { mutableStateOf<String?>(null) }
                        var openSpoolId by rememberSaveable { mutableStateOf<Int?>(null) }
                        LaunchedEffect(requestedLink) {
                            when (val link = requestedLink) {
                                is DeepLink.Printer -> {
                                    selectedPrinterId = link.id
                                    currentScreen = Routes.DETAIL
                                }
                                is DeepLink.Spool -> {
                                    openSpoolId = link.id
                                    selectedTab = AppTab.FILAMENT
                                    currentScreen = Routes.MAIN
                                }
                                null -> Unit
                            }
                            requestedLink = null
                        }

                        LaunchedEffect(tappedTag) {
                            tappedTag?.let {
                                selectedTab = AppTab.FILAMENT
                                currentScreen = Routes.MAIN
                                nfcViewModel.openFromTag(it)
                            }
                            tappedTag = null
                        }

                        val detailPane: @Composable (String, () -> Unit) -> Unit = { id, back ->
                            PrinterDetailPane(
                                printerId = id,
                                printerRepository = printerRepository,
                                printControlRepository = printControlRepository,
                                connectivity = container.connectivity,
                                pollIntervalMs = pollIntervalMs,
                                imageLoader = imageLoader,
                                cameraClient = cameraClient,
                                tabSources = container.printerTabSources(slotViewModel, slotActions, dryingViewModel),
                                openTools = ToolsRequest.appliesTo(toolsFor, id),
                                onToolsOpened = { toolsFor = ToolsRequest.afterHandled() },
                                onBack = back,
                            )
                        }
                        val wide = ScreenWidthPolicy.isWide(LocalConfiguration.current.screenWidthDp)

                        // On a wide screen the Detail page is the right-hand pane of the main page (design 28).
                        val screen = if (wide && currentScreen == Routes.DETAIL) Routes.MAIN else currentScreen

                        // The system Back button: a nested page returns to the main page, another tab returns to Printers,
                        // otherwise the app closes.
                        val backStep = BackPolicy.onBack(screen, selectedTab)
                        BackHandler(enabled = backStep != BackStep.LEAVE_APP) {
                            when (backStep) {
                                BackStep.GO_TO_MAIN -> currentScreen = Routes.MAIN
                                BackStep.SELECT_PRINTERS -> selectedTab = AppTab.PRINTERS
                                BackStep.LEAVE_APP -> Unit
                            }
                        }

                        Scaffold(
                            bottomBar = {
                                if (screen == Routes.MAIN && !wide) AppBottomBar(selectedTab) { selectedTab = it }
                            },
                            modifier = Modifier.fillMaxSize(),
                        ) { innerPadding ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(if (screen == Routes.MAIN) innerPadding else PaddingValues()),
                            ) {
                                if (wide && screen == Routes.MAIN) AppNavigationRail(selectedTab) { selectedTab = it }
                                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                                    OfflineBanner(offlineState)
                                    Box(
                                        modifier = Modifier.weight(1f).fillMaxSize().then(
                                            // The banner already sits below the status bar; stop the screen's own top bar adding a second gap.
                                            if (offlineState.isOffline) {
                                                Modifier.consumeWindowInsets(
                                                    WindowInsets.statusBars,
                                                )
                                            } else {
                                                Modifier
                                            },
                                        ),
                                    ) {
                                        when {
                                            screen == Routes.SETUP -> {
                                                SetupGate(
                                                    setupViewModel = setupViewModel,
                                                    onSetupComplete = {
                                                        printersViewModel.refresh()
                                                        historyViewModel.loadInitialHistory()
                                                        currentScreen = Routes.MAIN
                                                        selectedTab = AppTab.PRINTERS
                                                    },
                                                )
                                            }

                                            screen == Routes.ALERTS -> {
                                                AlertSettingsScreen(
                                                    viewModel = alertSettingsViewModel,
                                                    onBack = { currentScreen = Routes.MAIN },
                                                )
                                            }

                                            screen == Routes.DETAIL && selectedPrinterId != null -> {
                                                detailPane(selectedPrinterId.orEmpty()) { currentScreen = Routes.MAIN }
                                            }

                                            else -> {
                                                when (selectedTab) {
                                                    AppTab.PRINTERS -> {
                                                        PrintersTab(
                                                            wide = wide,
                                                            viewModel = printersViewModel,
                                                            imageLoader = imageLoader,
                                                            selectedPrinterId = selectedPrinterId,
                                                            onPrinterClick = { printerId ->
                                                                selectedPrinterId = printerId
                                                                toolsFor = ToolsRequest.afterCardTap()
                                                                if (!wide) currentScreen = Routes.DETAIL
                                                            },
                                                            onOpenTools = { id ->
                                                                selectedPrinterId = id
                                                                toolsFor = ToolsRequest.afterLineTap(id)
                                                                if (!wide) currentScreen = Routes.DETAIL
                                                            },
                                                            onOpenSettings = { selectedTab = AppTab.SETTINGS },
                                                            detail = { id -> detailPane(id) { selectedPrinterId = null } },
                                                        )
                                                    }

                                                    AppTab.QUEUE -> {
                                                        QueueScreen(
                                                            viewModel = queueViewModel,
                                                            title = stringResource(R.string.queue_all_printers),
                                                        )
                                                    }

                                                    AppTab.FILAMENT -> {
                                                        FilamentScreen(
                                                            viewModel = filamentViewModel,
                                                            amsViewModel = amsViewModel,
                                                            slotViewModel = slotViewModel,
                                                            dryingViewModel = dryingViewModel,
                                                            nfcViewModel = nfcViewModel,
                                                            barcodeViewModel = barcodeViewModel,
                                                            weighViewModel = weighViewModel,
                                                            slotActions = slotActions,
                                                            openSpoolId = openSpoolId,
                                                            onSpoolOpened = { openSpoolId = null },
                                                        )
                                                    }

                                                    AppTab.HISTORY -> {
                                                        HistoryScreen(
                                                            viewModel = historyViewModel,
                                                            statsViewModel = statsViewModel,
                                                            imageLoader = imageLoader,
                                                        )
                                                    }

                                                    AppTab.SETTINGS -> {
                                                        SettingsScreen(
                                                            viewModel = settingsViewModel,
                                                            onOpenAlerts = { currentScreen = Routes.ALERTS },
                                                            onSettingsSaved = {
                                                                printersViewModel.refresh()
                                                                historyViewModel.loadInitialHistory()
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
