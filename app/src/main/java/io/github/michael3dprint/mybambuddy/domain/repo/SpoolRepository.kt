package io.github.michael3dprint.mybambuddy.domain.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.model.SpoolDraft
import io.github.michael3dprint.mybambuddy.domain.model.UsageEntry

/** The user's spool inventory. */
interface SpoolRepository {
    /** The active spools in the inventory. */
    suspend fun getSpools(): ApiResult<List<Spool>>

    /** Creates a spool from the draft and returns it with its new id. */
    suspend fun createSpool(draft: SpoolDraft): ApiResult<Spool>

    /** Links an NFC tag UID to the spool so the tag identifies it. */
    suspend fun linkTag(spoolId: Int, tagUid: String): ApiResult<Unit>

    /** True when weighing can be used: only the Spoolman inventory has the route. */
    suspend fun canWeigh(): Boolean

    /** Sets the spool's remaining filament from a scale reading of [grossGrams] (spool included). A write: single attempt, never cached. */
    suspend fun updateWeight(spoolId: Int, grossGrams: Double): ApiResult<Unit>

    /** Which prints used this spool, newest first. */
    suspend fun getUsage(spoolId: Int): ApiResult<List<UsageEntry>>
}
