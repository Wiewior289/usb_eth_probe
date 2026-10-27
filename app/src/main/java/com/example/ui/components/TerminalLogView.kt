package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DiagnosticLog
import com.example.model.LogLevel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun TerminalLogView(
    logs: List<DiagnosticLog>,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var autoScroll by remember { mutableStateOf(true) }

    LaunchedEffect(logs.size, autoScroll) {
        if (autoScroll && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("terminal_log_view"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = TerminalBackground),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Terminal Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFFF5F56))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFFFBD2E))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF27C93F))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "LIVE KERNEL & USB LOG TERMINAL (${logs.size})",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TerminalCyan
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_copy_logs")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Kopiuj logi",
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = onShare,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_share_logs")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Udostępnij logi",
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = onClear,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_clear_logs")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Wyczyść logi",
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            autoScroll = !autoScroll
                            if (autoScroll && logs.isNotEmpty()) {
                                coroutineScope.launch { listState.scrollToItem(logs.size - 1) }
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerticalAlignBottom,
                            contentDescription = "Auto-scroll",
                            tint = if (autoScroll) CyanAccent else TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Divider(color = DarkBorder, thickness = 1.dp)

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "--- Oczekiwanie na start testu USB ---",
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 340.dp)
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(logs, key = { it.id }) { item ->
                        val color = when (item.level) {
                            LogLevel.SUCCESS -> TerminalText
                            LogLevel.INFO -> TextPrimary
                            LogLevel.WARN -> TerminalAmber
                            LogLevel.ERROR -> TerminalRed
                            LogLevel.KERNEL -> TerminalAmber
                            LogLevel.PACKET -> TerminalCyan
                        }

                        val tagColor = when (item.level) {
                            LogLevel.SUCCESS -> TerminalText
                            LogLevel.INFO -> TerminalCyan
                            LogLevel.WARN -> TerminalAmber
                            LogLevel.ERROR -> TerminalRed
                            LogLevel.KERNEL -> Color(0xFFFF9100)
                            LogLevel.PACKET -> Color(0xFFE040FB)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "[${item.timestamp}]",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(
                                text = "[${item.tag}]",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = tagColor,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = item.message,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = color,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
