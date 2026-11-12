package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.HmsStatus

/** The printer's HMS errors counted by level, so words and colours follow how serious they are instead of calling every entry an error (design 43). */
object HmsSummary {
    /** The most serious level present, or null when there are no errors. */
    fun worst(errors: List<HmsStatus>): HmsSeverity? = errors.map { HmsExplainer.severityOf(it) }.minOrNull()

    /** "1 error", "2 warnings", "1 notice", or a mix such as "1 error, 2 notices"; empty when there are none. */
    fun words(errors: List<HmsStatus>): String {
        val levels = errors.map { HmsExplainer.severityOf(it) }
        return listOf(
            HmsSeverity.ERROR to ("error" to "errors"),
            HmsSeverity.WARNING to ("warning" to "warnings"),
            HmsSeverity.NOTICE to ("notice" to "notices"),
        ).mapNotNull { (level, names) ->
            val n = levels.count { it == level }
            if (n == 0) null else "$n ${if (n == 1) names.first else names.second}"
        }.joinToString(", ")
    }
}
