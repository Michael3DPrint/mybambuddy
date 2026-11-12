package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.data.api.dto.InventorySpoolDto
import io.github.michael3dprint.mybambuddy.data.api.dto.SpoolCreateRequest
import io.github.michael3dprint.mybambuddy.data.api.dto.SpoolTagLinkRequest
import io.github.michael3dprint.mybambuddy.data.api.dto.SpoolWeightRequest
import io.github.michael3dprint.mybambuddy.data.api.dto.SpoolmanStatusDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Spool inventory. Bambuddy exposes the same flat model twice: the Spoolman passthrough (under `spoolman/inventory`) and its
 * built-in inventory (under `inventory`). `InventoryModeProvider` decides which one a repository calls.
 */
interface InventoryApi {
    /** Whether Spoolman is enabled and connected, as Bambuddy sees it. */
    @GET("spoolman/status")
    suspend fun getSpoolmanStatus(): Response<SpoolmanStatusDto>

    /** The spools held in Spoolman, read through Bambuddy. */
    @GET("spoolman/inventory/spools")
    suspend fun getSpoolmanSpools(): Response<List<InventorySpoolDto>>

    /** Creates a spool in Spoolman, creating the vendor and filament as needed. */
    @POST("spoolman/inventory/spools")
    suspend fun createSpoolmanSpool(@Body body: SpoolCreateRequest): Response<InventorySpoolDto>

    /** The spools in Bambuddy's own inventory. */
    @GET("inventory/spools")
    suspend fun getInventorySpools(): Response<List<InventorySpoolDto>>

    /** Creates a spool in Bambuddy's own inventory. */
    @POST("inventory/spools")
    suspend fun createInventorySpool(@Body body: SpoolCreateRequest): Response<InventorySpoolDto>

    /** Links an NFC tag UID to a Spoolman spool. */
    @PATCH("spoolman/inventory/spools/{id}/tag")
    suspend fun linkSpoolmanTag(@Path("id") id: Int, @Body body: SpoolTagLinkRequest): Response<Unit>

    /** Sets a Spoolman spool's remaining filament from a scale reading; the server subtracts the empty spool's weight. */
    @PATCH("spoolman/inventory/spools/{id}/weight")
    suspend fun setSpoolmanWeight(@Path("id") id: Int, @Body body: SpoolWeightRequest): Response<Unit>

    /** Links an NFC tag UID to a spool in Bambuddy's own inventory. */
    @PATCH("inventory/spools/{id}/link-tag")
    suspend fun linkInventoryTag(@Path("id") id: Int, @Body body: SpoolTagLinkRequest): Response<Unit>
}
