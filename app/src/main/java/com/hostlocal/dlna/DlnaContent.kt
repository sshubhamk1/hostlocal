package com.hostlocal.dlna

import java.io.File
import java.util.Base64

/** Object-id mapping, folder listing and DIDL-Lite generation for the ContentDirectory. */
object DlnaContent {
    const val ROOT_ID = "0"

    private val VIDEO_MIME = mapOf(
        "mp4" to "video/mp4", "m4v" to "video/mp4", "mkv" to "video/x-matroska",
        "avi" to "video/x-msvideo", "mov" to "video/quicktime", "webm" to "video/webm",
        "ts" to "video/mp2t", "m2ts" to "video/mp2t", "wmv" to "video/x-ms-wmv",
        "mpg" to "video/mpeg", "mpeg" to "video/mpeg", "3gp" to "video/3gpp",
        "flv" to "video/x-flv",
    )

    fun mimeFor(file: File): String? = VIDEO_MIME[file.extension.lowercase()]

    // Ids are url-safe base64 of the path relative to root; stateless and reversible.
    fun idFor(root: File, file: File): String {
        val rel = file.canonicalFile.relativeTo(root.canonicalFile).invariantSeparatorsPath
        return if (rel.isEmpty() || rel == ".") ROOT_ID
        else Base64.getUrlEncoder().withoutPadding().encodeToString(rel.toByteArray())
    }

    /** Resolves an id to a visible file strictly inside [root], or null. */
    fun resolve(root: File, id: String): File? {
        val rootCanon = root.canonicalFile
        if (id == ROOT_ID) return rootCanon
        val rel = try {
            String(Base64.getUrlDecoder().decode(id))
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (rel.split('/').any { it.startsWith(".") }) return null // hidden or ".."
        val f = File(rootCanon, rel).canonicalFile
        val inside = f.path == rootCanon.path || f.path.startsWith(rootCanon.path + File.separator)
        return if (inside && f.exists()) f else null
    }

    fun children(dir: File): List<File> =
        dir.listFiles().orEmpty()
            .filter { !it.name.startsWith(".") && (it.isDirectory || mimeFor(it) != null) }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

    fun parseRange(header: String?, size: Long): RangeResult {
        if (header == null) return RangeResult.Full
        val m = Regex("^bytes=(\\d*)-(\\d*)$").matchEntire(header.trim()) ?: return RangeResult.Full
        val (a, b) = m.destructured
        if (a.isEmpty() && b.isEmpty()) return RangeResult.Invalid
        val start: Long
        val end: Long
        if (a.isEmpty()) { // suffix: last n bytes
            val n = b.toLongOrNull() ?: return RangeResult.Invalid
            if (n == 0L) return RangeResult.Invalid
            start = maxOf(0, size - n)
            end = size - 1
        } else {
            start = a.toLongOrNull() ?: return RangeResult.Invalid
            end = if (b.isEmpty()) size - 1 else minOf(b.toLongOrNull() ?: return RangeResult.Invalid, size - 1)
        }
        return if (start >= size || start > end) RangeResult.Invalid else RangeResult.Partial(start, end)
    }

    sealed class RangeResult {
        object Full : RangeResult()
        object Invalid : RangeResult()
        data class Partial(val start: Long, val end: Long) : RangeResult()
    }

    fun escapeXml(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    fun unescapeXml(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&")
}

/** Result of a ContentDirectory Browse: DIDL-Lite document plus paging counts. */
data class BrowseResult(val didl: String, val returned: Int, val total: Int)

class DidlBuilder(
    private val root: File,
    private val baseUrl: String,
    private val rootTitle: String,
    private val withThumbs: Boolean,
) {
    fun browse(objectId: String, flag: String, start: Int, count: Int): BrowseResult? {
        val target = DlnaContent.resolve(root, objectId) ?: return null
        if (flag == "BrowseMetadata") {
            return BrowseResult(wrap(describe(target)), 1, 1)
        }
        if (!target.isDirectory) return null
        val all = DlnaContent.children(target)
        val from = start.coerceIn(0, all.size)
        val page = if (count <= 0) all.drop(from) else all.drop(from).take(count)
        return BrowseResult(wrap(page.joinToString("") { describe(it) }), page.size, all.size)
    }

    private fun describe(f: File): String {
        val id = DlnaContent.idFor(root, f)
        val isRoot = id == DlnaContent.ROOT_ID
        val parent = if (isRoot) "-1" else DlnaContent.idFor(root, f.parentFile!!)
        return if (f.isDirectory) {
            val title = if (isRoot) rootTitle else f.name
            val n = DlnaContent.children(f).size
            """<container id="$id" parentID="$parent" restricted="1" searchable="0" childCount="$n">""" +
                "<dc:title>${DlnaContent.escapeXml(title)}</dc:title>" +
                "<upnp:class>object.container.storageFolder</upnp:class></container>"
        } else {
            val mime = DlnaContent.mimeFor(f) ?: "video/mp4"
            val art = if (withThumbs) {
                """<upnp:albumArtURI dlna:profileID="JPEG_TN">$baseUrl/thumb/$id.jpg</upnp:albumArtURI>"""
            } else ""
            """<item id="$id" parentID="$parent" restricted="1">""" +
                "<dc:title>${DlnaContent.escapeXml(f.nameWithoutExtension)}</dc:title>" +
                "<upnp:class>object.item.videoItem</upnp:class>$art" +
                """<res protocolInfo="http-get:*:$mime:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000" size="${f.length()}">""" +
                "$baseUrl/media/$id.${f.extension.lowercase()}</res></item>"
        }
    }

    private fun wrap(body: String) =
        """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns:dlna="urn:schemas-dlna-org:metadata-1-0/">$body</DIDL-Lite>"""
}
