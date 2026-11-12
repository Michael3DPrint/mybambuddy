package io.github.michael3dprint.mybambuddy.domain.network

/** What the phone can say about Tailscale and the server (design 47). It never says "Tailscale is on". */
enum class NetworkVerdict(val text: String) {
    NOT_NEEDED("Not needed: this is a home-network address."),
    TAILSCALE_NOT_INSTALLED("The Tailscale app is not installed on this phone."),
    INSTALLED_NO_VPN("The Tailscale app is installed, but no VPN is on."),
    VPN_REACHABLE("A VPN is on and the server answers."),
    VPN_UNREACHABLE("A VPN is on but the server does not answer. Check Tailscale on the server and its approved routes."),
    COULD_NOT_CHECK("Cannot tell from this address whether Tailscale is needed."),
}

/** Reads the phone's own network state: its subnet, whether a VPN is on, and whether an app is installed. Null means it could not tell. */
interface NetworkInfo {
    /** The Wi-Fi address and prefix, or null when not on Wi-Fi or unreadable. */
    fun wifiSubnet(): Subnet?

    /** True when the active network is a VPN, null when unreadable. */
    fun vpnActive(): Boolean?

    /** True when the app with [packageName] is installed, null when it could not be checked. */
    fun isInstalled(packageName: String): Boolean?
}

/** The decision table behind the verdict words, pure so every combination is tested. */
object NetworkVerdictRule {
    /** The Android package name of the Tailscale app, as shown by its store listings (not from Tailscale's own documentation). */
    const val TAILSCALE_PACKAGE = "com.tailscale.ipn"

    /** The verdict for a server at [host], given what the phone could see. A null input means that check could not be made. */
    fun decide(host: String, tailscaleInstalled: Boolean?, vpnActive: Boolean?, reachable: Boolean?): NetworkVerdict = when {
        SubnetRules.isPrivateHost(host) -> NetworkVerdict.NOT_NEEDED
        !SubnetRules.isTailnetHost(host) -> NetworkVerdict.COULD_NOT_CHECK
        tailscaleInstalled == false -> NetworkVerdict.TAILSCALE_NOT_INSTALLED
        tailscaleInstalled == null || vpnActive == null -> NetworkVerdict.COULD_NOT_CHECK
        !vpnActive -> NetworkVerdict.INSTALLED_NO_VPN
        reachable == true -> NetworkVerdict.VPN_REACHABLE
        reachable == false -> NetworkVerdict.VPN_UNREACHABLE
        else -> NetworkVerdict.COULD_NOT_CHECK
    }
}
