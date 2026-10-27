package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.UsbKnownVendors
import com.example.usb.UsbPacketUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("USB Eth Probe", appName)
  }

  @Test
  fun `arp probe frame creation`() {
    val frame = UsbPacketUtils.createArpProbeFrame()
    assertEquals(64, frame.size)
    // Destination broadcast
    assertEquals(0xFF.toByte(), frame[0])
    assertEquals(0xFF.toByte(), frame[5])
    // EtherType 0x0806 (ARP)
    assertEquals(0x08.toByte(), frame[12])
    assertEquals(0x06.toByte(), frame[13])
  }

  @Test
  fun `chipset identification test`() {
    val (isRealtek, nameRtl) = UsbKnownVendors.identifyChipset(0x0BDA, 0x8153, 0)
    assertTrue(isRealtek)
    assertTrue(nameRtl.contains("Realtek RTL8153"))

    val (isAsix, nameAsix) = UsbKnownVendors.identifyChipset(0x0B95, 0x1790, 0)
    assertTrue(isAsix)
    assertTrue(nameAsix.contains("ASIX AX88179"))
  }
}
