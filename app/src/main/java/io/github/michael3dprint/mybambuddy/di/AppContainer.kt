package io.github.michael3dprint.mybambuddy.di

import android.app.Application
import coil.ImageLoader
import io.github.michael3dprint.mybambuddy.data.api.AmsApi
import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.CameraApi
import io.github.michael3dprint.mybambuddy.data.api.DiagnosticApi
import io.github.michael3dprint.mybambuddy.data.api.HistoryApi
import io.github.michael3dprint.mybambuddy.data.api.InventoryApi
import io.github.michael3dprint.mybambuddy.data.api.MaintenanceApi
import io.github.michael3dprint.mybambuddy.data.api.PrintFileApi
import io.github.michael3dprint.mybambuddy.data.api.PrinterApi
import io.github.michael3dprint.mybambuddy.data.api.PrinterControlApi
import io.github.michael3dprint.mybambuddy.data.api.QueueApi
import io.github.michael3dprint.mybambuddy.data.api.SlicingApi
import io.github.michael3dprint.mybambuddy.data.api.SpoolUsageApi
import io.github.michael3dprint.mybambuddy.data.api.StatsApi
import io.github.michael3dprint.mybambuddy.data.barcode.UpcItemDbLookup
import io.github.michael3dprint.mybambuddy.data.prefs.AlertPrefs
import io.github.michael3dprint.mybambuddy.data.prefs.BarcodePrefsImpl
import io.github.michael3dprint.mybambuddy.data.prefs.SettingsRepository
import io.github.michael3dprint.mybambuddy.data.prefs.SettingsRepositoryImpl
import io.github.michael3dprint.mybambuddy.data.repo.AmsCache
import io.github.michael3dprint.mybambuddy.data.repo.AmsRepositoryImpl
import io.github.michael3dprint.mybambuddy.data.repo.ArchiveRepositoryImpl
import io.github.michael3dprint.mybambuddy.data.repo.CacheAges
import io.github.michael3dprint.mybambuddy.data.repo.CachingAmsRepository
import io.github.michael3dprint.mybambuddy.data.repo.CachingHistoryRepository
import io.github.michael3dprint.mybambuddy.data.repo.CachingMaintenanceRepository
import io.github.michael3dprint.mybambuddy.data.repo.CachingPrinterRepository
import io.github.michael3dprint.mybambuddy.data.repo.CachingQueueRepository
import io.github.michael3dprint.mybambuddy.data.repo.CachingSpoolRepository
import io.github.michael3dprint.mybambuddy.data.repo.CatalogRepositoryImpl
import io.github.michael3dprint.mybambuddy.data.repo.DeveloperModeGuard
import io.github.michael3dprint.mybambuddy.data.repo.DiagnosticsRepositoryImpl
import io.github.michael3dprint.mybambuddy.data.repo.HistoryCache
import io.github.michael3dprint.mybambuddy.data.repo.HistoryRepositoryImpl
import io.github.michael3dprint.mybambuddy.data.repo.InventoryMode
import io.github.michael3dprint.mybambuddy.data.repo.InventoryModeProvider
import io.github.michael3dprint.mybambuddy.data.repo.MaintenanceCache
import io.github.michael3dprint.mybambuddy.data.repo.MaintenanceRepositoryImpl
import io.github.michael3dprint.mybambuddy.data.repo.PrintControlRepositoryImpl
import io.github.michael3dprint.mybambuddy.data.repo.PrintFileRepositoryImpl
import io.github.michael3dprint.mybambuddy.data.repo.PrinterRepositoryImpl
import io.github.michael3dprint.mybambuddy.data.repo.PrintersCache
import io.github.michael3dprint.mybambuddy.data.repo.QueueCache
import io.github.michael3dprint.mybambuddy.data.repo.QueueRepositoryImpl
import io.github.michael3dprint.mybambuddy.data.repo.ReadinessCheckImpl
import io.github.michael3dprint.mybambuddy.data.repo.SlicingRepositoryImpl
import io.github.michael3dprint.mybambuddy.data.repo.SpoolRepositoryImpl
import io.github.michael3dprint.mybambuddy.data.repo.SpoolsCache
import io.github.michael3dprint.mybambuddy.data.repo.StatsRepositoryImpl
import io.github.michael3dprint.mybambuddy.data.store.AlertMemoryFileStore
import io.github.michael3dprint.mybambuddy.data.store.SliceMemoryFileStore
import io.github.michael3dprint.mybambuddy.data.store.WidgetSnapshotFileStore
import io.github.michael3dprint.mybambuddy.data.store.cacheStore
import io.github.michael3dprint.mybambuddy.domain.alerts.AlertMemory
import io.github.michael3dprint.mybambuddy.domain.alerts.AlertRunner
import io.github.michael3dprint.mybambuddy.domain.alerts.AlertSettings
import io.github.michael3dprint.mybambuddy.domain.barcode.BarcodeResolver
import io.github.michael3dprint.mybambuddy.domain.barcode.BarcodeScanner
import io.github.michael3dprint.mybambuddy.domain.connectivity.ConnectivityStatus
import io.github.michael3dprint.mybambuddy.domain.connectivity.DefaultConnectivityStatus
import io.github.michael3dprint.mybambuddy.domain.repo.AmsRepository
import io.github.michael3dprint.mybambuddy.domain.repo.ArchiveRepository
import io.github.michael3dprint.mybambuddy.domain.repo.CatalogRepository
import io.github.michael3dprint.mybambuddy.domain.repo.DiagnosticsRepository
import io.github.michael3dprint.mybambuddy.domain.repo.HistoryRepository
import io.github.michael3dprint.mybambuddy.domain.repo.MaintenanceRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrintControlRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrintFileRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterRepository
import io.github.michael3dprint.mybambuddy.domain.repo.PrinterToolGate
import io.github.michael3dprint.mybambuddy.domain.repo.QueueRepository
import io.github.michael3dprint.mybambuddy.domain.repo.ReadinessCheck
import io.github.michael3dprint.mybambuddy.domain.repo.SlicingRepository
import io.github.michael3dprint.mybambuddy.domain.repo.SpoolRepository
import io.github.michael3dprint.mybambuddy.domain.repo.StatsRepository
import io.github.michael3dprint.mybambuddy.domain.slicing.SliceMemory
import io.github.michael3dprint.mybambuddy.domain.slicing.SliceMemoryStore
import io.github.michael3dprint.mybambuddy.domain.widget.WidgetFeedingPrinterRepository
import io.github.michael3dprint.mybambuddy.domain.widget.WidgetRefresher
import io.github.michael3dprint.mybambuddy.domain.widget.WidgetRenderer
import io.github.michael3dprint.mybambuddy.domain.widget.WidgetSnapshot
import io.github.michael3dprint.mybambuddy.domain.widget.WidgetSnapshotStore
import io.github.michael3dprint.mybambuddy.platform.alerts.AlertScheduler
import io.github.michael3dprint.mybambuddy.platform.alerts.AndroidAlertNotifier
import io.github.michael3dprint.mybambuddy.platform.barcode.GmsBarcodeScanner
import io.github.michael3dprint.mybambuddy.platform.nfc.AndroidNfcGateway
import io.github.michael3dprint.mybambuddy.platform.widget.AndroidWidgetRenderer
import io.github.michael3dprint.mybambuddy.ui.alerts.AlertSettingsStore
import io.github.michael3dprint.mybambuddy.ui.print.PrintFlowViewModel
import io.github.michael3dprint.mybambuddy.ui.queue.PreviousPrintsViewModel
import io.github.michael3dprint.mybambuddy.ui.queue.QueueJobViewModel
import io.github.michael3dprint.mybambuddy.util.CoilImageLoaderFactory
import io.github.michael3dprint.mybambuddy.util.SnapshotCache
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * The composition root: the ONLY place that knows concrete classes. Everything else receives interfaces.
 * Built once per process by [io.github.michael3dprint.mybambuddy.PrintDeckApp].
 */
