package com.hostlocal

import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/** Answers mDNS queries for hostlocal.local on [address] and advertises the FTP service. */
class MdnsHost(private val address: String, private val port: Int = Settings.FTP_PORT) {
    private var jmdns: JmDNS? = null

    fun start() {
        // JmDNS appends ".local." to the name.
        val j = JmDNS.create(InetAddress.getByName(address), HOSTNAME)
        j.registerService(
            ServiceInfo.create("_ftp._tcp.local.", "HostLocal", port, "HostLocal FTP")
        )
        jmdns = j
    }

    fun stop() {
        jmdns?.let {
            runCatching { it.unregisterAllServices() }
            runCatching { it.close() }
        }
        jmdns = null
    }

    val boundAddress: String get() = address

    companion object {
        const val HOSTNAME = "hostlocal"
        const val FQDN = "$HOSTNAME.local"
    }
}
