package io.github.michael3dprint.mybambuddy.util

import android.util.Log

/** Where log lines end up. Swapped in tests; the default writes to logcat, or to the console when logcat is unavailable (JVM tests). */
internal fun interface LogSink {
    fun write(level: Char, tag: String, message: String, throwable: Throwable?)
}

/**
 * The app's logger. With [LogPolicy.verbose] off (release builds) it writes error and warning messages with the exception's type
 * only, and drops debug and info lines, so no stack trace or server-supplied text reaches logcat.
 */
object PrintDeckLogger {
    private const val DEFAULT_TAG = "PrintDeckTrace"

    internal var sink: LogSink = LogSink { level, tag, message, throwable -> writeToLogcat(level, tag, message, throwable) }

    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) = emit('E', tag, message, throwable)

    fun w(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) = emit('W', tag, message, throwable)

    fun d(tag: String = DEFAULT_TAG, message: String) {
        if (LogPolicy.verbose) sink.write('D', tag, message, null)
    }

    fun i(tag: String = DEFAULT_TAG, message: String) {
        if (LogPolicy.verbose) sink.write('I', tag, message, null)
    }

    private fun emit(level: Char, tag: String, message: String, throwable: Throwable?) {
        if (LogPolicy.verbose) {
            sink.write(level, tag, message, throwable)
        } else {
            sink.write(level, tag, if (throwable == null) message else "$message (${throwable.javaClass.simpleName})", null)
        }
    }

    private fun writeToLogcat(level: Char, tag: String, message: String, throwable: Throwable?) {
        try {
            when (level) {
                'E' -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
                'W' -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
                'I' -> Log.i(tag, message)
                else -> Log.d(tag, message)
            }
        } catch (_: Exception) {
            // JVM fallback for unit tests where android.util.Log is not mocked
            System.err.println("[$tag] $level: $message")
            throwable?.printStackTrace(System.err)
        }
    }
}
