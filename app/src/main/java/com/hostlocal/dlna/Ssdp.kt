package com.hostlocal.dlna

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Live counters so the UI can show whether other devices' searches reach us. */
object SsdpStats {
    @Volatile var searches = 0
        internal set
    @Volatile var replies = 0
        internal set
    @Volatile var lastFrom: String? = null
        internal set
    @Volatile var lastSt: String? = null
        internal set
    @Volatile var replyError: String? = null
        internal set

    /** Any SSDP datagram (searches or announcements), by sender IP. */
    val sources: MutableMap<String, Int> = java.util.concurrent.ConcurrentHashMap()

    fun reset() { searches = 0; replies = 0; lastFrom = null; lastSt = null; replyError = null; sources.clear() }
}

/** SSDP message building / matching, kept free of sockets for testing. */
object SsdpMessages {
    /** (NT/ST, USN) pairs this device advertises. */
    fun targets(uuid: String): List<Pair<String, String>> = listOf(
        "upnp:rootdevice" to "uuid:$uuid::upnp:rootdevice",
        "uuid:$uuid" to "uuid:$uuid",
        DlnaDescriptions.DEVICE_TYPE to "uuid:$uuid::${DlnaDescriptions.DEVICE_TYPE}",
        DlnaDescriptions.CD_TYPE to "uuid:$uuid::${DlnaDescriptions.CD_TYPE}",
        DlnaDescriptions.CM_TYPE to "uuid:$uuid::${DlnaDescriptions.CM_TYPE}",
    )

    /** Targets to answer for an M-SEARCH datagram; empty if not a matching search. */
    fun matchSearch(request: String, uuid: String): List<Pair<String, String>> {
        val lines = request.split("\r\n", "\n")
        if (lines.firstOrNull()?.startsWith("M-SEARCH", ignoreCase = true) != true) return emptyList()
        val st = lines.firstOrNull { it.startsWith("ST:", ignoreCase = true) }
            ?.substringAfter(':')?.trim() ?: return emptyList()
        val all = targets(uuid)
        return if (st == "ssdp:all") all else all.filter { it.first.equals(st, ignoreCase = true) }
    }

    private const val SERVER = "Android/13 UPnP/1.0 HostLocal/1.0"

    fun response(st: String, usn: String, location: String) =
        "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nEXT:\r\nLOCATION: $location\r\n" +
            "SERVER: $SERVER\r\nST: $st\r\nUSN: $usn\r\n\r\n"

    fun notify(nt: String, usn: String, location: String, alive: Boolean) =
        "NOTIFY * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" +
            (if (alive) "CACHE-CONTROL: max-age=1800\r\nLOCATION: $location\r\nSERVER: $SERVER\r\n" else "") +
            "NT: $nt\r\nNTS: ssdp:${if (alive) "alive" else "byebye"}\r\nUSN: $usn\r\n\r\n"
}

/** Joins the SSDP multicast group on the LAN interface owning [ip]; answers searches and announces. */
class SsdpAdvertiser(private val ip: String, private val uuid: String, private val location: String) {
    private var socket: MulticastSocket? = null
    private var notifier: ScheduledExecutorService? = null
    private val group = InetAddress.getByName("239.255.255.250")

    fun start() {
        SsdpStats.reset()
        val nif = NetworkInterface.getByInetAddress(InetAddress.getByName(ip))
            ?: error("No interface for $ip")
        val s = MulticastSocket(null as java.net.SocketAddress?).apply {
            reuseAddress = true
            bind(InetSocketAddress(1900))
            networkInterface = nif
            timeToLive = 2
            joinGroup(InetSocketAddress(group, 1900), nif)
        }
        socket = s

        Thread({
            val buf = ByteArray(2048)
            while (!s.isClosed) {
                try {
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    val req = String(p.data, 0, p.length)
                    p.address.hostAddress?.let { SsdpStats.sources.merge(it, 1, Int::plus) }
                    if (req.startsWith("M-SEARCH", ignoreCase = true)) {
                        SsdpStats.searches++
                        SsdpStats.lastFrom = p.address.hostAddress
                        SsdpStats.lastSt = req.lines().firstOrNull { it.startsWith("ST:", true) }?.substringAfter(':')?.trim()
                    }
                    for ((st, usn) in SsdpMessages.matchSearch(req, uuid)) {
                        val bytes = SsdpMessages.response(st, usn, location).toByteArray()
                        try {
                            s.send(DatagramPacket(bytes, bytes.size, p.address, p.port))
                            SsdpStats.replies++
                        } catch (e: Exception) {
                            SsdpStats.replyError = e.message ?: e.javaClass.simpleName
                        }
                    }
                } catch (e: Exception) {
                    if (s.isClosed) break
                }
            }
        }, "ssdp-listen").apply { isDaemon = true }.start()

        notifier = Executors.newSingleThreadScheduledExecutor().also {
            it.scheduleWithFixedDelay({ announce(true) }, 0, 60, TimeUnit.SECONDS)
        }
    }

    private fun announce(alive: Boolean) {
        val s = socket ?: return
        val bcast = broadcastAddress()
        for ((nt, usn) in SsdpMessages.targets(uuid)) {
            val bytes = SsdpMessages.notify(nt, usn, location, alive).toByteArray()
            runCatching { s.send(DatagramPacket(bytes, bytes.size, group, 1900)) }
            // Routers that filter multicast still flood subnet broadcasts.
            if (bcast != null) runCatching {
                DatagramSocket(InetSocketAddress(InetAddress.getByName(ip), 0)).use { b ->
                    b.broadcast = true
                    b.send(DatagramPacket(bytes, bytes.size, bcast, 1900))
                }
            }
        }
    }

    private fun broadcastAddress(): InetAddress? = runCatching {
        NetworkInterface.getByInetAddress(InetAddress.getByName(ip))
            .interfaceAddresses.firstOrNull { it.address.hostAddress == ip }?.broadcast
    }.getOrNull()

    fun stop() {
        notifier?.shutdownNow()
        announce(false)
        runCatching { socket?.close() }
        socket = null
        notifier = null
    }
}
