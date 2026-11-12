package io.github.michael3dprint.mybambuddy.platform.network

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import io.github.michael3dprint.mybambuddy.domain.network.NetworkInfo
import io.github.michael3dprint.mybambuddy.domain.network.Subnet
import java.net.Inet4Address

/** Reads the phone's network state with Android's connectivity and package services. Needs only the network-state permission. */
class AndroidNetworkInfo(private val context: Context) : NetworkInfo {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    // allNetworks is deprecated, but it is the way to find the Wi-Fi network while a VPN is the active one.
    @Suppress("DEPRECATION")
    override fun wifiSubnet(): Subnet? = try {
        connectivity?.allNetworks?.firstNotNullOfOrNull { network ->
            val wifi = connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val address = connectivity.getLinkProperties(network)?.linkAddresses?.firstOrNull { it.address is Inet4Address }
            if (wifi && address != null) Subnet(address.address.hostAddress.orEmpty(), address.prefixLength) else null
        }
    } catch (_: SecurityException) {
        null
    }

    override fun vpnActive(): Boolean? = try {
        val network = connectivity?.activeNetwork
        network?.let { connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }
    } catch (_: SecurityException) {
        null
    }

    override fun isInstalled(packageName: String): Boolean? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: RuntimeException) {
        null
    }
}
