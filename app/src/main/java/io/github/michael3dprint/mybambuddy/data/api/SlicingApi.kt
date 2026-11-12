package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.data.api.dto.SlicerPresetsDto
import io.github.michael3dprint.mybambuddy.data.api.dto.SlicerSettingsDto
import retrofit2.Response
import retrofit2.http.GET

/** Read-only calls for the slicing readiness check (design 36). Nothing here changes a setting or starts a slice. */
interface SlicingApi {
    /** Bambuddy's settings. The reply holds every setting, so only the slicing fields are read and nothing is logged. */
    @GET("settings/")
    suspend fun getSettings(): Response<SlicerSettingsDto>

    /** The slicer presets in tiers; the standard tier comes from the slicer app, so it is empty when Bambuddy cannot reach it. */
    @GET("slicer/presets")
    suspend fun getPresets(): Response<SlicerPresetsDto>
}
