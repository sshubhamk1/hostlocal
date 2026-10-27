package com.hostlocal

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.URL

class FtpHostTest {
    private lateinit var dir: File
    private lateinit var host: FtpHost
    private val port = 2129

    @Before fun setUp() {
        dir = createTempDir("ftp").also { File(it, "a.txt").writeText("hello") }
        host = FtpHost(dir, "127.0.0.1", port).also { it.start() }
    }

    @After fun tearDown() {
        host.stop()
        dir.deleteRecursively()
    }

    @Test fun anonymousCanDownload() {
        val body = URL("ftp://127.0.0.1:$port/a.txt").openStream().bufferedReader().readText()
        assertEquals("hello", body)
    }

    @Test fun uploadDenied() {
        try {
            URL("ftp://127.0.0.1:$port/new.txt;type=i").openConnection().apply { doOutput = true }
                .getOutputStream().use { it.write("x".toByteArray()) }
            fail("upload should be rejected")
        } catch (e: java.io.IOException) {
            // expected
        }
        assertFalse(File(dir, "new.txt").exists())
    }

    @Test fun originalFileIntact() {
        assertTrue(File(dir, "a.txt").exists())
    }
}
