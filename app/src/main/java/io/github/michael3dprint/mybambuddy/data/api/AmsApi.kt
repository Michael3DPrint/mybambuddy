package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.data.api.dto.InventoryAssignRequest
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterAmsStatusDto
import io.github.michael3dprint.mybambuddy.data.api.dto.SlotAssignmentDto
import io.github.michael3dprint.mybambuddy.data.api.dto.SpoolSlotAssignRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** AMS units, which spool sits in which slot (both inventory back-ends), and drying. */
interface AmsApi {
    /** Same URL as [PrinterApi.getPrinterStatus] but parsed into the AMS-focused DTO. */
    @GET("printers/{id}/status")
    suspend fun getPrinterAmsStatus(@Path("id") id: String): Response<PrinterAmsStatusDto>

    /** Which Spoolman spool is assigned to each AMS slot. */
    @GET("spoolman/inventory/slot-assignments/all")
    suspend fun getSpoolmanSlotAssignments(): Response<List<SlotAssignmentDto>>

    /** Which spool from Bambuddy's own inventory is assigned to each AMS slot. */
    @GET("inventory/assignments")
    suspend fun getInventoryAssignments(): Response<List<SlotAssignmentDto>>

    /** Assigns a Spoolman spool to a slot. Bambuddy saves the record and may also push the settings to the printer. */
    @POST("spoolman/inventory/slot-assignments")
    suspend fun assignSpoolmanSlot(@Body body: SpoolSlotAssignRequest): Response<Unit>

    /** Clears a Spoolman spool's slot record. */
    @DELETE("spoolman/inventory/slot-assignments/{spoolId}")
    suspend fun unassignSpoolmanSlot(@Path("spoolId") spoolId: Int): Response<Unit>

    // Streaming: the reply is optional extra information, so reading it must not be able to fail the (already saved) assignment.
    /** Assigns an inventory spool to a slot. The reply body is read leniently. */
    @Streaming
    @POST("inventory/assignments")
    suspend fun assignInventorySlot(@Body body: InventoryAssignRequest): Response<ResponseBody>

    /** Clears the record for one slot. */
    @DELETE("inventory/assignments/{printerId}/{amsId}/{trayId}")
    suspend fun unassignInventorySlot(
        @Path("printerId") printerId: Int,
        @Path("amsId") amsId: Int,
        @Path("trayId") trayId: Int,
    ): Response<Unit>

    /** Server accepts 45-85 C and 1-24 h; the repository validates before calling. */
    @POST("printers/{id}/drying/start")
    suspend fun startDrying(
        @Path("id") id: String,
        @Query("ams_id") amsId: Int,
        @Query("temp") temp: Int,
        @Query("duration") durationHours: Int,
        @Query("filament") filament: String = "",
    ): Response<Unit>

    /** Stops drying on one AMS unit. */
    @POST("printers/{id}/drying/stop")
    suspend fun stopDrying(@Path("id") id: String, @Query("ams_id") amsId: Int): Response<Unit>
}
