package com.hostlocal.dlna

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream

/** Produces a cached JPEG thumbnail for a video, or null. */
fun interface ThumbProvider {
    fun jpeg(video: File): File?
}

/** UPnP description, ContentDirectory SOAP and Range-capable media streaming. */
class DlnaServer(
    ip: String,
    port: Int,
    private val root: File,
    private val uuid: String,
    private val friendlyName: String,
    private val thumbs: ThumbProvider?,
) : NanoHTTPD(ip, port) {
    private val baseUrl = "http://$ip:$port"
    private val didl = DidlBuilder(root, baseUrl, friendlyName, thumbs != null)

    override fun serve(session: IHTTPSession): Response {
        val path = session.uri
        return try {
            when {
                path == "/desc.xml" -> xml(DlnaDescriptions.device(uuid, friendlyName))
                path == "/cd/scpd.xml" -> xml(DlnaDescriptions.contentDirectoryScpd)
                path == "/cm/scpd.xml" -> xml(DlnaDescriptions.connectionManagerScpd)
                path == "/cd/control" && session.method == Method.POST -> contentDirectory(session)
                path == "/cm/control" && session.method == Method.POST -> connectionManager(session)
                path.startsWith("/media/") -> media(session, path.removePrefix("/media/"))
                path.startsWith("/thumb/") -> thumb(path.removePrefix("/thumb/"))
                else -> notFound()
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "error")
        }
    }

    private fun xml(body: String) =
        newFixedLengthResponse(Response.Status.OK, "text/xml; charset=\"utf-8\"", body)

    private fun notFound() = newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")

    // ---- SOAP ----

    private fun soapBody(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        session.parseBody(files)
        return files["postData"].orEmpty()
    }

    private fun soapAction(session: IHTTPSession): String =
        session.headers["soapaction"].orEmpty().trim('"').substringAfter('#')

    private fun arg(body: String, name: String): String =
        Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(body)
            ?.groupValues?.get(1)?.let(DlnaContent::unescapeXml).orEmpty()

    private fun soap(type: String, action: String, vararg out: Pair<String, String>): Response {
        val args = out.joinToString("") { "<${it.first}>${DlnaContent.escapeXml(it.second)}</${it.first}>" }
        return xml(
            """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:${action}Response xmlns:u="$type">$args</u:${action}Response></s:Body></s:Envelope>"""
        )
    }

    private fun soapFault(code: Int, text: String) = newFixedLengthResponse(
        Response.Status.INTERNAL_ERROR, "text/xml; charset=\"utf-8\"",
        """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail><UPnPError xmlns="urn:schemas-upnp-org:control-1-0"><errorCode>$code</errorCode><errorDescription>$text</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>""",
    )

    private fun contentDirectory(session: IHTTPSession): Response {
        val action = soapAction(session)
        val type = DlnaDescriptions.CD_TYPE
        return when (action) {
            "Browse" -> {
                val body = soapBody(session)
                val r = didl.browse(
                    arg(body, "ObjectID").ifEmpty { DlnaContent.ROOT_ID },
                    arg(body, "BrowseFlag"),
                    arg(body, "StartingIndex").toIntOrNull() ?: 0,
                    arg(body, "RequestedCount").toIntOrNull() ?: 0,
                ) ?: return soapFault(701, "No such object")
                soap(type, "Browse",
                    "Result" to r.didl, "NumberReturned" to "${r.returned}",
                    "TotalMatches" to "${r.total}", "UpdateID" to "0")
            }
            "GetSystemUpdateID" -> soap(type, action, "Id" to "0")
            "GetSearchCapabilities" -> soap(type, action, "SearchCaps" to "")
            "GetSortCapabilities" -> soap(type, action, "SortCaps" to "")
            else -> soapFault(401, "Invalid Action")
        }
    }

    private fun connectionManager(session: IHTTPSession): Response {
        val action = soapAction(session)
        val type = DlnaDescriptions.CM_TYPE
        return when (action) {
            "GetProtocolInfo" -> soap(type, action, "Source" to "http-get:*:video/mp4:*,http-get:*:video/x-matroska:*", "Sink" to "")
            "GetCurrentConnectionIDs" -> soap(type, action, "ConnectionIDs" to "0")
            "GetCurrentConnectionInfo" -> soap(type, action,
                "RcsID" to "-1", "AVTransportID" to "-1", "ProtocolInfo" to "",
                "PeerConnectionManager" to "", "PeerConnectionID" to "-1",
                "Direction" to "Output", "Status" to "OK")
            else -> soapFault(401, "Invalid Action")
        }
    }

    // ---- media ----

    private fun media(session: IHTTPSession, name: String): Response {
        val file = DlnaContent.resolve(root, name.substringBeforeLast('.'))
            ?.takeIf { it.isFile && DlnaContent.mimeFor(it) != null } ?: return notFound()
        val mime = DlnaContent.mimeFor(file)!!
        val size = file.length()

        val range = DlnaContent.parseRange(session.headers["range"], size)
        val resp = when (range) {
            is DlnaContent.RangeResult.Invalid -> newFixedLengthResponse(
                Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, ""
            ).apply { addHeader("Content-Range", "bytes */$size") }
            is DlnaContent.RangeResult.Partial -> {
                val len = range.end - range.start + 1
                newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, open(file, range.start), len)
                    .apply { addHeader("Content-Range", "bytes ${range.start}-${range.end}/$size") }
            }
            DlnaContent.RangeResult.Full ->
                newFixedLengthResponse(Response.Status.OK, mime, open(file, 0), size)
        }
        resp.addHeader("Accept-Ranges", "bytes")
        resp.addHeader("transferMode.dlna.org", "Streaming")
        resp.addHeader("contentFeatures.dlna.org",
            "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000")
        return resp
    }

    private fun open(file: File, start: Long) = FileInputStream(file).also {
        if (start > 0) it.channel.position(start)
    }

    private fun thumb(name: String): Response {
        val video = DlnaContent.resolve(root, name.substringBeforeLast('.'))
            ?.takeIf { it.isFile && DlnaContent.mimeFor(it) != null } ?: return notFound()
        val jpg = thumbs?.jpeg(video) ?: return notFound()
        return newFixedLengthResponse(Response.Status.OK, "image/jpeg", FileInputStream(jpg), jpg.length())
    }
}
