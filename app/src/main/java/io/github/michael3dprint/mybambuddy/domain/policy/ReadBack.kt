package io.github.michael3dprint.mybambuddy.domain.policy

/** What the slot reports after an assignment, compared with the spool that was assigned. */
enum class ReadBack {
    /** Material and colour match what was assigned. */
    CONFIRMED,

    /** The slot still reports something else (or nothing). The printer may have refused the change. */
    DIFFERENT,
}
