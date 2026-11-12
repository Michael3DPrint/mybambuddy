package io.github.michael3dprint.mybambuddy.domain.policy

/** Warns when the API key/data would travel over plain http to a host that is not on a private network. */
object ServerUrlPolicy {
    /**
     * A warning when the address is plain http to a host outside a private network, otherwise null. https, private, local and
     * tailnet addresses and blank input give null.
     */
    fun warning(rawUrl: String): String? {
        val url = rawUrl.trim()
        if (url.isEmpty()) return null
        if (url.startsWith("https://", ignoreCase = true)) return null
        val host = url.removePrefix(
            "http://",
        ).removePrefix("HTTP://").substringBefore('/').substringBefore('?').substringBefore(':').lowercase()
        if (host.isEmpty() || isPrivate(host)) return null
        return "This address uses plain http outside a private network. Anyone on the path could read your data and API key. " +
            "Use https or your Tailscale address."
    }

    private fun isPrivate(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".ts.net") || host.endsWith(".local")) return true
        val parts = host.split('.')
        if (parts.size != 4) return !host.contains('.') // single-word names (docker/LAN) are local
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        val (a, b) = octets
        return a == 10 || a == 127 || (a == 192 && b == 168) || (a == 172 && b in 16..31) || (a == 100 && b in 64..127)
    }
}
