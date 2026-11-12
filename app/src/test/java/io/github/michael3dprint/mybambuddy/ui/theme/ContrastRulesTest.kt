package io.github.michael3dprint.mybambuddy.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Turns the colour-contrast rules into a build check (design 34): text needs 4.5:1 against its background and
 * the outline that marks a widget box needs 3:1 (WCAG 2.1 AA). A colour change that drops below fails the build.
 */
class ContrastRulesTest {
    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun assertText(name: String, text: Color, background: Color) {
        val r = ratio(text, background)
        assertTrue("$name has contrast ${"%.2f".format(r)}:1, below 4.5:1", r >= TEXT)
    }

    @Test
    fun `theme text and its backgrounds reach 4_5 to 1 in light and dark`() {
        assertText("light text on surface", OnSurfaceLight, SurfaceLight)
        assertText("light variant text on surface", OnSurfaceVariantLight, SurfaceLight)
        assertText("light primary on surface", TealPrimaryLight, SurfaceLight)
        assertText("light onPrimary on primary", TealOnPrimaryLight, TealPrimaryLight)
        assertText("light on container", TealOnContainerLight, TealContainerLight)
        assertText("light tertiary on surface", AmberTertiaryLight, SurfaceLight)
        assertText("dark text on surface", OnSurfaceDark, SurfaceDark)
        assertText("dark variant text on surface", OnSurfaceVariantDark, SurfaceDark)
        assertText("dark primary on surface", TealPrimaryDark, SurfaceDark)
        assertText("dark onPrimary on primary", TealOnPrimaryDark, TealPrimaryDark)
        assertText("dark on container", TealOnContainerDark, TealContainerDark)
        assertText("dark tertiary on surface", AmberTertiaryDark, SurfaceDark)
    }

    @Test
    fun `every printer state chip reaches 4_5 to 1 in light and dark`() {
        listOf("light" to StatusColors.Light, "dark" to StatusColors.Dark).forEach { (mode, colors) ->
            listOf(
                "printing" to colors.printing,
                "paused" to colors.paused,
                "finished" to colors.finished,
                "error" to colors.error,
                "offline" to colors.offline,
                "unknown" to colors.unknown,
            ).forEach { (name, chip) -> assertText("$mode $name chip", chip.content, chip.container) }
        }
    }

    @Test
    fun `widget text reaches 4_5 to 1 and the box outline 3 to 1 in light and dark`() {
        listOf("values" to "light", "values-night" to "dark").forEach { (dir, mode) ->
            val c = widgetColors(dir)
            assertText("widget $mode text on background", c.getValue("widget_text"), c.getValue("widget_background"))
            assertText("widget $mode subtext on background", c.getValue("widget_subtext"), c.getValue("widget_background"))
            assertText("widget $mode text on row", c.getValue("widget_text"), c.getValue("widget_row_fill"))
            assertText("widget $mode subtext on row", c.getValue("widget_subtext"), c.getValue("widget_row_fill"))
            val outline = ratio(c.getValue("widget_row_stroke"), c.getValue("widget_background"))
            assertTrue("widget $mode box outline has contrast ${"%.2f".format(outline)}:1, below 3:1", outline >= BOUNDARY)
        }
    }

    @Test
    fun `status icons reach 3 to 1 on the card surfaces in light and dark`() {
        // Card surfaces: the default card container (surfaceContainerHighest) and the page surface, as defined in Theme.kt.
        val surfaces = mapOf("light" to listOf(SurfaceLight, Color(0xFFDEE3E5)), "dark" to listOf(SurfaceDark, Color(0xFF303738)))
        listOf("light" to StatusColors.Light, "dark" to StatusColors.Dark).forEach { (mode, c) ->
            listOf("ok" to c.printing.content, "warn" to c.paused.content, "error" to c.error.content).forEach { (name, tint) ->
                surfaces.getValue(mode).forEach { bg ->
                    val r = ratio(tint, bg)
                    assertTrue("$mode $name status icon has contrast ${"%.2f".format(r)}:1, below 3:1", r >= BOUNDARY)
                }
            }
        }
    }

    private fun widgetColors(dir: String): Map<String, Color> {
        val file = listOf("src/main/res/$dir/colors.xml", "app/src/main/res/$dir/colors.xml").map(::File).first { it.exists() }
        return Regex("""<color name="(widget_\w+)">#([0-9A-Fa-f]{8})</color>""").findAll(file.readText()).associate {
            it.groupValues[1] to Color(it.groupValues[2].toLong(16))
        }
    }

    private companion object {
        const val TEXT = 4.5
        const val BOUNDARY = 3.0
    }
}
