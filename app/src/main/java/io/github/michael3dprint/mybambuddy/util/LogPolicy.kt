package io.github.michael3dprint.mybambuddy.util

/**
 * Whether detailed logging is allowed (design 19). Off by default, so anything that forgets to set it is safe; the Application
 * turns it on only for a debuggable build. When off, logs carry the message and the exception type only: no server text, no stack trace.
 */
object LogPolicy {
    @Volatile
    var verbose: Boolean = false
}
