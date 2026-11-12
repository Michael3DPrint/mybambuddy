package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.AmsTray

/**
 * Turns display text into what a screen reader should say (design 34). Screen readers read symbols literally ("tilde", "em dash",
 * "droplet"), so lines built from them get a spoken form: "~750 g" is "about 750 grams", "45 %" is "45 percent", "21.0 °C" is "21.0 degrees
 * Celsius", a lone dash is "unknown", and the middle dot between facts becomes a pause. Pure, so it is tested without a device.
 */
object SpokenText {
    private val grams = Regex("(\\d)\\s?g\\b")
    private val percent = Regex("(\\d)\\s?%")
    private val celsius = Regex("\\s?°C")
    private val lead = Regex("^\\s*•\\s*", RegexOption.MULTILINE)

    /** The spoken form of [display]. */
    fun of(display: String): String {
        val trimmed = display.trim()
        if (trimmed == "—" || trimmed == "–" || trimmed == "-") return "unknown"
        return display
            .replace("~", "about ")
            .replace("⚠", "warning ")
            .replace("💧", "humidity ")
            .replace("🌡", "temperature ")
            .replace("→", " to ")
            .replace(" — ", ", ")
            .replace(" · ", ", ")
            .replace(lead, "")
            .replace(grams, "$1 grams")
            .replace(percent, "$1 percent")
            .replace(celsius, " degrees Celsius")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }
}

/** The one description a screen reader gets for an AMS slot tile (design 34). */
object SlotSpeech {
    /** For example "AMS 1, slot 3, PLA Basic, about 750 grams left", with ", low" when low, or "AMS 1, slot 3, empty". */
    fun describe(unitName: String, tray: AmsTray, fill: SlotFill?, low: Boolean): String {
        val where = "$unitName, slot ${tray.trayId + 1}"
        if (tray.isEmpty) return "$where, empty"
        val amount = fill?.let { "about ${it.grams} grams left" } ?: "amount unknown"
        return listOfNotNull(where, tray.label, amount, "low".takeIf { low }).joinToString(", ")
    }
}
