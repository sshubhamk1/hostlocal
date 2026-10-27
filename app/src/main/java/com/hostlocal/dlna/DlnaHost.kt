package com.hostlocal.dlna

import com.hostlocal.Settings
import fi.iki.elonen.NanoHTTPD
import java.io.File

/** DLNA media server: HTTP (description, browse, streaming) + SSDP discovery. */
class DlnaHost(
    private val root: File,
    private val ip: String,
    private val uuid: String,
    private val thumbs: ThumbProvider?,
    private val port: Int = Settings.DLNA_PORT,
) {
    private var http: DlnaServer? = null
    private var ssdp: SsdpAdvertiser? = null

    fun start() {
        val server = DlnaServer(ip, port, root, uuid, Settings.DLNA_NAME, thumbs)
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        http = server
        try {
            ssdp = SsdpAdvertiser(ip, uuid, "http://$ip:$port/desc.xml").also { it.start() }
        } catch (e: Exception) {
            stop()
            throw e
        }
    }

    fun stop() {
        runCatching { ssdp?.stop() }
        runCatching { http?.stop() }
        ssdp = null
        http = null
    }
}
