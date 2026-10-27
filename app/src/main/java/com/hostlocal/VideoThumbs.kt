package com.hostlocal

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.hostlocal.dlna.ThumbProvider
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Semaphore

/** Extracts a frame from a video, scales to ~320px wide JPEG, caches on disk. */
class VideoThumbs(private val cacheDir: File) : ThumbProvider {
    private val gate = Semaphore(2)

    override fun jpeg(video: File): File? {
        cacheDir.mkdirs()
        val key = MessageDigest.getInstance("SHA-1")
            .digest("${video.path}|${video.lastModified()}".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val out = File(cacheDir, "$key.jpg")
        if (out.exists()) return out

        gate.acquire()
        val mmr = MediaMetadataRetriever()
        try {
            if (out.exists()) return out
            mmr.setDataSource(video.path)
            val frame = mmr.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: mmr.getFrameAtTime(0) ?: return null
            val w = 320
            val h = (frame.height * w / frame.width.toFloat()).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(frame, w, h, true)
            val tmp = File(cacheDir, "$key.tmp")
            tmp.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            tmp.renameTo(out)
            return out
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { mmr.release() }
            gate.release()
        }
    }
}
