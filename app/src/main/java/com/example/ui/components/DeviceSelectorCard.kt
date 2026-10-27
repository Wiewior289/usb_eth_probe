package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UsbDiscoveredDevice
import com.example.ui.theme.*

@Composable
fun DeviceSelectorCard(
    devices: List<UsbDiscoveredDevice>,
    selectedDevice: UsbDiscoveredDevice?,
    onSelectDevice: (UsbDiscoveredDevice) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpandedDetails by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("device_selector_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = "Hub",
                        tint = CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MULTI-HUB USB TOPOLOGIA",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = CyanGlow,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${devices.size} urządzeń",
                            fontSize = 10.sp,
                            color = CyanAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("btn_refresh_devices")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Odśwież USB",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Cable,
                            contentDescription = "Brak USB",
                            tint = TextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Brak podłączonych urządzeń USB Host",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Podłącz przejściówkę OTG / Multi-Hub z adapterem Ethernet",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                // List of devices on the hub
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    devices.forEach { dev ->
                        val isSelected = dev.device.deviceId == selectedDevice?.device?.deviceId
                        val vidHex = String.format("%04X", dev.vendorId)
                        val pidHex = String.format("%04X", dev.productId)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) DarkSurfaceVariant else DarkBackground)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) CyanAccent else DarkBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectDevice(dev) }
                                .padding(10.dp)
                                .testTag("device_item_${dev.device.deviceId}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (dev.isKnownEthernet) Icons.Default.Lan else Icons.Default.Usb,
                                        contentDescription = null,
                                        tint = if (dev.isKnownEthernet) EmeraldSuccess else CyanAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = dev.chipsetDescription,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            if (dev.isKnownEthernet) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = EmeraldGlow,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "ETH",
                                                        color = EmeraldSuccess,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "VID: 0x$vidHex | PID: 0x$pidHex | DevID: ${dev.device.deviceId} | Interfejsy: ${dev.interfaceCount}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSelectDevice(dev) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = CyanAccent,
                                        unselectedColor = TextMuted
                                    )
                                )
                            }
                        }
                    }
                }

                // Selected Device Low-Level Inspector toggle
                selectedDevice?.let { dev ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpandedDetails = !isExpandedDetails }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isExpandedDetails) "Ukryj strukturę interfejsów i endpointów" else "Pokaż szczegóły USB (Deskryptory, Endpointy IN/OUT)",
                            fontSize = 11.sp,
                            color = CyanAccent
                        )
                        Icon(
                            imageVector = if (isExpandedDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(visible = isExpandedDetails) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkBackground)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Ścieżka urządzenia: ${dev.deviceName}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted
                            )
                            if (!dev.manufacturerName.isNullOrBlank()) {
                                Text(
                                    text = "Producent: ${dev.manufacturerName} | Produkt: ${dev.productName}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextSecondary
                                )
                            }

                            Divider(color = DarkBorder, thickness = 0.5.dp)

                            dev.interfaces.forEach { iface ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Interfejs #${iface.id} (Alt: ${iface.alternateSetting}, Klasa: 0x${iface.interfaceClass.toString(16).uppercase()}, Endpointy: ${iface.endpointCount})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (iface.hasBulkPair) EmeraldSuccess else TextPrimary
                                    )
                                    Row(
                                        modifier = Modifier.padding(start = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        iface.bulkInEndpoint?.let { ep ->
                                            Text(
                                                text = "Bulk IN: EP ${ep.endpointNumber} (Max: ${ep.maxPacketSize} B)",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = CyanAccent
                                            )
                                        }
                                        iface.bulkOutEndpoint?.let { ep ->
                                            Text(
                                                text = "Bulk OUT: EP ${ep.endpointNumber} (Max: ${ep.maxPacketSize} B)",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = AmberWarning
                                            )
                                        }
                                        iface.interruptInEndpoint?.let { ep ->
                                            Text(
                                                text = "Int IN: EP ${ep.endpointNumber}",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = TextMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
