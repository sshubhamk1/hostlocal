package com.hostlocal

import java.net.Inet4Address
import java.net.NetworkInterface

/** Interface name + its IPv4 addresses, decoupled from java.net for testing. */
data class IfaceInfo(val name: String, val ipv4: List<String>)

object Hotspot {
    // Typical tether/AP interface names across OEMs.
    private val AP_NAME = Regex("^(ap\\d*|swlan\\d*|softap\\d*|wlan[1-9]\\d*|rndis\\d*)$")

    // Client Wi-Fi / ethernet: used when no hotspot is active (phone joined a router).
    private val LAN_NAME = Regex("^(wlan0|eth\\d+)$")

    /** Address to serve on: hotspot if active, else the router LAN IP, else null. */
    fun pickAddress(ifaces: List<IfaceInfo>): String? =
        firstWithIp(ifaces, AP_NAME) ?: firstWithIp(ifaces, LAN_NAME)

    private fun firstWithIp(ifaces: List<IfaceInfo>, name: Regex): String? =
        ifaces.firstOrNull { name.matches(it.name) && it.ipv4.isNotEmpty() }?.ipv4?.first()

    fun currentAddress(): String? {
        val ifaces = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        } catch (e: Exception) {
            return null
        }
        return pickAddress(ifaces.filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .map { nic ->
                IfaceInfo(
                    nic.name,
                    nic.inetAddresses.toList()
                        .filterIsInstance<Inet4Address>()
                        .filter { it.isSiteLocalAddress }
                        .map { it.hostAddress!! },
                )
            })
    }
}
