package com.hostlocal

import org.apache.ftpserver.ConnectionConfigFactory
import org.apache.ftpserver.DataConnectionConfigurationFactory
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.Authority
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import java.io.File

/** Anonymous, read-only FTP server rooted at [rootDir], bound to [bindAddress] (all interfaces if null). */
class FtpHost(
    private val rootDir: File,
    private val bindAddress: String? = null,
    private val port: Int = Settings.FTP_PORT,
) {
    private var server: FtpServer? = null

    fun start() {
        require(rootDir.isDirectory) { "Not a folder: $rootDir" }

        val listener = ListenerFactory().apply {
            port = this@FtpHost.port
            bindAddress?.let { serverAddress = it }
            dataConnectionConfiguration = DataConnectionConfigurationFactory().apply {
                passivePorts = Settings.PASSIVE_PORTS
            }.createDataConnectionConfiguration()
        }.createListener()

        val users = PropertiesUserManagerFactory().createUserManager()
        users.save(BaseUser().apply {
            name = "anonymous"
            homeDirectory = rootDir.absolutePath
            authorities = emptyList<Authority>() // no WritePermission => read-only
        })

        val factory = FtpServerFactory().apply {
            addListener("default", listener)
            userManager = users
            connectionConfig = ConnectionConfigFactory().apply {
                isAnonymousLoginEnabled = true
            }.createConnectionConfig()
        }
        server = factory.createServer().also { it.start() }
    }

    fun stop() {
        server?.stop()
        server = null
    }
}
