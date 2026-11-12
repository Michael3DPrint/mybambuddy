package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.data.api.ApiResult
import io.github.michael3dprint.mybambuddy.domain.model.QueueItem
import io.github.michael3dprint.mybambuddy.domain.model.QueueStatus
import io.github.michael3dprint.mybambuddy.domain.repo.QueueRepository
import kotlinx.coroutines.delay

/**
 * What became of a job after Bambuddy accepted a start. A start only tells Bambuddy to let the scheduler send the job; whether the printer
 * takes it shows up afterwards on the job itself (for example "refused to store the file"), so the app looks again instead of claiming success.
 */
sealed interface StartVerdict {
    /** The job is printing (or has finished). */
    data object Printing : StartVerdict

    /** Still waiting with no reason given yet. */
    data object Waiting : StartVerdict

    /** Waiting, with Bambuddy's reason the printer has not started; Bambuddy may retry by itself. */
    data class Blocked(val reason: String) : StartVerdict

    /** The job failed, was skipped or cancelled, with Bambuddy's reason. */
    data class Failed(val reason: String) : StartVerdict

    /** The job is no longer in the queue. */
    data object Gone : StartVerdict
}

/** Reads a job into a [StartVerdict] and says it in words. Pure, so it is tested without a server. */
object StartCheck {
    /** The verdict for [item] (null when the job is not in the queue). */
    fun verdict(item: QueueItem?): StartVerdict = when {
        item == null -> StartVerdict.Gone
        item.status == QueueStatus.PRINTING || item.status == QueueStatus.COMPLETED -> StartVerdict.Printing
        item.status == QueueStatus.FAILED || item.status == QueueStatus.SKIPPED || item.status == QueueStatus.CANCELLED ->
            StartVerdict.Failed(item.errorMessage ?: item.waitingReason ?: "Bambuddy gave no reason.")
        !item.waitingReason.isNullOrBlank() -> StartVerdict.Blocked(item.waitingReason)
        else -> StartVerdict.Waiting
    }

    /** What to tell the user, naming the printer. */
    fun words(verdict: StartVerdict, printerName: String): String = when (verdict) {
        StartVerdict.Printing -> "Printing has started on $printerName."
        StartVerdict.Waiting -> "Bambuddy accepted the start, but $printerName has not started yet. Check the queue."
        is StartVerdict.Blocked -> "It won't print yet. Bambuddy accepted the start, but ${verdict.reason}"
        is StartVerdict.Failed -> "It won't print. The job could not start: ${verdict.reason}"
        StartVerdict.Gone -> "The job is no longer in the queue. Check Bambuddy to see what happened."
    }

    /** True when the verdict means the print is not going ahead as it stands. */
    fun isProblem(verdict: StartVerdict): Boolean =
        verdict is StartVerdict.Blocked || verdict is StartVerdict.Failed || verdict == StartVerdict.Gone
}

/** Looks at a started job every [pollMs] until it prints, fails, shows the same blocking reason [blockedStreak] times, or [maxPolls] looks pass. */
class StartWatcher(
    private val queue: QueueRepository,
    private val pollMs: Long = 2_000L,
    private val maxPolls: Int = 15,
    private val blockedStreak: Int = 3,
) {
    /** The final verdict for job [itemId]; a failed read of the queue counts as no news, and the last known verdict is returned at the end. */
    suspend fun watch(itemId: Int): StartVerdict {
        var last: StartVerdict = StartVerdict.Waiting
        var streak = 0
        repeat(maxPolls) {
            val items = (queue.getQueue() as? ApiResult.Success)?.data
            if (items != null) {
                val verdict = StartCheck.verdict(items.firstOrNull { it.id == itemId })
                streak =
                    if (verdict is StartVerdict.Blocked && verdict == last) {
                        streak + 1
                    } else if (verdict is StartVerdict.Blocked) {
                        1
                    } else {
                        0
                    }
                last = verdict
                if (verdict == StartVerdict.Printing || verdict is StartVerdict.Failed || verdict == StartVerdict.Gone) return verdict
                if (streak >= blockedStreak) return verdict
            }
            delay(pollMs)
        }
        return last
    }
}
