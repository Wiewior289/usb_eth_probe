package com.example.usb

import java.nio.ByteBuffer
import java.util.Locale

object UsbPacketUtils {

    /**
     * Builds a standard ARP Request Ethernet frame:
     * Destination: Broadcast (FF:FF:FF:FF:FF:FF)
     * Source: 02:00:00:19:21:68 (locally administered MAC)
     * EtherType: 0x0806 (ARP)
     * Target IP: 192.168.1.1 (typical PLC/HMI gateway)
     * Sender IP: 192.168.1.100 (host IP as used in user's script)
     * Padded to 64 bytes (standard minimum Ethernet frame size including FCS margin)
     */
    fun createArpProbeFrame(
        senderIp: ByteArray = byteArrayOf(192.toByte(), 168.toByte(), 1.toByte(), 100.toByte()),
        targetIp: ByteArray = byteArrayOf(192.toByte(), 168.toByte(), 1.toByte(), 1.toByte()),
        senderMac: ByteArray = byteArrayOf(0x02, 0x00, 0x00, 0x19, 0x21, 0x68)
    ): ByteArray {
        val buffer = ByteBuffer.allocate(64)
        // 1. Ethernet Header (14 bytes)
        // Destination MAC: Broadcast
        buffer.put(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        // Source MAC
        buffer.put(senderMac)
        // EtherType: 0x0806 (ARP)
        buffer.putShort(0x0806.toShort())

        // 2. ARP Payload (28 bytes)
        buffer.putShort(0x0001.toShort()) // Hardware type: Ethernet (1)
        buffer.putShort(0x0800.toShort()) // Protocol type: IPv4 (0x0800)
        buffer.put(6.toByte())            // Hardware size: 6 (MAC)
        buffer.put(4.toByte())            // Protocol size: 4 (IPv4)
        buffer.putShort(0x0001.toShort()) // Opcode: Request (1)
        buffer.put(senderMac)             // Sender MAC (6 bytes)
        buffer.put(senderIp)              // Sender IP (4 bytes)
        buffer.put(ByteArray(6))          // Target MAC: 00:00:00:00:00:00 (unknown)
        buffer.put(targetIp)              // Target IP (4 bytes)

        // Remaining bytes in 64-byte frame are zeroes (padding)
        return buffer.array()
    }

    /**
     * Builds a raw 64-byte control ping / dummy Ethernet frame.
     */
    fun createDummyProbeFrame(): ByteArray {
        val frame = ByteArray(64)
        for (i in 0 until 6) frame[i] = 0xFF.toByte() // Broadcast
        frame[6] = 0xDE.toByte()
        frame[7] = 0xAD.toByte()
        frame[8] = 0xBE.toByte()
        frame[9] = 0xEF.toByte()
        frame[10] = 0x01.toByte()
        frame[11] = 0x02.toByte()
        frame[12] = 0x88.toByte()
        frame[13] = 0xB5.toByte() // Local experimental IEEE 802
        for (i in 14 until 64) frame[i] = (i and 0xFF).toByte()
        return frame
    }

    /**
     * Formats bytes to hex dump string (e.g. "00 1A 2B 3C...")
     */
    fun toHexString(bytes: ByteArray, length: Int = bytes.size, maxBytes: Int = 32): String {
        val actualLen = minOf(length, bytes.size, maxBytes)
        val sb = StringBuilder()
        for (i in 0 until actualLen) {
            sb.append(String.format("%02X ", bytes[i]))
        }
        if (length > maxBytes) {
            sb.append("... (+${length - maxBytes} B)")
        }
        return sb.toString().trim()
    }

    /**
     * Decodes high-level information from received Ethernet frame.
     */
    fun decodeEthernetHeader(bytes: ByteArray, length: Int): String {
        if (length < 14) return "Raw packet ($length B) - shorter than Ethernet header"
        val dstMac = String.format(
            Locale.US, "%02X:%02X:%02X:%02X:%02X:%02X",
            bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5]
        )
        val srcMac = String.format(
            Locale.US, "%02X:%02X:%02X:%02X:%02X:%02X",
            bytes[6], bytes[7], bytes[8], bytes[9], bytes[10], bytes[11]
        )
        val etherType = ((bytes[12].toInt() and 0xFF) shl 8) or (bytes[13].toInt() and 0xFF)
        val proto = when (etherType) {
            0x0806 -> "ARP"
            0x0800 -> "IPv4"
            0x86DD -> "IPv6"
            0x88CC -> "LLDP (Industrial)"
            0x8892 -> "PROFINET"
            else -> "EtherType 0x${etherType.toString(16).uppercase()}"
        }
        return "$proto | Src: $srcMac -> Dst: $dstMac ($length B)"
    }

    /**
     * Translates Linux kernel errno codes commonly returned by USB bulk transfers.
     */
    fun explainKernelError(code: Int): String {
        return when (code) {
            -1 -> "EPERM / General I/O Error: System driver rejected access, or transfer timed out."
            -16 -> "EBUSY (-16): Device or resource busy! Android kernel driver holds the interface exclusively."
            -19 -> "ENODEV (-19): Device was disconnected or hub port suspended."
            -22 -> "EINVAL (-22): Invalid argument / packet size / endpoint direction."
            -32 -> "EPIPE (-32): Broken pipe / Endpoint STALL. Handshake failed."
            -110 -> "ETIMEDOUT (-110): Transfer timed out waiting for hardware response."
            -113 -> "EHOSTUNREACH (-113): Host unreachable / cable unplugged."
            else -> "Kernel status code: $code"
        }
    }
}
