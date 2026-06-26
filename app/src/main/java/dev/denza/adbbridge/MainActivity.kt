package dev.denza.adbbridge

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GatewayRepository.initialize(applicationContext)
        requestNotificationPermission()
        setContent {
            DenzaGatewayTheme {
                GatewayScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        GatewayRepository.refreshWifi(applicationContext)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }
}

@Composable
private fun DenzaGatewayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AccentGreen,
            secondary = AccentCyan,
            tertiary = AccentAmber,
            background = AppBackground,
            surface = Panel,
            surfaceVariant = PanelMuted,
            error = AccentRed,
            onPrimary = Color(0xFF03120D),
            onSecondary = Color(0xFF041116),
            onBackground = TextPrimary,
            onSurface = TextPrimary,
            onSurfaceVariant = TextSecondary,
        ),
        content = content,
    )
}

@Composable
private fun GatewayScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by GatewayRepository.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        GatewayRepository.refreshWifi(context)
    }

    Scaffold(containerColor = AppBackground) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header(state)
            ActionStrip(
                state = state,
                onStart = { GatewayService.start(context) },
                onStop = { GatewayService.stop(context) },
                onTest = { scope.launch { GatewayRepository.testAdb(context) } },
                onRotate = { GatewayRepository.rotateCode() },
            )
            ConnectionPanel(state)
            EndpointPanel(state)
            CommandPanel(state)
            LogPanel(state.logs)
        }
    }
}

@Composable
private fun Header(state: GatewayUiState) {
    Surface(
        color = Panel,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, PanelStroke),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Denza ADB Gateway",
                        color = TextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = state.activeEndpoint?.kind?.title ?: "ADB endpoint not selected",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusPill(state.status)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetricBlock(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Rounded.Wifi, contentDescription = null) },
                    label = "Wi-Fi",
                    value = state.wifiBinding?.hostAddress ?: "offline",
                )
                MetricBlock(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    label = "Code",
                    value = state.pairingCode,
                    monospace = true,
                )
                MetricBlock(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    label = "SSH",
                    value = state.config.sshPort.toString(),
                )
            }
        }
    }
}

@Composable
private fun StatusPill(status: GatewayStatus) {
    val color = status.color()
    Surface(
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = if (status == GatewayStatus.Running || status == GatewayStatus.ClientConnected || status == GatewayStatus.Ready) {
                    Icons.Rounded.CheckCircle
                } else {
                    Icons.Rounded.Warning
                },
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
            Text(status.title, color = TextPrimary, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun MetricBlock(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    monospace: Boolean = false,
) {
    Surface(
        modifier = modifier.heightIn(min = 82.dp),
        color = PanelMuted,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                    icon()
                }
                Text(label, color = TextSecondary, fontSize = 13.sp)
            }
            Text(
                value,
                color = TextPrimary,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (monospace) 22.sp else 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ActionStrip(
    state: GatewayUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTest: () -> Unit,
    onRotate: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = onStart,
            enabled = !state.isBusy && !state.isRunning,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Start")
        }
        OutlinedButton(
            onClick = onStop,
            enabled = !state.isBusy && state.status != GatewayStatus.Stopped,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Stop")
        }
        OutlinedButton(
            onClick = onTest,
            enabled = !state.isBusy,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Test ADB")
        }
        OutlinedButton(
            onClick = onRotate,
            enabled = !state.isBusy,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Rotate code")
        }
    }
}

@Composable
private fun ConnectionPanel(state: GatewayUiState) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = Panel),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle("Connection")
            DetailRow("Subnet", state.wifiBinding?.subnet?.toString() ?: "-")
            DetailRow("Endpoint", state.activeEndpoint?.let { "${it.host}:${it.port}" } ?: "-")
            DetailRow("ADB mode", state.activeEndpoint?.kind?.title ?: state.config.endpointMode.title)
            DetailRow("Host key", state.hostFingerprint.ifBlank { "-" })
            state.lastError?.let {
                Surface(
                    color = AccentRed.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = it,
                        color = TextPrimary,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun EndpointPanel(state: GatewayUiState) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = Panel),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Endpoint")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EndpointMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.config.endpointMode == mode,
                        onClick = { GatewayRepository.updateEndpointMode(mode) },
                        label = { Text(mode.title) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentCyan.copy(alpha = 0.20f),
                            selectedLabelColor = TextPrimary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = state.config.endpointMode == mode,
                            borderColor = PanelStroke,
                            selectedBorderColor = AccentCyan,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            }

            if (state.config.endpointMode != EndpointMode.Auto) {
                EndpointFields(state.config)
            }
        }
    }
}

