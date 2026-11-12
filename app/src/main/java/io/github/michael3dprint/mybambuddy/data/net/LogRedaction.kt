package io.github.michael3dprint.mybambuddy.data.net

/** Removes secrets from log lines before they are written. */
object LogRedaction {
    private val TOKEN = Regex("([?&](?:token|key|api_key|access_code)=)[^&\\s]+", RegexOption.IGNORE_CASE)

    /** Hides token-like URL parameters (token, key, api_key, access_code) in a log line. */
    fun redact(line: String): String = TOKEN.replace(line) { it.groupValues[1] + "***" }
}
