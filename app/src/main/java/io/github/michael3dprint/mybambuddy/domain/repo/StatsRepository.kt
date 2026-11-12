package io.github.michael3dprint.mybambuddy.domain.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.PrintStats
import io.github.michael3dprint.mybambuddy.domain.policy.StatsPeriod

/** Print statistics from Bambuddy. Read only. */
interface StatsRepository {
    /** The statistics for [period]. */
    suspend fun getStats(period: StatsPeriod): ApiResult<PrintStats>
}
