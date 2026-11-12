package io.github.michael3dprint.mybambuddy.domain.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.DiagnosticReport

/** Bambuddy's connection diagnostics for one printer. The server tests its connection to the printer; nothing is sent to the printer by the app. */
interface DiagnosticsRepository {
    /** Runs the diagnostics. Takes up to about 15 seconds. A single attempt, started only when the user asks. */
    suspend fun run(printerId: Int): ApiResult<DiagnosticReport>
}
