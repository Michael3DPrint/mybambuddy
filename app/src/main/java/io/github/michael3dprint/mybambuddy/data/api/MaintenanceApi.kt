package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.data.api.dto.PerformMaintenanceBody
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterMaintenanceDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Bambuddy maintenance routes: the overview, and marking an item done. */
interface MaintenanceApi {
    /** The upkeep items of every printer. */
    @GET("maintenance/overview")
    suspend fun getOverview(): Response<List<PrinterMaintenanceDto>>

    /** Marks a maintenance item as done, which resets its counter. */
    @POST("maintenance/items/{id}/perform")
    suspend fun perform(@Path("id") id: Int, @Body body: PerformMaintenanceBody = PerformMaintenanceBody()): Response<Unit>
}
