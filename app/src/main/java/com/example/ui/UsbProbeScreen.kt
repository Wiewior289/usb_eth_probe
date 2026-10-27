package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ArchitectureAdviceDialog
import com.example.ui.components.DeviceSelectorCard
import com.example.ui.components.TerminalLogView
import com.example.ui.components.VerdictSummaryCard
import com.example.ui.theme.*
import com.example.viewmodel.UsbProbeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbProbeScreen(
    viewModel: UsbProbeViewModel,
    modifier: Modifier = Modifier
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val verdict by viewModel.verdict.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTesting.collectAsStateWithLifecycle()
    val isSniffing by viewModel.isSniffing.collectAsStateWithLifecycle()

    var showAdviceDialog by remember { mutableStateOf(false) }

    // Pulsing effect for active sniffer
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("usb_probe_screen"),
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "USB ETH PROBE",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = EmeraldGlow,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "STOCK ROM",
                                    color = EmeraldSuccess,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "FD Extraction & ClaimInterface Diagnostic Tool",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAdviceDialog = true },
                        modifier = Modifier.testTag("top_btn_advice")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Porady architektoniczne",
                            tint = CyanAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Main Execution Button
            Button(
                onClick = { viewModel.runFullTest() },
                enabled = !isTesting && (selectedDevice != null || devices.isNotEmpty()),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("btn_start_full_test"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanAccent,
                    disabledContainerColor = DarkSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = DarkBackground,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "TEST W TOKU: POBIERANIE FD I CLAIM...",
                        color = DarkBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = DarkBackground,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "URUCHOM TEST USB (FD & CLAIM)",
                        color = DarkBackground,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            OutlinedButton(
                onClick = { viewModel.runLibUsbProbe() },
                enabled = !isTesting && selectedDevice != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_libusb_probe"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = DarkSurface,
                    contentColor = TextPrimary
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(AmberWarning)
                )
            ) {
                Text(
                    text = "LIBUSB: WRAP + DETACH/CLAIM",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                )
            }

            // Quick Actions Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ARP Probe Button
                OutlinedButton(
                    onClick = { viewModel.sendArpProbe() },
                    enabled = !isTesting,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("btn_send_arp_probe"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = DarkSurface,
                        contentColor = TextPrimary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ramka ARP (TX)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                // Sniffer Toggle Button
                OutlinedButton(
                    onClick = { viewModel.toggleSniffer() },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("btn_toggle_sniffer"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSniffing) DarkSurfaceVariant else DarkSurface,
                        contentColor = if (isSniffing) EmeraldSuccess else TextPrimary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (isSniffing) EmeraldSuccess else DarkBorder
                        )
                    )
                ) {
                    Icon(
                        imageVector = if (isSniffing) Icons.Default.Stop else Icons.Default.Hearing,
                        contentDescription = null,
                        tint = if (isSniffing) EmeraldSuccess else AmberWarning,
                        modifier = Modifier
                            .size(16.dp)
                            .then(if (isSniffing) Modifier.alpha(pulseAlpha) else Modifier)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSniffing) "Sniffer: ON" else "Sniffer RX",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSniffing) EmeraldSuccess else TextPrimary
                    )
                }

                // Control Transfer (EP0)
                OutlinedButton(
                    onClick = { viewModel.testControlTransfer() },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("btn_ctrl_transfer"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = DarkSurface,
                        contentColor = TextPrimary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsInputComponent,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("EP0 Ctrl", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Diagnostic Verdict Summary Card
            VerdictSummaryCard(
                verdict = verdict,
                onShowAdvice = { showAdviceDialog = true }
            )

            // Multi-Hub USB Device Topology Card
            DeviceSelectorCard(
                devices = devices,
                selectedDevice = selectedDevice,
                onSelectDevice = { viewModel.selectDevice(it) },
                onRefresh = { viewModel.refreshDevices() }
            )

            // Industrial Terminal Log View
            TerminalLogView(
                logs = logs,
                onClear = { viewModel.clearLogs() },
                onCopy = { viewModel.copyLogsToClipboard() },
                onShare = { viewModel.shareLogs() }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showAdviceDialog) {
        ArchitectureAdviceDialog(
            onDismiss = { showAdviceDialog = false }
        )
    }
}