class AppContainer(app: Application) {
    val settings: SettingsRepository = SettingsRepositoryImpl(app)
    val connectivity: ConnectivityStatus = DefaultConnectivityStatus(failuresBeforeOffline = 2)
    val nfcGateway = AndroidNfcGateway()

    private val okHttp = ApiClient.createOkHttpClient(settings)
    private val printerApi = ApiClient.createService(okHttp, PrinterApi::class.java)
    private val inventoryApi = ApiClient.createService(okHttp, InventoryApi::class.java)
    val readiness: ReadinessCheck = ReadinessCheckImpl(printerApi)

    /**
     * Connection diagnostics: the server probes the printer and may wait about 10 s for it to publish, so this one service gets longer
     * limits than the shared client (design 20).
     */
    val diagnostics: DiagnosticsRepository = DiagnosticsRepositoryImpl(
        ApiClient.createService(
            okHttp.newBuilder().readTimeout(45, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build(),
            DiagnosticApi::class.java,
        ),
    )
    val toolGate: PrinterToolGate = DeveloperModeGuard(readiness)
    private val moshi = ApiClient.createMoshi()
    private val cacheDir = File(app.filesDir, "cache")
    private val inventoryMode =
        InventoryModeProvider(inventoryApi, cacheStore(cacheDir, "inventory-mode", moshi, InventoryMode::class.java))

    // Live-only readers. The caching decorators below wrap these for the UI; the background alert check reads these
    // directly, because a decorator turns "server unreachable" into saved data and the check could not tell (design 16, F1).
    private val livePrinters: PrinterRepository = PrinterRepositoryImpl(printerApi, settings)
    private val liveSpools: SpoolRepository =
        SpoolRepositoryImpl(inventoryApi, inventoryMode, ApiClient.createService(okHttp, SpoolUsageApi::class.java))
    private val liveMaintenance: MaintenanceRepository =
        MaintenanceRepositoryImpl(ApiClient.createService(okHttp, MaintenanceApi::class.java))

    val widgetStore: WidgetSnapshotStore =
        WidgetSnapshotFileStore(cacheStore(cacheDir, "widget", moshi, WidgetSnapshot::class.java))
    val widgetRenderer: WidgetRenderer = AndroidWidgetRenderer(app)

    // Every real read the app makes also refreshes the widget's saved snapshot; it sits under the cache so saved data is never passed off as fresh.
    val printers: PrinterRepository = CachingPrinterRepository(
        WidgetFeedingPrinterRepository(livePrinters, widgetStore, widgetRenderer),
        cacheStore(cacheDir, "printers", moshi, PrintersCache::class.java),
        connectivity,
        maxAgeMs = CacheAges.LIVE_STATE,
        minWriteGapMs = CacheAges.PRINTER_WRITE_GAP,
    )
    val printControl: PrintControlRepository =
        PrintControlRepositoryImpl(ApiClient.createService(okHttp, PrinterControlApi::class.java), toolGate)
    val history: HistoryRepository = CachingHistoryRepository(
        HistoryRepositoryImpl(ApiClient.createService(okHttp, HistoryApi::class.java)),
        cacheStore(cacheDir, "history", moshi, HistoryCache::class.java),
        connectivity,
        maxAgeMs = CacheAges.ARCHIVE,
    )
    val spools: SpoolRepository = CachingSpoolRepository(
        liveSpools,
        cacheStore(cacheDir, "spools", moshi, SpoolsCache::class.java),
        connectivity,
    )
    val catalog: CatalogRepository = CatalogRepositoryImpl(inventoryMode, settings)
    val ams: AmsRepository = CachingAmsRepository(
        AmsRepositoryImpl(printerApi, ApiClient.createService(okHttp, AmsApi::class.java), inventoryMode, toolGate),
        cacheStore(cacheDir, "ams", moshi, AmsCache::class.java),
        connectivity,
        maxAgeMs = CacheAges.LIVE_STATE,
    )

    val maintenance: MaintenanceRepository = CachingMaintenanceRepository(
        liveMaintenance,
        cacheStore(cacheDir, "maintenance", moshi, MaintenanceCache::class.java),
        connectivity,
    )

    val stats: StatsRepository = StatsRepositoryImpl(ApiClient.createService(okHttp, StatsApi::class.java))
    private val slicingApi = ApiClient.createService(okHttp, SlicingApi::class.java)

    // Uploads, MakerWorld downloads and reading an unsliced project (the server slices a preview) take longer than the 10 second
    // read limit and 30 second call limit of everyday calls, so the print-from-the-phone calls get their own, longer limits.
    private val longOkHttp = okHttp.newBuilder()
        .readTimeout(LONG_CALL_MINUTES, TimeUnit.MINUTES)
        .writeTimeout(LONG_CALL_MINUTES, TimeUnit.MINUTES)
        .callTimeout(LONG_CALL_MINUTES * 2, TimeUnit.MINUTES)
        .build()
    val printFiles: PrintFileRepository =
        PrintFileRepositoryImpl(ApiClient.createService(longOkHttp, PrintFileApi::class.java), slicingApi, toolGate, cacheDir)
    val slicing: SlicingRepository = SlicingRepositoryImpl(slicingApi, livePrinters)
    val sliceMemory: SliceMemoryStore = SliceMemoryFileStore(cacheStore(cacheDir, "slice-memory", moshi, SliceMemory::class.java))
    val archives: ArchiveRepository = ArchiveRepositoryImpl(ApiClient.createService(okHttp, HistoryApi::class.java))

    /** The factory for the Previous prints list of one printer, or of all printers when [printerId] is null (design 44). */
    fun previousPrintsFactory(printerId: Int?) = PreviousPrintsViewModel.Factory(archives, printerId)

    val printFlowFactory by lazy { PrintFlowViewModel.Factory(printFiles, printers, ams, toolGate, queue, printControl, sliceMemory) }
    val queueJobFactory by lazy { QueueJobViewModel.Factory(queue, printFiles, printers, ams, sliceMemory) }
    val queue: QueueRepository = CachingQueueRepository(
        QueueRepositoryImpl(ApiClient.createService(okHttp, QueueApi::class.java), toolGate),
        cacheStore(cacheDir, "queue", moshi, QueueCache::class.java),
        connectivity,
        maxAgeMs = CacheAges.LIVE_STATE,
    )

    val widgetRefresher = WidgetRefresher(livePrinters, widgetStore, widgetRenderer)

    val alertPrefs = AlertPrefs(app)
    val alertRunner = AlertRunner(
        printers = livePrinters,
        spools = liveSpools,
        maintenance = liveMaintenance,
        memory = AlertMemoryFileStore(cacheStore(cacheDir, "alert-memory", moshi, AlertMemory::class.java)),
        notifier = AndroidAlertNotifier(app),
        settings = alertPrefs,
    )

    /** Stores the alert choices and switches the background check to match. */
    val alertSettingsStore = object : AlertSettingsStore {
        override fun load() = alertPrefs.load()

        override fun apply(settings: AlertSettings) {
            alertPrefs.save(settings)
            AlertScheduler.apply(app, settings.enabled)
        }

        override fun serverUrl() = settings.getServerUrlSync()

        override fun isHelpHidden() = alertPrefs.helpHidden

        override fun setHelpHidden(hidden: Boolean) {
            alertPrefs.helpHidden = hidden
        }
    }

    val barcodeScanner: BarcodeScanner = GmsBarcodeScanner(app)
    val barcodeResolver = BarcodeResolver(UpcItemDbLookup(), BarcodePrefsImpl(app))

    val cameraClient: OkHttpClient = CoilImageLoaderFactory.createCameraClient(
        settings,
        ApiClient.createService(okHttp, CameraApi::class.java),
    )
    val imageLoader: ImageLoader = CoilImageLoaderFactory.create(app, cameraClient)

    init {
        SnapshotCache.init(app)
    }
}

private const val LONG_CALL_MINUTES = 3L
