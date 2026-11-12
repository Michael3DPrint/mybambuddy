package io.github.michael3dprint.mybambuddy.domain.print

/** Compares printer model names written differently, such as a model ("X2D") and a printer preset ("Bambu Lab X2D 0.4 nozzle"). */
object ModelNames {
    private val nozzleText = Regex("""\d+(?:\.\d+)?\s*(?:mm)?\s*nozzle""")

    /** "Bambu Lab X2D 0.4 nozzle" and "X2D" both give "x2d"; null or blank gives an empty string. */
    fun normalize(model: String?): String =
        model.orEmpty().lowercase().replace(nozzleText, "").removePrefix("bambu lab").filter { it.isLetterOrDigit() }

    /** True only when both names are known and are the same model. */
    fun same(a: String?, b: String?): Boolean = normalize(a).isNotEmpty() && normalize(a) == normalize(b)
}
