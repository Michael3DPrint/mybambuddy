package io.github.michael3dprint.mybambuddy.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class UiErrorMessageTest {
    @Test
    fun `a client-side refusal shows its own reason, not the generic text`() {
        val e = ApiResult.Error(message = "X2D is printing. Wait.", errorType = ApiErrorType.UNSUPPORTED)
        assertEquals("X2D is printing. Wait.", e.toUiErrorState().message)
    }

    @Test
    fun `an unsupported error with no message falls back to the generic text`() {
        assertEquals(
            "This action isn't supported.",
            ApiResult.Error(message = "", errorType = ApiErrorType.UNSUPPORTED).toUiErrorState().message,
        )
    }

    @Test
    fun `server detail still wins and other types keep their friendly text`() {
        assertEquals(
            "AMS is busy",
            ApiResult.Error(message = "m", errorType = ApiErrorType.REJECTED, serverDetail = "AMS is busy").toUiErrorState().message,
        )
        assertEquals(
            "Unable to connect to Bambuddy server. Please check your network and Tailscale connection.",
            ApiResult.Error(message = "socket", errorType = ApiErrorType.NETWORK_ERROR).toUiErrorState().message,
        )
    }

    @Test
    fun `an inventory call never blames the printer`() {
        val rejected = ApiResult.Error(message = "m", errorType = ApiErrorType.REJECTED).toUiErrorState(aboutPrinter = false)
        val down = ApiResult.Error(message = "m", errorType = ApiErrorType.UNAVAILABLE).toUiErrorState(aboutPrinter = false)
        assertEquals("Request Rejected", rejected.title)
        assertEquals("Service Unavailable", down.title)
        assertEquals(false, (rejected.message + down.message).contains("printer", ignoreCase = true))
        assertEquals(
            "Spool already exists",
            ApiResult.Error(message = "m", errorType = ApiErrorType.REJECTED, serverDetail = "Spool already exists")
                .toUiErrorState(aboutPrinter = false).message,
        )
    }
}
