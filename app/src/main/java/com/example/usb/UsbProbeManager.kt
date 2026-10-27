package com.example.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import com.example.model.DiagnosticLog
import com.example.model.LogLevel
import com.example.model.ProbeVerdict
import com.example.model.UsbDiscoveredDevice
import com.example.model.UsbInterfaceInfo
import com.example.model.UsbKnownVendors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UsbProbeManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        const val ACTION_USB_PERMISSION = "com.example.usbethprobe.USB_PERMISSION"
    }

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val _logs = MutableStateFlow<List<DiagnosticLog>>(emptyList())
    val logs = _logs.asStateFlow()

    private val _devices = MutableStateFlow<List<UsbDiscoveredDevice>>(emptyList())
    val devices = _devices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<UsbDiscoveredDevice?>(null)
    val selectedDevice = _selectedDevice.asStateFlow()

    private val _verdict = MutableStateFlow(ProbeVerdict())
    val verdict = _verdict.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting = _isTesting.asStateFlow()

    private val _isSniffing = MutableStateFlow(false)
    val isSniffing = _isSniffing.asStateFlow()

    private var activeConnection: UsbDeviceConnection? = null
    private var activeClaimedInterface: UsbInterface? = null
    private var snifferJob: Job? = null

    private var permissionCallback: ((Boolean, UsbDevice) -> Unit)? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_USB_PERMISSION -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (device != null) {
                        if (granted) {
                            log(LogLevel.SUCCESS, "USB", "Uprawnienia USB przyznane dla urządzenia [${device.deviceName}]")
                        } else {
                            log(LogLevel.ERROR, "USB", "BŁĄD: Użytkownik lub system odmówił uprawnień USB dla [${device.deviceName}]")
                        }
                        permissionCallback?.invoke(granted, device)
                        permissionCallback = null
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    log(LogLevel.INFO, "HUB", "Wykryto podłączenie nowego urządzenia USB do portu/huba.")
                    refreshDevices()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    log(LogLevel.WARN, "HUB", "Urządzenie USB zostało odłączone od portu/huba.")
                    stopSniffer()
                    closeActiveConnection()
                    refreshDevices()
                }
            }
        }
    }

    init {
        registerUsbReceiver()
        refreshDevices()
        checkSystemEthernetInterface()
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
    }

    fun unregisterUsbReceiver() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: Exception) { }
        stopSniffer()
        closeActiveConnection()
    }

    fun log(level: LogLevel, tag: String, message: String) {
        val entry = DiagnosticLog(
            timestamp = timeFormat.format(Date()),
            level = level,
            tag = tag,
            message = message
        )
        _logs.value = _logs.value + entry
    }

    fun clearLogs() {
        _logs.value = emptyList()
        log(LogLevel.INFO, "SYS", "Wyczyszczono bufor logów.")
    }

    /**
     * Inspects Android's native ConnectivityManager to see if the kernel
     * already registered an Ethernet interface (e.g. eth0).
     */
    fun checkSystemEthernetInterface(): Pair<Boolean, String?> {
        try {
            val networks = connectivityManager.allNetworks
            for (network in networks) {
                val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    val linkProps = connectivityManager.getLinkProperties(network)
                    val ifaceName = linkProps?.interfaceName ?: "eth0"
                    val ipAddresses = linkProps?.linkAddresses
                        ?.mapNotNull { (it.address as? Inet4Address)?.hostAddress }
                        ?.joinToString(", ") ?: "Brak przypisanego IPv4"

                    log(
                        LogLevel.INFO, "NET-SYS",
                        "Android OS wykrył natywny interfejs ETHERNET: $ifaceName (IP: $ipAddresses)"
                    )
                    _verdict.value = _verdict.value.copy(
                        systemEth0Detected = true,
                        systemEth0Ip = "$ifaceName: $ipAddresses"
                    )
                    return true to "$ifaceName ($ipAddresses)"
                }
            }
        } catch (e: Exception) {
            log(LogLevel.WARN, "NET-SYS", "Błąd odczytu stanu Ethernet systemu: ${e.message}")
        }
        _verdict.value = _verdict.value.copy(
            systemEth0Detected = false,
            systemEth0Ip = null
        )
        return false to null
    }

    /**
     * Enumerates all connected USB devices via UsbManager.
     */
    fun refreshDevices() {
        try {
            val rawList = usbManager.deviceList.values.toList()
            val parsedList = rawList.map { device ->
                val (isEth, chipsetName) = UsbKnownVendors.identifyChipset(
                    device.vendorId,
                    device.productId,
                    device.deviceClass
                )

                // Inspect interfaces
                val interfaces = mutableListOf<UsbInterfaceInfo>()
                for (i in 0 until device.interfaceCount) {
                    val iface = device.getInterface(i)
                    var bIn: UsbEndpoint? = null
                    var bOut: UsbEndpoint? = null
                    var intIn: UsbEndpoint? = null

                    for (e in 0 until iface.endpointCount) {
                        val ep = iface.getEndpoint(e)
                        if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                            if (ep.direction == UsbConstants.USB_DIR_IN) bIn = ep
                            else if (ep.direction == UsbConstants.USB_DIR_OUT) bOut = ep
                        } else if (ep.type == UsbConstants.USB_ENDPOINT_XFER_INT) {
                            if (ep.direction == UsbConstants.USB_DIR_IN) intIn = ep
                        }
                    }

                    interfaces.add(
                        UsbInterfaceInfo(
                            id = iface.id,
                            alternateSetting = iface.alternateSetting,
                            interfaceClass = iface.interfaceClass,
                            interfaceSubclass = iface.interfaceSubclass,
                            interfaceProtocol = iface.interfaceProtocol,
                            endpointCount = iface.endpointCount,
                            bulkInEndpoint = bIn,
                            bulkOutEndpoint = bOut,
                            interruptInEndpoint = intIn,
                            hasBulkPair = (bIn != null && bOut != null)
                        )
                    )
                }

                // Check if any interface has CDC Ethernet class
                val hasCdcIface = interfaces.any {
                    it.interfaceClass == UsbConstants.USB_CLASS_COMM ||
                            it.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA
                }
                val finalIsEth = isEth || hasCdcIface

                UsbDiscoveredDevice(
                    device = device,
                    deviceName = device.deviceName,
                    vendorId = device.vendorId,
                    productId = device.productId,
                    manufacturerName = try { device.manufacturerName } catch (_: Exception) { null },
                    productName = try { device.productName } catch (_: Exception) { null },
                    serialNumber = try { device.serialNumber } catch (_: Exception) { null },
                    deviceClass = device.deviceClass,
                    deviceSubclass = device.deviceSubclass,
                    interfaceCount = device.interfaceCount,
                    isKnownEthernet = finalIsEth,
                    chipsetDescription = if (hasCdcIface && !isEth) "CDC Ethernet Network Adapter" else chipsetName,
                    interfaces = interfaces
                )
            }

            _devices.value = parsedList
            log(LogLevel.INFO, "SCAN", "Przeskanowano magistralę USB / Multi-Hub: wykryto ${parsedList.size} urządzeń.")

            // Auto-select preferred device (first ethernet adapter, or first device)
            val preferred = parsedList.firstOrNull { it.isKnownEthernet } ?: parsedList.firstOrNull()
            if (_selectedDevice.value == null || !parsedList.any { it.device.deviceId == _selectedDevice.value?.device?.deviceId }) {
                _selectedDevice.value = preferred
            }
        } catch (e: Exception) {
            log(LogLevel.ERROR, "SCAN", "Błąd skanowania USB: ${e.message}")
        }
    }

    fun selectDevice(device: UsbDiscoveredDevice) {
        _selectedDevice.value = device
        log(LogLevel.INFO, "SELECT", "Wybrano urządzenie: VID=0x${String.format("%04X", device.vendorId)}, PID=0x${String.format("%04X", device.productId)} (${device.chipsetDescription})")
    }

    /**
     * Executes the complete automated test sequence:
     * 1. Check/Request USB Permission
     * 2. Open Device via UsbManager
     * 3. Extract File Descriptor (FD)
     * 4. Find bulk interface
     * 5. Call claimInterface(force = true)
     * 6. Perform TX Bulk Transfer (ARP Probe)
     * 7. Perform RX Bulk Transfer (listen)
     * 8. Check System eth0 interface state
     */
    fun runFullAutomatedTest(targetDevice: UsbDevice? = _selectedDevice.value?.device) {
        val device = targetDevice ?: run {
            log(LogLevel.ERROR, "TEST", "BŁĄD: Nie wybrano żadnego urządzenia USB do testu.")
            return
        }

        val discovered = _devices.value.firstOrNull { it.device.deviceId == device.deviceId }
        val vidHex = String.format("%04X", device.vendorId)
        val pidHex = String.format("%04X", device.productId)

        _isTesting.value = true
        _verdict.value = ProbeVerdict(
            hasDevice = true,
            deviceName = device.deviceName,
            vidPid = "0x$vidHex:0x$pidHex",
            chipsetName = discovered?.chipsetDescription ?: "Urządzenie USB"
        )

        log(LogLevel.INFO, "TEST", "==================================================")
        log(LogLevel.INFO, "TEST", "START TESTU: Urządzenie VID=0x$vidHex, PID=0x$pidHex")
        log(LogLevel.INFO, "TEST", "Model chipsetu: ${discovered?.chipsetDescription}")
        log(LogLevel.INFO, "TEST", "Ścieżka systemowa: ${device.deviceName}, Liczba interfejsów: ${device.interfaceCount}")
        log(LogLevel.INFO, "TEST", "==================================================")

        // Step 1: Check Permission
        if (usbManager.hasPermission(device)) {
            log(LogLevel.SUCCESS, "PERM", "Uprawnienia USB są już aktywne dla tego urządzenia.")
            _verdict.value = _verdict.value.copy(permissionGranted = true)
            scope.launch(Dispatchers.Default) {
                executeDeviceSequence(device, discovered)
            }
        } else {
            log(LogLevel.WARN, "PERM", "Brak uprawnień USB. Wyświetlanie systemowego okna zgody...")
            _verdict.value = _verdict.value.copy(permissionGranted = false)
            requestUsbPermission(device) { granted, grantedDevice ->
                if (granted) {
                    _verdict.value = _verdict.value.copy(permissionGranted = true)
                    scope.launch(Dispatchers.Default) {
                        executeDeviceSequence(grantedDevice, discovered)
                    }
                } else {
                    log(LogLevel.ERROR, "PERM", "Odmowa uprawnień! Android nie udostępni Deskryptora Pliku (FD).")
                    _isTesting.value = false
                }
            }
        }
    }

    private fun requestUsbPermission(device: UsbDevice, onResult: (Boolean, UsbDevice) -> Unit) {
        permissionCallback = onResult
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(context.packageName)
        }
        val permissionIntent = PendingIntent.getBroadcast(context, 0, intent, flags)
        usbManager.requestPermission(device, permissionIntent)
    }

    private suspend fun executeDeviceSequence(device: UsbDevice, discovered: UsbDiscoveredDevice?) {
        withContext(Dispatchers.Main) {
            log(LogLevel.INFO, "DEV", "Otwieranie urządzenia za pomocą UsbManager.openDevice()...")
        }

        closeActiveConnection()

        val connection = usbManager.openDevice(device)
        if (connection == null) {
            withContext(Dispatchers.Main) {
                log(LogLevel.ERROR, "DEV", "KATASTROFA: openDevice() zwróciło NULL!")
                log(LogLevel.ERROR, "DEV", "Przyczyna: Restrykcje SELinux Androida lub odmowa UsbService w jądrze.")
                _verdict.value = _verdict.value.copy(fileDescriptor = null)
                _isTesting.value = false
            }
            return
        }

        activeConnection = connection

        // Step 2: File Descriptor extraction
        val fd = connection.fileDescriptor
        withContext(Dispatchers.Main) {
            log(LogLevel.SUCCESS, "FD", ">>> SUKCES! Wyciągnięto File Descriptor: FD = $fd <<<")
            log(LogLevel.INFO, "FD", "Ten FD ($fd) jest bezpośrednim uchwytem do usbfs (/dev/bus/usb/...).")
            log(LogLevel.INFO, "FD", "Można go przekazać do C/Go/libusb przez libusb_wrap_sys_device(ctx, $fd, &handle)!")
            _verdict.value = _verdict.value.copy(fileDescriptor = fd)
        }

        // Step 3: Find appropriate interface with Bulk endpoints
        var selectedIface: UsbInterface? = null
        var epIn: UsbEndpoint? = null
        var epOut: UsbEndpoint? = null

        // Priority 1: Check CDC Data interface or interface with bulk pair
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            var curIn: UsbEndpoint? = null
            var curOut: UsbEndpoint? = null

            for (e in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) curIn = ep
                    else if (ep.direction == UsbConstants.USB_DIR_OUT) curOut = ep
                }
            }

            if (curIn != null && curOut != null) {
                selectedIface = iface
                epIn = curIn
                epOut = curOut
                break
            }
        }

        if (selectedIface == null || epIn == null || epOut == null) {
            withContext(Dispatchers.Main) {
                log(LogLevel.WARN, "IFACE", "Nie znaleziono interfejsu z klasyczną parą Bulk IN/OUT.")
                log(LogLevel.INFO, "IFACE", "Wykonywanie testu Device Descriptor na kanale kontrolnym (EP 0)...")
            }
            testControlTransferDescriptor(connection)
            withContext(Dispatchers.Main) {
                _isTesting.value = false
            }
            return
        }

        withContext(Dispatchers.Main) {
            log(
                LogLevel.INFO, "IFACE",
                "Wybrany interfejs ID=${selectedIface.id}, Klasa=0x${selectedIface.interfaceClass.toString(16).uppercase()}, Endpoints: IN=${epIn.endpointNumber}, OUT=${epOut.endpointNumber}"
            )
            log(LogLevel.WARN, "CLAIM", ">>> Próba wywołania claimInterface(id=${selectedIface.id}, force = true)... <<<")
        }

        // Step 4: KLUCZOWY TEST BEZPIECZEŃSTWA ROM-U
        val claimResult = connection.claimInterface(selectedIface, true)

        if (!claimResult) {
            withContext(Dispatchers.Main) {
                log(LogLevel.ERROR, "CLAIM", ">>> PORAŻKA: claimInterface zwróciło FALSE (-16 EBUSY)! <<<")
                log(LogLevel.KERNEL, "KERNEL", "WYJAŚNIENIE: Systemowy sterownik jądra Androida (np. r8152, asix, cdc_ether) trzyma interfejs!")
                log(LogLevel.KERNEL, "KERNEL", "Oznacza to, że kernel automatycznie przechwycił kartę jako urządzenie sieciowe 'eth0'.")
                _verdict.value = _verdict.value.copy(
                    interfaceClaimed = false,
                    kernelErrorClaim = -16
                )
            }

            // Test if control requests are still permitted even when interface is busy
            testControlTransferDescriptor(connection)

            // Also check if system registered it in Android ConnectivityManager
            withContext(Dispatchers.Main) {
                checkSystemEthernetInterface()
                log(LogLevel.INFO, "ARCH", "WNIOSEK DLA ARCHITEKTURY PROJEKTU:")
                log(LogLevel.INFO, "ARCH", "1. W userlandzie bez root sterownik jądra blokuje wywłaszczenie przez claimInterface.")
                log(LogLevel.INFO, "ARCH", "2. ALE skoro kernel trzyma eth0, to transparentny bridge można zrealizować przez systemowy VpnService (tun0 <-> eth0) BEZ ROOT!")
                _isTesting.value = false
            }
            return
        }

        activeClaimedInterface = selectedIface

        withContext(Dispatchers.Main) {
            log(LogLevel.SUCCESS, "CLAIM", ">>> SUKCES: Interfejs przejęty pomyślnie (claimInterface = TRUE)! <<<")
            log(LogLevel.SUCCESS, "CLAIM", "Kernel nie zablokował dostępu. Userland ma 100% bezpośredniej kontroli nad linią USB!")
            _verdict.value = _verdict.value.copy(interfaceClaimed = true)
        }

        // Step 5: Test Raw TX Transfer (ARP Probe)
        withContext(Dispatchers.Main) {
            log(LogLevel.INFO, "TX", "Wysyłanie ramki testowej ARP Request (who-has 192.168.1.1 tell 192.168.1.100)...")
        }

        val arpFrame = UsbPacketUtils.createArpProbeFrame()
        val txStartTime = SystemClock.elapsedRealtime()
        val txResult = connection.bulkTransfer(epOut, arpFrame, arpFrame.size, 1000)
        val txDuration = SystemClock.elapsedRealtime() - txStartTime

        withContext(Dispatchers.Main) {
            if (txResult >= 0) {
                log(LogLevel.SUCCESS, "TX", "TX SUKCES: Wysłano $txResult bajtów do endpointu OUT ${epOut.endpointNumber} (czas: ${txDuration}ms).")
                log(LogLevel.PACKET, "TX-DATA", "Dane: ${UsbPacketUtils.toHexString(arpFrame, txResult)}")
                _verdict.value = _verdict.value.copy(
                    txSuccess = true,
                    txBytes = txResult,
                    txErrorCode = null
                )
            } else {
                val explanation = UsbPacketUtils.explainKernelError(txResult)
                log(LogLevel.ERROR, "TX", "TX BŁĄD: bulkTransfer zwrócił kod: $txResult ($explanation)")
                _verdict.value = _verdict.value.copy(
                    txSuccess = false,
                    txBytes = 0,
                    txErrorCode = txResult
                )
            }
        }

        // Step 6: Test Raw RX Transfer (Listen for reply / packets)
        withContext(Dispatchers.Main) {
            log(LogLevel.INFO, "RX", "Nasłuch danych na endpoint IN ${epIn.endpointNumber} (timeout: 1500ms)...")
        }

        val rxBuffer = ByteArray(2048)
        val rxStartTime = SystemClock.elapsedRealtime()
        val rxResult = connection.bulkTransfer(epIn, rxBuffer, rxBuffer.size, 1500)
        val rxDuration = SystemClock.elapsedRealtime() - rxStartTime

        withContext(Dispatchers.Main) {
            if (rxResult >= 0) {
                val decoded = UsbPacketUtils.decodeEthernetHeader(rxBuffer, rxResult)
                log(LogLevel.SUCCESS, "RX", "RX SUKCES: Odebrano $rxResult bajtów z linii sieciowej! ($decoded) (czas: ${rxDuration}ms)")
                log(LogLevel.PACKET, "RX-DATA", "Dane: ${UsbPacketUtils.toHexString(rxBuffer, rxResult)}")
                _verdict.value = _verdict.value.copy(
                    rxSuccess = true,
                    rxBytes = rxResult,
                    rxErrorCode = null
                )
            } else {
                val explanation = UsbPacketUtils.explainKernelError(rxResult)
                log(LogLevel.WARN, "RX", "RX Zakończony: bulkTransfer zwrócił kod: $rxResult ($explanation)")
                log(LogLevel.INFO, "RX", "Uwaga: Kod ujemny (np. -1 lub -110) jest normalny, jeśli żaden sterownik PLC/HMI nie wysłał w tym ułamku sekundy pakietu.")
                _verdict.value = _verdict.value.copy(
                    rxSuccess = false,
                    rxBytes = 0,
                    rxErrorCode = rxResult
                )
            }

            checkSystemEthernetInterface()

            log(LogLevel.INFO, "TEST", "==================================================")
            log(LogLevel.SUCCESS, "TEST", "--- ZAKOŃCZONO PEŁNY CYKL DIAGNOSTYCZNY ---")
            log(LogLevel.INFO, "TEST", "==================================================")
            _isTesting.value = false
        }
    }

    /**
     * Diagnostic: Tests standard USB Control Transfer on Endpoint 0 (Device Descriptor)
     */
    fun testControlTransferDescriptor(conn: UsbDeviceConnection? = activeConnection) {
        val connection = conn ?: return
        val buffer = ByteArray(64)
        // Standard GET_DESCRIPTOR request:
        // bmRequestType: 0x80 (Device-to-Host, Standard, Device)
        // bRequest: 0x06 (GET_DESCRIPTOR)
        // wValue: 0x0100 (DEVICE descriptor type)
        // wIndex: 0x0000
        val res = connection.controlTransfer(0x80, 0x06, 0x0100, 0x0000, buffer, buffer.size, 1000)
        if (res > 0) {
            val usbSpec = String.format("%x.%02x", buffer[3].toInt(), buffer[2].toInt())
            val maxPacket = buffer[7].toInt() and 0xFF
            val vid = ((buffer[9].toInt() and 0xFF) shl 8) or (buffer[8].toInt() and 0xFF)
            val pid = ((buffer[11].toInt() and 0xFF) shl 8) or (buffer[10].toInt() and 0xFF)
            log(LogLevel.SUCCESS, "CTRL", "ControlTransfer SUKCES: Odczytano deskryptor ($res B) na EP0.")
            log(LogLevel.INFO, "CTRL", "USB Spec: $usbSpec | MaxPacket0: $maxPacket | Raw VID: 0x${String.format("%04X", vid)}, PID: 0x${String.format("%04X", pid)}")
        } else {
            log(LogLevel.ERROR, "CTRL", "ControlTransfer BŁĄD: zwrócono $res")
        }
    }

    /**
     * Send single custom Ethernet packet (ARP or Dummy) on demand
     */
    fun sendSingleFrame(frameType: String) {
        val conn = activeConnection
        val iface = activeClaimedInterface
        if (conn == null || iface == null) {
            log(LogLevel.ERROR, "TX", "Brak aktywnego, przejętego interfejsu. Uruchom najpierw test claimInterface!")
            return
        }

        var epOut: UsbEndpoint? = null
        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                epOut = ep
                break
            }
        }

        if (epOut == null) {
            log(LogLevel.ERROR, "TX", "Nie znaleziono endpointu Bulk OUT na przejętym interfejsie.")
            return
        }

        val frame = if (frameType == "ARP") {
            UsbPacketUtils.createArpProbeFrame()
        } else {
            UsbPacketUtils.createDummyProbeFrame()
        }

        scope.launch(Dispatchers.Default) {
            val res = conn.bulkTransfer(epOut, frame, frame.size, 1000)
            withContext(Dispatchers.Main) {
                if (res >= 0) {
                    log(LogLevel.SUCCESS, "TX-MANUAL", "Ręczna ramka [$frameType] wysłana pomyślnie ($res B).")
                } else {
                    log(LogLevel.ERROR, "TX-MANUAL", "Błąd wysyłania: $res (${UsbPacketUtils.explainKernelError(res)})")
                }
            }
        }
    }

    /**
     * Toggles continuous RX sniffer mode
     */
    fun toggleSniffer() {
        if (_isSniffing.value) {
            stopSniffer()
        } else {
            startSniffer()
        }
    }

    private fun startSniffer() {
        val conn = activeConnection
        val iface = activeClaimedInterface
        if (conn == null || iface == null) {
            log(LogLevel.ERROR, "SNIFFER", "Brak aktywnego połączenia USB lub interfejs nie został przejęty. Uruchom najpierw test!")
            return
        }

        var epIn: UsbEndpoint? = null
        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_IN) {
                epIn = ep
                break
            }
        }

        if (epIn == null) {
            log(LogLevel.ERROR, "SNIFFER", "Brak endpointu Bulk IN do nasłuchu!")
            return
        }

        _isSniffing.value = true
        log(LogLevel.INFO, "SNIFFER", ">>> Rozpoczęto ciągły nasłuch sieci (Sniffer Mode) na EP ${epIn.endpointNumber} <<<")

        snifferJob = scope.launch(Dispatchers.Default) {
            val buffer = ByteArray(2048)
            var packetCount = 0
            while (isActive && _isSniffing.value) {
                val res = conn.bulkTransfer(epIn, buffer, buffer.size, 500)
                if (res > 0) {
                    packetCount++
                    val decoded = UsbPacketUtils.decodeEthernetHeader(buffer, res)
                    val hexDump = UsbPacketUtils.toHexString(buffer, res, 24)
                    withContext(Dispatchers.Main) {
                        log(LogLevel.PACKET, "SNIFF #$packetCount", "$decoded | Dump: $hexDump")
                    }
                }
            }
        }
    }

    fun stopSniffer() {
        if (_isSniffing.value) {
            _isSniffing.value = false
            snifferJob?.cancel()
            snifferJob = null
            log(LogLevel.INFO, "SNIFFER", "Zatrzymano tryb Sniffera.")
        }
    }

    fun closeActiveConnection() {
        try {
            stopSniffer()
            activeClaimedInterface?.let { iface ->
                activeConnection?.releaseInterface(iface)
                log(LogLevel.INFO, "USB", "Zwolniono interfejs ID=${iface.id}.")
            }
            activeClaimedInterface = null
            activeConnection?.close()
            activeConnection = null
            log(LogLevel.INFO, "USB", "Zamknięto uchwyt urządzenia USB.")
        } catch (e: Exception) {
            log(LogLevel.WARN, "USB", "Błąd zamykania połączenia: ${e.message}")
        }
    }
}
