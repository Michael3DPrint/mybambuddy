package io.github.michael3dprint.mybambuddy.domain.policy

/** What the confirmation dialog shows: what the printer may be set to, and the warnings that apply. */
data class SlotLoadAssessment(val pushSummary: String, val warnings: List<SlotWarning>)
