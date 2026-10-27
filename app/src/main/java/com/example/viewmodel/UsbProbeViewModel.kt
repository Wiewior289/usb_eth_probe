package com.example.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.DiagnosticLog
import com.example.model.ProbeVerdict
import com.example.model.UsbDiscoveredDevice
import com.example.usb.UsbProbeManager
import kotlinx.coroutines.flow.StateFlow

class UsbProbeViewModel(application: Application) : AndroidViewModel(application) {

    val probeManager = UsbProbeManager(application.applicationContext, viewModelScope)

    val logs: StateFlow<List<DiagnosticLog>> = probeManager.logs
    val devices: StateFlow<List<UsbDiscoveredDevice>> = probeManager.devices
    val selectedDevice: StateFlow<UsbDiscoveredDevice?> = probeManager.selectedDevice
    val verdict: StateFlow<ProbeVerdict> = probeManager.verdict
    val isTesting: StateFlow<Boolean> = probeManager.isTesting
    val isSniffing: StateFlow<Boolean> = probeManager.isSniffing

    fun refreshDevices() {
        probeManager.refreshDevices()
        probeManager.checkSystemEthernetInterface()
    }

    fun selectDevice(device: UsbDiscoveredDevice) {
        probeManager.selectDevice(device)
    }

    fun runFullTest() {
        probeManager.runFullAutomatedTest()
    }

    fun testControlTransfer() {
        probeManager.testControlTransferDescriptor()
    }

    fun sendArpProbe() {
        probeManager.sendSingleFrame("ARP")
    }

    fun sendDummyProbe() {
        probeManager.sendSingleFrame("DUMMY")
    }

    fun toggleSniffer() {
        probeManager.toggleSniffer()
    }

    fun clearLogs() {
        probeManager.clearLogs()
    }

    fun copyLogsToClipboard() {
        val allLogs = logs.value.joinToString("\n") { log ->
            "[${log.timestamp}] [${log.level}] [${log.tag}] ${log.message}"
        }
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("USB Probe Logs", allLogs)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(getApplication(), "Skopiowano ${logs.value.size} linii logów do schowka", Toast.LENGTH_SHORT).show()
    }

    fun shareLogs() {
        val allLogs = logs.value.joinToString("\n") { log ->
            "[${log.timestamp}] [${log.level}] [${log.tag}] ${log.message}"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Raport diagnostyczny USB Eth Probe (FD & Claim)")
            putExtra(Intent.EXTRA_TEXT, allLogs)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val chooser = Intent.createChooser(intent, "Udostępnij logi diagnostyczne").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        getApplication<Application>().startActivity(chooser)
    }

    override fun onCleared() {
        super.onCleared()
        probeManager.unregisterUsbReceiver()
    }
}
