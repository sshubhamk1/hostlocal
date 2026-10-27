package com.hostlocal

import android.net.Uri
import android.provider.DocumentsContract

/** Maps a SAF tree URI to a filesystem path (needs all-files access to use). */
fun treeUriToPath(uri: Uri): String? {
    val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
    val (volume, rel) = docId.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    val root = if (volume.equals("primary", true)) "/storage/emulated/0" else "/storage/$volume"
    return if (rel.isEmpty()) root else "$root/$rel"
}
