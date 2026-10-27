package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProbeVerdict
import com.example.ui.theme.*

@Composable
fun VerdictSummaryCard(
    verdict: ProbeVerdict,
    onShowAdvice: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("verdict_summary_card"),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                when {
                                    verdict.interfaceClaimed == true -> EmeraldSuccess
                                    verdict.fileDescriptor != null -> CyanAccent
                                    verdict.interfaceClaimed == false -> AmberWarning
                                    else -> TextMuted
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "STATUS DIAGNOSTYKI SPRZĘTOWEJ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        letterSpacing = 1.sp
                    )
                }

                TextButton(
                    onClick = onShowAdvice,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("btn_arch_advice")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Architektura",
                        tint = CyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Analiza ROM / Architektura", fontSize = 11.sp, color = CyanAccent)
                }
            }

            // Grid of 4 key probe indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. FD Extraction
                MetricBadge(
                    title = "FILE DESCRIPTOR (FD)",
                    value = when {
                        verdict.fileDescriptor != null -> "FD #${verdict.fileDescriptor}"
                        verdict.hasDevice -> "Oczekuje..."
                        else -> "Brak USB"
                    },
                    subtext = when {
                        verdict.fileDescriptor != null -> "Pobrany z UsbDeviceConnection"
                        else -> "Czysty uchwyt usbfs"
                    },
                    isSuccess = verdict.fileDescriptor != null,
                    isWarning = false,
                    isFailure = verdict.fileDescriptor == null && verdict.hasDevice && verdict.permissionGranted == false,
                    modifier = Modifier.weight(1f)
                )

                // 2. claimInterface
                MetricBadge(
                    title = "CLAIM INTERFACE",
                    value = when (verdict.interfaceClaimed) {
                        true -> "SUKCES (TRUE)"
                        false -> "EBUSY (-16)"
                        null -> "Nierozpoczęty"
                    },
                    subtext = when (verdict.interfaceClaimed) {
                        true -> "Userland ma pełną kontrolę"
                        false -> "Kernel trzyma eth0"
                        null -> "force = true test"
                    },
                    isSuccess = verdict.interfaceClaimed == true,
                    isWarning = verdict.interfaceClaimed == false,
                    isFailure = false,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 3. Bulk TX
                MetricBadge(
                    title = "BULK TX (WYŚLIJ)",
                    value = when {
                        verdict.txSuccess == true -> "OK (${verdict.txBytes ?: 0} B)"
                        verdict.txErrorCode != null -> "Kod: ${verdict.txErrorCode}"
                        else -> "--"
                    },
                    subtext = if (verdict.txSuccess == true) "Ramka ARP wyemitowana" else "Próba zapisu do endpointu",
                    isSuccess = verdict.txSuccess == true,
                    isWarning = false,
                    isFailure = verdict.txSuccess == false,
                    modifier = Modifier.weight(1f)
                )

                // 4. Bulk RX
                MetricBadge(
                    title = "BULK RX (ODBIÓR)",
                    value = when {
                        verdict.rxSuccess == true -> "OK (${verdict.rxBytes ?: 0} B)"
                        verdict.rxErrorCode != null -> "Timeout (${verdict.rxErrorCode})"
                        else -> "--"
                    },
                    subtext = if (verdict.rxSuccess == true) "Odebrano pakiet z PLC" else "Nasłuch magistrali",
                    isSuccess = verdict.rxSuccess == true,
                    isWarning = verdict.rxErrorCode != null && verdict.rxSuccess == false,
                    isFailure = false,
                    modifier = Modifier.weight(1f)
                )
            }

            // Android System Network Integration Info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "STAN KERNELA ANDROIDA (eth0):",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Text(
                            text = if (verdict.systemEth0Detected) {
                                "Aktywny interfejs: ${verdict.systemEth0Ip}"
                            } else {
                                "Brak systemowego eth0 (sterownik jądra uśpiony lub nieaktywny)"
                            },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (verdict.systemEth0Detected) EmeraldSuccess else TextMuted
                        )
                    }

                    if (verdict.systemEth0Detected) {
                        Surface(
                            color = EmeraldGlow,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "NET-UP",
                                color = EmeraldSuccess,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricBadge(
    title: String,
    value: String,
    subtext: String,
    isSuccess: Boolean,
    isWarning: Boolean,
    isFailure: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isSuccess -> EmeraldSuccess
        isWarning -> AmberWarning
        isFailure -> CrimsonError
        else -> DarkBorder
    }

    val valueColor = when {
        isSuccess -> EmeraldSuccess
        isWarning -> AmberWarning
        isFailure -> CrimsonError
        else -> TextPrimary
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = valueColor
            )
            Text(
                text = subtext,
                fontSize = 9.sp,
                color = TextMuted,
                maxLines = 1
            )
        }
    }
}
