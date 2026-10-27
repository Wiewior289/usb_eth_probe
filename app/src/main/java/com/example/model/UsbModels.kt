package com.example.model

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface

/**
 * Information about a detected USB device on the hub or bus.
 */
data class UsbDiscoveredDevice(
    val device: UsbDevice,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val manufacturerName: String?,
    val productName: String?,
    val serialNumber: String?,
    val deviceClass: Int,
    val deviceSubclass: Int,
    val interfaceCount: Int,
    val isKnownEthernet: Boolean,
    val chipsetDescription: String,
    val interfaces: List<UsbInterfaceInfo>
)

data class UsbInterfaceInfo(
    val id: Int,
    val alternateSetting: Int,
    val interfaceClass: Int,
    val interfaceSubclass: Int,
    val interfaceProtocol: Int,
    val endpointCount: Int,
    val bulkInEndpoint: UsbEndpoint?,
    val bulkOutEndpoint: UsbEndpoint?,
    val interruptInEndpoint: UsbEndpoint?,
    val hasBulkPair: Boolean
)

enum class LogLevel {
    INFO, SUCCESS, WARN, ERROR, KERNEL, PACKET
}

data class DiagnosticLog(
    val id: Long = System.nanoTime(),
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String
)

data class ProbeVerdict(
    val hasDevice: Boolean = false,
    val deviceName: String = "",
    val vidPid: String = "",
    val chipsetName: String = "",
    val permissionGranted: Boolean? = null,
    val fileDescriptor: Int? = null,
    val interfaceClaimed: Boolean? = null,
    val kernelErrorClaim: Int? = null,
    val txSuccess: Boolean? = null,
    val txBytes: Int? = null,
    val txErrorCode: Int? = null,
    val rxSuccess: Boolean? = null,
    val rxBytes: Int? = null,
    val rxErrorCode: Int? = null,
    val systemEth0Detected: Boolean = false,
    val systemEth0Ip: String? = null
)

object UsbKnownVendors {
    const val VID_REALTEK = 0x0BDA
    const val VID_ASIX = 0x0B95
    const val VID_MICROCHIP = 0x0424
    const val VID_DAVICOM = 0x0A46
    const val VID_MOSCHIP = 0x9710
    const val VID_APPLE = 0x05AC

    fun identifyChipset(vid: Int, pid: Int, deviceClass: Int): Pair<Boolean, String> {
        return when (vid) {
            VID_REALTEK -> when (pid) {
                0x8152 -> true to "Realtek RTL8152 (Fast Ethernet 10/100M)"
                0x8153 -> true to "Realtek RTL8153 (Gigabit Ethernet 10/100/1000M)"
                0x8156 -> true to "Realtek RTL8156 (2.5G Ethernet)"
                else -> true to "Realtek Network Controller (0x${pid.toString(16).uppercase()})"
            }
            VID_ASIX -> when (pid) {
                0x1790, 0x178A -> true to "ASIX AX88179/AX88179A (USB 3.0 to Gigabit)"
                0x7720, 0x772A, 0x772B -> true to "ASIX AX88772/AX88772B (Fast Ethernet)"
                0x88179 -> true to "ASIX AX88179"
                else -> true to "ASIX Network Controller (0x${pid.toString(16).uppercase()})"
            }
            VID_MICROCHIP -> when (pid) {
                0x7500 -> true to "Microchip LAN7500 (Gigabit Ethernet)"
                0x7800, 0x7850 -> true to "Microchip LAN7800 (Gigabit Ethernet)"
                0x9500, 0x9512, 0x9514 -> true to "SMSC LAN95xx (Fast Ethernet)"
                else -> false to "Microchip / SMSC Controller"
            }
            VID_DAVICOM -> true to "Davicom DM96xx USB Ethernet"
            VID_MOSCHIP -> true to "Moschip MCS7830 Ethernet"
            VID_APPLE -> when (pid) {
                0x1402 -> true to "Apple USB Ethernet Adapter"
                else -> false to "Apple USB Device"
            }
            else -> {
                if (deviceClass == UsbConstants.USB_CLASS_COMM) {
                    true to "Generic CDC-ECM / CDC-NCM Ethernet Adapter"
                } else {
                    false to "Generic USB Device (Class 0x${deviceClass.toString(16).uppercase()})"
                }
            }
        }
    }
}
