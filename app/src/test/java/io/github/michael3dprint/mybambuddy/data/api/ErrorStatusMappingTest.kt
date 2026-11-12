package io.github.michael3dprint.mybambuddy.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class ErrorStatusMappingTest {
    private fun errorFor(code: Int, body: String = ""): ApiResult.Error = runBlocking {
        safeApiCall { Response.error<Unit>(code, body.toResponseBody("application/json".toMediaType())) } as ApiResult.Error
    }

    @Test
    fun `every status the routes can return maps to a kind that names it correctly`() {
        mapOf(
            400 to ApiErrorType.REJECTED,
            401 to ApiErrorType.UNAUTHORIZED,
            403 to ApiErrorType.FORBIDDEN,
            404 to ApiErrorType.NOT_FOUND,
            409 to ApiErrorType.REJECTED,
            410 to ApiErrorType.REJECTED,
            413 to ApiErrorType.REJECTED,
            422 to ApiErrorType.REJECTED,
            500 to ApiErrorType.SERVER_ERROR,
            502 to ApiErrorType.SERVER_ERROR,
            503 to ApiErrorType.UNAVAILABLE,
            504 to ApiErrorType.SERVER_ERROR,
            507 to ApiErrorType.SERVER_ERROR,
        ).forEach { (code, type) -> assertEquals("HTTP $code", type, errorFor(code).errorType) }
    }

    @Test
    fun `the server's reason and code travel with the error`() {
        val e =
            errorFor(
                409,
                """{"detail":{"code":"insufficient_filament","deficit":[{"slot_id":1,"required_grams":10,"remaining_grams":2}]}}""",
            )
        assertEquals("insufficient_filament", e.serverCode)
        assertEquals("Not enough filament: filament 1 needs about 10 g, the slot has about 2 g.", e.serverDetail)
    }
}
