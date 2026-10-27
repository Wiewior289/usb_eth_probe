package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*

@Composable
fun ArchitectureAdviceDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .testTag("architecture_advice_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ANALIZA ARCHITEKTURY BRIDGE",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Stock ROM (bez root) vs Galaxy S9+ vs PLC / GP-Pro EX",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Zamknij", tint = TextPrimary)
                    }
                }

                Divider(color = DarkBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Scenario 1: claimInterface == true
                    AdviceSection(
                        title = "ŚCIEŻKA A: claimInterface(force = true) == TRUE",
                        badge = "RAW USERLAND USB (Go / libusb)",
                        badgeColor = EmeraldSuccess,
                        icon = Icons.Default.Speed,
                        description = "Jeśli claimInterface przejdzie na zielono, oznacza to, że stockowy ROM Samsunga pozwala aplikacji w przestrzeni użytkownika (userland) w całości odpiąć i zająć interfejs USB.\n\n" +
                                "• Co robimy: Przekazujesz wyciągnięty File Descriptor (FD) do biblioteki C/Go (np. przez libusb_wrap_sys_device(ctx, fd, &handle)).\n" +
                                "• Brak rywalizacji: Kernel Linuxa nie zakłóca przesyłu ramek.\n" +
                                "• Pełny L2 Ethernet: Możesz wysyłać i odbierać surowe ramki Ethernet (ARP, ICMP, TCP, UDP, Pro-face GP-Pro EX download/monitor, FTPS) dokładnie tak, jakbyś był fizyczną kartą sieciową."
                    )

                    // Scenario 2: claimInterface == false (-16 EBUSY)
                    AdviceSection(
                        title = "ŚCIEŻKA B: claimInterface == FALSE (-16 EBUSY)",
                        badge = "KERNEL AUTO-BINDING (eth0)",
                        badgeColor = AmberWarning,
                        icon = Icons.Default.Lan,
                        description = "Jeśli claimInterface zwróci FALSE, kernel Androida automatycznie przejął adapter modułem 'r8152' lub 'cdc_ether' i stworzył interfejs 'eth0'.\n\n" +
                                "TO NIE JEST PORAŻKA – TO KLUCZ DO SUKCESU BEZ ROOT!\n" +
                                "• Na stock ROM bez root nie odpalisz iptables i ip rule ręcznie w shellu.\n" +
                                "• ALE skoro kernel zarządza eth0, Android udostępnia oficjalne VpnService (tun0) bez uprawnień root!\n" +
                                "• VpnService tworzy wirtualny interfejs TUN, do którego trafia ruch z Twojego szyfrowanego tunelu TLS 1.3 z PC (GP-Pro EX).\n" +
                                "• Wystarczy zaimplementować unrooted lwIP / tun2socks w Go/Kotlin: pakiety z TUN są kierowane przez standardowe gniazda Socket(192.168.1.1, port) bezpośrednio przez fizyczny interfejs eth0, a Android sam zajmie się trasowaniem i maskaradą w tle!"
                    )

                    // Industrial Protocol Notes
                    AdviceSection(
                        title = "OBSŁUGA GP-PRO EX I DYNAMICZNYCH PORTÓW FTP/FTPS",
                        badge = "TRANSPARENT BRIDGE",
                        badgeColor = CyanAccent,
                        icon = Icons.Default.Security,
                        description = "Pro-face GP-Pro EX wykorzystuje:\n" +
                                "1. Download / Receive projektu: standardowy port TCP 21 (FTP sterowanie) oraz dynamiczne porty transferu w trybie PASS (Passive Mode).\n" +
                                "2. Monitorowanie i status PLC: porty UDP/TCP (zależnie od drivera HMI/PLC).\n\n" +
                                "• W trybie transparentnego tunelu L2/L3 (np. WireGuard/TLS 1.3 tunelujący cały podsieć 192.168.1.0/24), dynamiczne porty FTP PASV nie wymagają ALG (Application Layer Gateway), ponieważ cały ruch IP między PC a HMI/PLC jest trasowany 1:1 bez translacji portów NAT!"
                    )

                    // Summary checklist
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "CHECKLISTA DO WERYFIKACJI:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent
                            )
                            Text(
                                text = "1. Czy FD ma wartość > 0? (potwierdza dostęp do usbfs)",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                            Text(
                                text = "2. Czy claimInterface = true czy false?",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                            Text(
                                text = "3. Jaki kod błędu zwraca TX/RX? (0 lub >0 = sukces; -110 = timeout oczekiwania)",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                            Text(
                                text = "4. Czy w sekcji 'STAN KERNELA' widać eth0? (jeśli tak, kernel sam zestawia sieć)",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                ) {
                    Text("Zrozumiałem, wracam do testów", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AdviceSection(
    title: String,
    badge: String,
    badgeColor: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkBackground)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Surface(
                color = badgeColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = badge,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Text(
                text = description,
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}
