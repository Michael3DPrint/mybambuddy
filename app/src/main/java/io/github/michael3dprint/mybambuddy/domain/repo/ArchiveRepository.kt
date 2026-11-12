package io.github.michael3dprint.mybambuddy.domain.repo

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.HistoryItem

/** Bambuddy's print archive as the Previous prints list reads it (design 44). Reads only; nothing here changes or deletes an archive. */
interface ArchiveRepository {
    /** One page of archived prints, newest first, for printer [printerId] or for all printers when null. */
    suspend fun page(limit: Int, offset: Int, printerId: Int?): ApiResult<List<HistoryItem>>

    /** The small picture of one archived print as image bytes; an error when it has none. */
    suspend fun picture(archiveId: Int): ApiResult<ByteArray>
}
