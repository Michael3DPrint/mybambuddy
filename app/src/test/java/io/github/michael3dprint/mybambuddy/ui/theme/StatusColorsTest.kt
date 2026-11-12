package io.github.michael3dprint.mybambuddy.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.michael3dprint.mybambuddy.domain.model.PrinterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusColorsTest {
    /** WCAG contrast ratio between two colours. */
    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance().toDouble()
        val l2 = b.luminance().toDouble()
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun check(name: String, colors: StatusColors) {
        PrinterState.entries.forEach { state ->
            val chip = colors.forState(state)
            val ratio = contrast(chip.container, chip.content)
            assertTrue("$name $state contrast $ratio is below WCAG AA 4.5", ratio >= 4.5)
        }
    }

    @Test
    fun `light status colours meet WCAG AA`() = check("light", StatusColors.Light)

    @Test
    fun `dark status colours meet WCAG AA`() = check("dark", StatusColors.Dark)

    @Test
    fun `every state has a colour pair and light differs from dark`() {
        PrinterState.entries.forEach { assertTrue(StatusColors.Light.forState(it) != StatusColors.Dark.forState(it)) }
        assertEquals(StatusColors.Light.printing, StatusColors.Light.forState(PrinterState.PRINTING))
    }

    @Test
    fun `brand colour pairs meet WCAG AA in both themes`() {
        listOf(
            TealPrimaryLight to TealOnPrimaryLight, TealContainerLight to TealOnContainerLight,
            SlateContainerLight to SlateOnContainerLight, AmberContainerLight to AmberOnContainerLight,
            SurfaceLight to OnSurfaceLight, SurfaceVariantLight to OnSurfaceVariantLight,
            TealPrimaryDark to TealOnPrimaryDark, TealContainerDark to TealOnContainerDark,
            SlateContainerDark to SlateOnContainerDark, AmberContainerDark to AmberOnContainerDark,
            SurfaceDark to OnSurfaceDark, SurfaceVariantDark to OnSurfaceVariantDark,
        ).forEach { (bg, fg) -> assertTrue("contrast ${contrast(bg, fg)}", contrast(bg, fg) >= 4.5) }
    }
}
