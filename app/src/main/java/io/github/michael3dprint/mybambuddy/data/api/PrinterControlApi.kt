package io.github.michael3dprint.mybambuddy.data.api

import retrofit2.Response
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Calls that change printer state. Never retried automatically (see ENGINEERING_STANDARDS §3) and never sent as a test
 * against the live printer; verify with MockWebServer.
 */
interface PrinterControlApi {
    /** Pauses the running print. */
    @POST("printers/{id}/print/pause")
    suspend fun pausePrint(@Path("id") id: String): Response<Unit>

    /** Resumes a paused print. */
    @POST("printers/{id}/print/resume")
    suspend fun resumePrint(@Path("id") id: String): Response<Unit>

    /** Cancels the running print. */
    @POST("printers/{id}/print/stop")
    suspend fun cancelPrint(@Path("id") id: String): Response<Unit>

    /** Asks the server to request a fresh status from the printer. */
    @POST("printers/{id}/refresh-status")
    suspend fun refreshStatus(@Path("id") id: String): Response<Unit>

    /** Clears the printer's HMS errors. */
    @POST("printers/{id}/hms/clear")
    suspend fun clearHms(@Path("id") id: String): Response<Unit>

    /** Marks the build plate as cleared in Bambuddy. */
    @POST("printers/{id}/clear-plate")
    suspend fun clearPlate(@Path("id") id: String): Response<Unit>

    /** Sets the print speed mode: 1 silent, 2 standard, 3 sport, 4 ludicrous. */
    @POST("printers/{id}/print-speed")
    suspend fun setPrintSpeed(@Path("id") id: String, @Query("mode") mode: Int): Response<Unit>

    /** Switches the chamber light on or off. */
    @POST("printers/{id}/chamber-light")
    suspend fun setChamberLight(@Path("id") id: String, @Query("on") on: Boolean): Response<Unit>

    /** Feeds filament from the AMS slot with printer tray number [trayId] into the printer. Moves filament. */
    @POST("printers/{id}/ams/load")
    suspend fun amsLoad(@Path("id") id: String, @Query("tray_id") trayId: Int): Response<Unit>

    /** Unloads the filament fed from the AMS slot with printer tray number [trayId]. Moves filament. */
    @POST("printers/{id}/ams/unload")
    suspend fun amsUnload(@Path("id") id: String, @Query("tray_id") trayId: Int): Response<Unit>
}
