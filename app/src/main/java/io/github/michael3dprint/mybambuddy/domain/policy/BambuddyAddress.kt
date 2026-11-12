package io.github.michael3dprint.mybambuddy.domain.policy

import java.net.URI

/** Works out the address of Bambuddy's own web page from the saved server address (which may end in `/api/v1`). */
object BambuddyAddress {
    private val apiSuffixes = listOf("/api/v1", "/api")

    /**
     * The web address to open in a browser, or null when [serverUrl] is blank or not an http or https address. A missing scheme is taken as
     * http (as the app does when it connects), trailing slashes and a trailing API path are removed, and any query or fragment is dropped.
     */
    fun webAddress(serverUrl: String): String? {
        val trimmed = serverUrl.trim()
        if (trimmed.isEmpty()) return null
        val explicitScheme = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://").find(trimmed)?.groupValues?.get(1)?.lowercase()
        // An address that names some other scheme is never turned into an http one.
        if (explicitScheme != null && explicitScheme != "http" && explicitScheme != "https") return null
        val withScheme = if (explicitScheme != null) trimmed else "http://$trimmed"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        val host = uri.host
        if ((scheme != "http" && scheme != "https") || host.isNullOrEmpty()) return null
        var path = uri.rawPath.orEmpty().trimEnd('/')
        apiSuffixes.firstOrNull { path.endsWith(it) }?.let { path = path.removeSuffix(it).trimEnd('/') }
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        return "$scheme://$host$port$path"
    }
}