@Composable
private fun EndpointFields(config: GatewayConfig) {
    val isSmartSocket = config.endpointMode == EndpointMode.AdbServer
    val host = if (isSmartSocket) config.adbServerHost else config.rawAdbdHost
    val port = if (isSmartSocket) config.adbServerPort else config.rawAdbdPort
    var hostText by remember(config.endpointMode, host) { mutableStateOf(host) }
    var portText by remember(config.endpointMode, port) { mutableStateOf(port.toString()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = hostText,
            onValueChange = {
                hostText = it
                if (isSmartSocket) GatewayRepository.updateAdbServerHost(it) else GatewayRepository.updateRawAdbdHost(it)
            },
            label = { Text("Host") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
        )
        OutlinedTextField(
            value = portText,
            onValueChange = { value ->
                if (value.length <= 5 && value.all { it.isDigit() }) {
                    portText = value
                    value.toIntOrNull()?.let {
                        if (isSmartSocket) GatewayRepository.updateAdbServerPort(it) else GatewayRepository.updateRawAdbdPort(it)
                    }
                }
            },
            label = { Text("Port") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(132.dp),
            shape = RoundedCornerShape(8.dp),
        )
    }
}

@Composable
private fun CommandPanel(state: GatewayUiState) {
    val commands = state.commands()
    val clipboard = LocalClipboardManager.current
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = Panel),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle("Commands")
            if (commands == null) {
                Text("No command yet", color = TextSecondary, fontSize = 14.sp)
            } else {
                CopyableCommand("SSH tunnel", commands.sshTunnel) {
                    clipboard.setText(AnnotatedString(commands.sshTunnel))
                }
                CopyableCommand("ADB", commands.adbCommand) {
                    clipboard.setText(AnnotatedString(commands.adbCommand))
                }
                commands.extraAdbCommand?.let { command ->
                    CopyableCommand("ADB check", command) {
                        clipboard.setText(AnnotatedString(command))
                    }
                }
            }
        }
    }
}

@Composable
private fun CopyableCommand(label: String, command: String, onCopy: () -> Unit) {
    Surface(
        color = CodePanel,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, PanelStroke),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = TextSecondary, fontSize = 12.sp)
                Text(
                    text = command,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy $label", tint = AccentCyan)
            }
        }
    }
}

@Composable
private fun LogPanel(logs: List<LogEntry>) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = Panel),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle("Log")
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val visibleLogs = logs.takeLast(80)
                itemsIndexed(visibleLogs) { index, entry ->
                    LogLine(index, entry)
                }
            }
        }
    }
}

@Composable
private fun LogLine(index: Int, entry: LogEntry) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = formatter.format(Date(entry.timestampMillis)),
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.width(62.dp),
        )
        Text(
            text = entry.level.name.uppercase(Locale.US).take(4),
            color = entry.level.color(),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.width(38.dp),
        )
        Text(
            text = entry.message.ifBlank { "event #$index" },
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = TextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
            fontFamily = if (label == "Host key") FontFamily.Monospace else FontFamily.Default,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 18.dp)
                .weight(1f),
        )
    }
}

private fun GatewayStatus.color(): Color = when (this) {
    GatewayStatus.Ready,
    GatewayStatus.Running,
    GatewayStatus.ClientConnected -> AccentGreen
    GatewayStatus.NoWifi,
    GatewayStatus.AdbUnavailable,
    GatewayStatus.BlockedPeer -> AccentAmber
    GatewayStatus.Error -> AccentRed
    GatewayStatus.Stopped -> AccentCyan
}

private fun LogLevel.color(): Color = when (this) {
    LogLevel.Info -> AccentCyan
    LogLevel.Warn -> AccentAmber
    LogLevel.Error -> AccentRed
}

private val AppBackground = Color(0xFF0A0D12)
private val Panel = Color(0xFF111827)
private val PanelMuted = Color(0xFF172033)
private val CodePanel = Color(0xFF090F17)
private val PanelStroke = Color(0xFF2A3648)
private val TextPrimary = Color(0xFFE5E7EB)
private val TextSecondary = Color(0xFFAAB3C2)
private val TextMuted = Color(0xFF6B7280)
private val AccentGreen = Color(0xFF34D399)
private val AccentCyan = Color(0xFF67E8F9)
private val AccentAmber = Color(0xFFFBBF24)
private val AccentRed = Color(0xFFF87171)
