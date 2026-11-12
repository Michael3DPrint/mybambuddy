package io.github.michael3dprint.mybambuddy.ui.filament

import io.github.michael3dprint.mybambuddy.domain.model.AmsUnit
import io.github.michael3dprint.mybambuddy.domain.model.Spool
import io.github.michael3dprint.mybambuddy.domain.policy.SlotLoadAssessment

/** An assignment the user has asked for and still has to confirm. */
data class PendingAssignment(val spool: Spool, val unit: AmsUnit, val trayId: Int, val assessment: SlotLoadAssessment)
