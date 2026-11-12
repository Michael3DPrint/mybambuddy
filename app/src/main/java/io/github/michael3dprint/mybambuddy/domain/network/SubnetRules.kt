package io.github.michael3dprint.mybambuddy.domain.network

/** The phone's own address on its network and the prefix length, for example 24 for a mask of 255.255.255.0. */
data class Subnet(val address: String, val prefix: Int)

/** Address maths for "Find Bambuddy" (design 47). Only private home-network ranges are ever listed. */
object SubnetRules {
    /** The most hosts one sweep lists: a /24. A larger network is cut down to the /24 around the phone's own address. */
    const val MAX_PREFIX_SPAN = 24

    /** Every other address on the phone's own private subnet, or an empty list when the address is not a private IPv4 one. */
    fun hosts(subnet: Subnet): List<String> {
        val ip = parse(subnet.address) ?: return emptyList()
        if (!isPrivate(ip) || subnet.prefix > 30 || subnet.prefix < 1) return emptyList()
        val prefix = maxOf(subnet.prefix, MAX_PREFIX_SPAN)
        val mask = (-1L shl (32 - prefix)) and 0xFFFFFFFFL
        val network = ip and mask
        val broadcast = network or (mask.inv() and 0xFFFFFFFFL)
        return ((network + 1) until broadcast).filter { it != ip }.map { format(it) }
    }

    // The top 10 bits of the Tailscale block (first octet 100, second octet 64), found by shifting right by 22.
    private const val CGNAT_TOP_BITS = (100L shl 24 or (64L shl 16)) shr 22

    /** True for a Tailscale address (the 100.64 slash 10 block) or a name ending in the Tailscale domain. */
    fun isTailnetHost(host: String): Boolean {
        if (host.endsWith(".ts.net")) return true
        val ip = parse(host) ?: return false
        return (ip shr 22) == CGNAT_TOP_BITS
    }

    /** The host part of a server address such as http://name:8000/, or an empty string when there is none. */
    fun hostOf(url: String): String = url.trim().removePrefix("http://").removePrefix("https://").substringBefore('/').substringBefore(':')

    /** True for a home-network (private) IPv4 address. */
    fun isPrivateHost(host: String): Boolean = parse(host)?.let { isPrivate(it) } ?: false

    private fun isPrivate(ip: Long): Boolean {
        val a = (ip shr 24).toInt()
        val b = ((ip shr 16) and 0xFF).toInt()
        return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
    }

    private fun parse(text: String): Long? {
        val parts = text.split(".")
        if (parts.size != 4) return null
        var value = 0L
        for (p in parts) {
            val n = p.toIntOrNull() ?: return null
            if (n !in 0..255) return null
            value = (value shl 8) or n.toLong()
        }
        return value
    }

    private fun format(ip: Long): String = "${ip shr 24 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 8 and 0xFF}.${ip and 0xFF}"
}
