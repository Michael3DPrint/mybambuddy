package io.github.michael3dprint.mybambuddy.data.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.data.api.StatsApi
import io.github.michael3dprint.mybambuddy.data.api.dto.ArchiveStatsDto
import io.github.michael3dprint.mybambuddy.data.api.safeApiCall
import io.github.michael3dprint.mybambuddy.domain.model.PrintStats
import io.github.michael3dprint.mybambuddy.domain.model.StatBar
import io.github.michael3dprint.mybambuddy.domain.policy.StatsPeriod
import io.github.michael3dprint.mybambuddy.domain.repo.StatsRepository
import java.time.LocalDate
import java.time.ZoneId

/** Reads statistics through [StatsApi]; the clock and time zone are parameters so the dates can be tested. */
class StatsRepositoryImpl(
    private val api: StatsApi,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : StatsRepository {
    override suspend fun getStats(period: StatsPeriod): ApiResult<PrintStats> {
        val range = period.range(today())
        val result = safeApiCall { api.getStats(range.from?.toString(), range.to?.toString(), zone().id) }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Error -> result
        }
    }
}

private const val MAX_BARS = 4

/** Maps the reply: missing numbers become 0, printer ids are named from the server's own list, and only the largest bars are kept. */
internal fun ArchiveStatsDto.toDomain(): PrintStats = PrintStats(
    totalPrints = totalPrints ?: 0,
    successfulPrints = successfulPrints ?: 0,
    failedPrints = failedPrints ?: 0,
    cancelledPrints = cancelledPrints ?: 0,
    totalHours = totalHours ?: 0.0,
    totalGrams = totalGrams ?: 0.0,
    byPrinter = byPrinter.orEmpty().map { (id, count) ->
        StatBar(printerNames?.get(id)?.takeIf { it.isNotBlank() } ?: "Printer $id", count.toInt())
    }
        .topBars(),
    byMaterial = byMaterial.orEmpty().map { (name, count) -> StatBar(name, count.toInt()) }.topBars(),
)

private fun List<StatBar>.topBars(): List<StatBar> = filter { it.count > 0 }.sortedByDescending { it.count }.take(MAX_BARS)
