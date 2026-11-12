package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.DiagnosticApi
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterDiagnosticDto
import io.github.michael3dprint.mybambuddy.data.api.safeApiCall
import io.github.michael3dprint.mybambuddy.domain.model.DiagnosticCheck
import io.github.michael3dprint.mybambuddy.domain.model.DiagnosticOverall
import io.github.michael3dprint.mybambuddy.domain.model.DiagnosticReport
import io.github.michael3dprint.mybambuddy.domain.model.DiagnosticStatus
import io.github.michael3dprint.mybambuddy.domain.nfc.TagText
import io.github.michael3dprint.mybambuddy.domain.repo.DiagnosticsRepository

/** Runs Bambuddy's diagnostics through [DiagnosticApi]. A single read-only request; it is never retried by this class or cached. */
class DiagnosticsRepositoryImpl(private val api: DiagnosticApi) : DiagnosticsRepository {
    override suspend fun run(printerId: Int): ApiResult<DiagnosticReport> =
        when (val result = safeApiCall { api.getDiagnostic(printerId.toString()) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.toReport())
            is ApiResult.Error -> result
        }
}

private const val MAX_CHECKS = 20
private const val MAX_ID_LENGTH = 40

/** Maps the reply defensively: unknown statuses become UNKNOWN, ids are cleaned and capped, and at most 20 checks are kept. */
internal fun PrinterDiagnosticDto.toReport(): DiagnosticReport = DiagnosticReport(
    overall = when (overall?.lowercase()) {
        "ok" -> DiagnosticOverall.OK
        "warnings" -> DiagnosticOverall.WARNINGS
        "problems" -> DiagnosticOverall.PROBLEMS
        else -> DiagnosticOverall.UNKNOWN
    },
    checks = checks.orEmpty().take(MAX_CHECKS).mapNotNull { dto ->
        val id = TagText.sanitize(dto.id.orEmpty(), MAX_ID_LENGTH).takeIf { it.isNotBlank() } ?: return@mapNotNull null
        DiagnosticCheck(
            id = id,
            status = when (dto.status?.lowercase()) {
                "pass" -> DiagnosticStatus.PASS
                "warn" -> DiagnosticStatus.WARN
                "fail" -> DiagnosticStatus.FAIL
                "skip" -> DiagnosticStatus.SKIP
                else -> DiagnosticStatus.UNKNOWN
            },
        )
    },
)
