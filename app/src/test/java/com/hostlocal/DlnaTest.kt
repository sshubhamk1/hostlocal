package com.hostlocal

import com.hostlocal.dlna.DidlBuilder
import com.hostlocal.dlna.DlnaContent
import com.hostlocal.dlna.DlnaContent.RangeResult
import com.hostlocal.dlna.DlnaServer
import com.hostlocal.dlna.SsdpMessages
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class DlnaTest {
    private lateinit var dir: File
    private lateinit var server: DlnaServer
    private val port = 8209
    private val uuid = "11111111-2222-3333-4444-555555555555"
    private val payload = ByteArray(1000) { (it % 251).toByte() }

    @Before fun setUp() {
        dir = createTempDir("dlna")
        File(dir, "movie.mp4").writeBytes(payload)
        File(dir, "notes.txt").writeText("skip me")
        File(dir, ".hidden.mp4").writeBytes(payload)
        File(dir, "Season 1").mkdir()
        File(dir, "Season 1/ep & one.mkv").writeBytes(payload)
        server = DlnaServer("127.0.0.1", port, dir, uuid, "HostLocal", null).also { it.start(5000, false) }
    }

    @After fun tearDown() {
        server.stop()
        dir.deleteRecursively()
    }

    private fun open(path: String, range: String? = null): HttpURLConnection =
        (URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection).apply {
            range?.let { setRequestProperty("Range", it) }
        }

    private fun browse(id: String, flag: String = "BrowseDirectChildren", start: Int = 0, count: Int = 0): String {
        val c = open("/cd/control")
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        c.setRequestProperty("SOAPACTION", "\"urn:schemas-upnp-org:service:ContentDirectory:1#Browse\"")
        c.outputStream.use {
            it.write("""<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:Browse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1"><ObjectID>$id</ObjectID><BrowseFlag>$flag</BrowseFlag><Filter>*</Filter><StartingIndex>$start</StartingIndex><RequestedCount>$count</RequestedCount><SortCriteria></SortCriteria></u:Browse></s:Body></s:Envelope>""".toByteArray())
        }
        return c.inputStream.bufferedReader().readText()
    }

    @Test fun descriptionServed() {
        val body = open("/desc.xml").inputStream.bufferedReader().readText()
        assertTrue(body.contains("MediaServer:1"))
        assertTrue(body.contains("uuid:$uuid"))
    }

    @Test fun browseRootListsFoldersAndVideosOnly() {
        val r = browse("0")
        assertTrue(r.contains("Season 1"))
        assertTrue(r.contains("movie"))
        assertFalse(r.contains("notes"))
        assertFalse(r.contains("hidden"))
        assertTrue(r.contains("<TotalMatches>2</TotalMatches>"))
    }

    @Test fun browsePaging() {
        val r = browse("0", start = 1, count = 1)
        assertTrue(r.contains("<NumberReturned>1</NumberReturned>"))
        assertTrue(r.contains("<TotalMatches>2</TotalMatches>"))
    }

    @Test fun browseSubfolderEscapesTitle() {
        val id = DlnaContent.idFor(dir, File(dir, "Season 1"))
        val r = browse(id)
        assertTrue(r.contains("ep &amp;amp; one")) // escaped once in DIDL, again inside <Result>
    }

    @Test fun browseUnknownObjectFaults() {
        try {
            browse("bm90LXRoZXJl")
            org.junit.Assert.fail("expected fault")
        } catch (e: java.io.IOException) {
            // 500 with UPnP 701
        }
    }

    @Test fun fullDownload() {
        val c = open("/media/${DlnaContent.idFor(dir, File(dir, "movie.mp4"))}.mp4")
        assertEquals(200, c.responseCode)
        assertEquals("bytes", c.getHeaderField("Accept-Ranges"))
        assertTrue(c.inputStream.readBytes().contentEquals(payload))
    }

    @Test fun rangeRequestReturns206() {
        val c = open("/media/${DlnaContent.idFor(dir, File(dir, "movie.mp4"))}.mp4", "bytes=100-199")
        assertEquals(206, c.responseCode)
        assertEquals("bytes 100-199/1000", c.getHeaderField("Content-Range"))
        assertTrue(c.inputStream.readBytes().contentEquals(payload.copyOfRange(100, 200)))
    }

    @Test fun openEndedRange() {
        val c = open("/media/${DlnaContent.idFor(dir, File(dir, "movie.mp4"))}.mp4", "bytes=900-")
        assertEquals(206, c.responseCode)
        assertTrue(c.inputStream.readBytes().contentEquals(payload.copyOfRange(900, 1000)))
    }

    @Test fun unsatisfiableRange() {
        val c = open("/media/${DlnaContent.idFor(dir, File(dir, "movie.mp4"))}.mp4", "bytes=5000-")
        assertEquals(416, c.responseCode)
    }

    @Test fun traversalRejected() {
        val outside = File(dir.parentFile, "secret-${System.nanoTime()}.mp4").apply { writeBytes(payload) }
        try {
            val id = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("../${outside.name}".toByteArray())
            assertEquals(404, open("/media/$id.mp4").responseCode)
            assertNull(DlnaContent.resolve(dir, id))
        } finally {
            outside.delete()
        }
    }

    @Test fun nonVideoNotServed() {
        val id = DlnaContent.idFor(dir, File(dir, "notes.txt"))
        assertEquals(404, open("/media/$id.txt").responseCode)
    }

    @Test fun idRoundTrip() {
        val f = File(dir, "Season 1/ep & one.mkv")
        assertEquals(f.canonicalFile, DlnaContent.resolve(dir, DlnaContent.idFor(dir, f)))
        assertEquals(DlnaContent.ROOT_ID, DlnaContent.idFor(dir, dir))
    }

    @Test fun rangeParsing() {
        assertEquals(RangeResult.Full, DlnaContent.parseRange(null, 100))
        assertEquals(RangeResult.Partial(0, 9), DlnaContent.parseRange("bytes=0-9", 100))
        assertEquals(RangeResult.Partial(90, 99), DlnaContent.parseRange("bytes=-10", 100))
        assertEquals(RangeResult.Partial(50, 99), DlnaContent.parseRange("bytes=50-", 100))
        assertEquals(RangeResult.Partial(50, 99), DlnaContent.parseRange("bytes=50-5000", 100))
        assertEquals(RangeResult.Invalid, DlnaContent.parseRange("bytes=100-", 100))
        assertEquals(RangeResult.Invalid, DlnaContent.parseRange("bytes=20-10", 100))
    }

    @Test fun didlItemHasResAndSize() {
        val r = DidlBuilder(dir, "http://h:8200", "HostLocal", false).browse("0", "BrowseDirectChildren", 0, 0)
        assertNotNull(r)
        assertTrue(r!!.didl.contains("size=\"1000\""))
        assertTrue(r.didl.contains("video/mp4"))
    }

    @Test fun ssdpMatching() {
        val all = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n"
        assertEquals(5, SsdpMessages.matchSearch(all, uuid).size)
        val ms = all.replace("ssdp:all", "urn:schemas-upnp-org:device:MediaServer:1")
        assertEquals(1, SsdpMessages.matchSearch(ms, uuid).size)
        assertTrue(SsdpMessages.matchSearch(all.replace("ssdp:all", "urn:other:thing:1"), uuid).isEmpty())
        assertTrue(SsdpMessages.matchSearch("NOTIFY * HTTP/1.1\r\n\r\n", uuid).isEmpty())
        val resp = SsdpMessages.response("upnp:rootdevice", "uuid:x::upnp:rootdevice", "http://1.2.3.4:8200/desc.xml")
        assertTrue(resp.startsWith("HTTP/1.1 200 OK"))
        assertTrue(resp.contains("LOCATION: http://1.2.3.4:8200/desc.xml"))
    }

    @Test fun subnetSweep24() {
        val me = java.net.InetAddress.getByName("192.168.1.14")
        val hosts = SsdpMessages.hostsIn(me, 24).map { it.hostAddress }
        assertEquals(253, hosts.size)
        assertFalse(hosts.contains("192.168.1.14"))
        assertFalse(hosts.contains("192.168.1.0"))
        assertFalse(hosts.contains("192.168.1.255"))
        assertTrue(hosts.contains("192.168.1.1"))
        assertTrue(hosts.contains("192.168.1.254"))
    }

    @Test fun subnetSweepCappedForBigNetworks() {
        val hosts = SsdpMessages.hostsIn(java.net.InetAddress.getByName("10.5.77.9"), 8)
        assertEquals(1021, hosts.size) // /22 -> 1024 - net - bcast - self
        assertTrue(hosts.all { it.hostAddress!!.startsWith("10.5.") })
    }

    @Test fun subnetSweepHotspotStyle() {
        val hosts = SsdpMessages.hostsIn(java.net.InetAddress.getByName("192.168.43.1"), 24).map { it.hostAddress }
        assertEquals(253, hosts.size)
        assertTrue(hosts.contains("192.168.43.2"))
    }
}
