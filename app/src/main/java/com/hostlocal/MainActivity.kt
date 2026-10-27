package com.hostlocal

import android.content.Intent
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var hasAllFiles by mutableStateOf(false)
    private var batteryOk by mutableStateOf(true)
    private var notifOk by mutableStateOf(true)
    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { notifOk = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = Settings(this)
        setContent {
            MaterialTheme {
                var running by remember { mutableStateOf(HostService.running) }
                var path by remember { mutableStateOf(settings.folderPath) }
                var error by remember { mutableStateOf(HostService.lastError) }
                var hotspotIp by remember { mutableStateOf(Hotspot.currentAddress()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        hotspotIp = Hotspot.currentAddress()
                        running = HostService.running
                        error = HostService.lastError
                        delay(2000)
                    }
                }
                val picker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree()
                ) { uri ->
                    uri?.let(::treeUriToPath)?.let { path = it; settings.folderPath = it }
                }
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("HostLocal", style = MaterialTheme.typography.headlineMedium)
                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it; settings.folderPath = it },
                        label = { Text("Folder to share") },
                        enabled = !running,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(enabled = !running, onClick = { picker.launch(null) }) { Text("Choose folder") }
                    if (!hasAllFiles || !notifOk || !batteryOk) {
                        Text("Setup", style = MaterialTheme.typography.titleMedium)
                        if (!hasAllFiles) {
                            Text("1. Needed to read your videos.")
                            Button(onClick = { requestAllFiles() }) { Text("Grant all-files access") }
                        }
                        if (!notifOk) {
                            Text("2. Shows the running notification.")
                            Button(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                Text("Allow notifications")
                            }
                        }
                        if (!batteryOk) {
                            Text("3. Keeps the server alive with screen off (recommended).")
                            Button(onClick = { requestBatteryExemption() }) { Text("Disable battery optimization") }
                        }
                    }
                    Text(
                        hotspotIp?.let { "ftp://$it:${Settings.FTP_PORT}" }
                            ?: "Hotspot off - turn on mobile hotspot",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (hotspotIp != null) {
                        Text("or ftp://${MdnsHost.FQDN}:${Settings.FTP_PORT}")
                    }
                    Text("Anonymous, read-only")
                    if (running) {
                        Text(
                            if (HostService.ftpAddress != null) "FTP: serving on ${HostService.ftpAddress}"
                            else "FTP: waiting for hotspot",
                        )
                        Text(
                            if (HostService.mdnsAddress != null) "mDNS: advertising ${MdnsHost.FQDN}"
                            else "mDNS: waiting for hotspot",
                        )
                        HostService.ftpError?.let { Text("FTP error: $it", color = MaterialTheme.colorScheme.error) }
                        HostService.mdnsError?.let { Text("mDNS error: $it", color = MaterialTheme.colorScheme.error) }
                    }
                    Button(enabled = hasAllFiles, onClick = {
                        if (running) {
                            HostService.stop(this@MainActivity)
                            running = false
                        } else {
                            HostService.start(this@MainActivity)
                            running = true
                        }
                        error = null
                    }) { Text(if (running) "Stop" else "Start") }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasAllFiles = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            Environment.isExternalStorageManager()
        notifOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        batteryOk = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBatteryExemption() {
        startActivity(
            Intent(
                AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName"),
            )
        )
    }

    private fun requestAllFiles() {
        startActivity(
            Intent(
                AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName"),
            )
        )
    }
}
