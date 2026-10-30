package com.example.linkgrab

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState == null) handleShare(intent)

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) { MainScreen() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Leaving the app locks the private files again. */
    override fun onStop() {
        super.onStop()
        PrivateLock.relock()
    }

    /** Links shared into the app from a browser or another app go to the preview list. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val urls = LinkParser.extract(text)
        if (urls.isEmpty()) {
            Toast.makeText(this, "No links found in shared text", Toast.LENGTH_SHORT).show()
        } else {
            val n = PreviewRepository.load(urls)
            Toast.makeText(this, "Fetching info for $n link(s)", Toast.LENGTH_SHORT).show()
        }
    }
}

// ===================================================================== theme

private val HeaderBrush = Brush.horizontalGradient(listOf(Color(0xFF534AB7), Color(0xFF185FA5)))

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFFAFA9EC), onPrimary = Color(0xFF26215C),
            primaryContainer = Color(0xFF3C3489), onPrimaryContainer = Color(0xFFCECBF6),
            secondary = Color(0xFF5DCAA5), onSecondary = Color(0xFF04342C),
            secondaryContainer = Color(0xFF085041), onSecondaryContainer = Color(0xFF9FE1CB),
            tertiary = Color(0xFFF0997B), onTertiary = Color(0xFF4A1B0C),
            tertiaryContainer = Color(0xFF712B13), onTertiaryContainer = Color(0xFFF5C4B3),
            background = Color(0xFF14131C), onBackground = Color(0xFFE6E3F2),
            surface = Color(0xFF1C1B26), onSurface = Color(0xFFE6E3F2),
            surfaceVariant = Color(0xFF2B2938), onSurfaceVariant = Color(0xFFC9C6D6),
            error = Color(0xFFF09595), errorContainer = Color(0xFF791F1F), onErrorContainer = Color(0xFFF7C1C1),
            outline = Color(0xFF8A879A),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF534AB7), onPrimary = Color.White,
            primaryContainer = Color(0xFFEEEDFE), onPrimaryContainer = Color(0xFF26215C),
            secondary = Color(0xFF0F6E56), onSecondary = Color.White,
            secondaryContainer = Color(0xFFE1F5EE), onSecondaryContainer = Color(0xFF04342C),
            tertiary = Color(0xFF993C1D), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFAECE7), onTertiaryContainer = Color(0xFF4A1B0C),
            background = Color(0xFFF7F6FD), onBackground = Color(0xFF1C1B26),
            surface = Color.White, onSurface = Color(0xFF1C1B26),
            surfaceVariant = Color(0xFFEFEDF8), onSurfaceVariant = Color(0xFF55526A),
            error = Color(0xFFA32D2D), errorContainer = Color(0xFFFCEBEB), onErrorContainer = Color(0xFF501313),
            outline = Color(0xFF8A879A),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private class StatusStyle(val label: String, val container: Color, val content: Color, val accent: Color)

private fun statusStyle(status: Status, cs: ColorScheme): StatusStyle = when (status) {
    Status.QUEUED -> StatusStyle("Queued", cs.surfaceVariant, cs.onSurfaceVariant, cs.outline)
    Status.RUNNING -> StatusStyle("Downloading", cs.primaryContainer, cs.onPrimaryContainer, cs.primary)
    Status.DONE -> StatusStyle("Done", cs.secondaryContainer, cs.onSecondaryContainer, cs.secondary)
    Status.FAILED -> StatusStyle("Failed", cs.errorContainer, cs.onErrorContainer, cs.error)
    Status.CANCELLED -> StatusStyle("Cancelled", cs.tertiaryContainer, cs.onTertiaryContainer, cs.tertiary)
}

// ===================================================================== main

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val previews by PreviewRepository.items.collectAsState()
    val queue by DownloadRepository.items.collectAsState()

    var input by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    var privateMode by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<PrivateStore.Entry?>(null) }
    val privateEntries by PrivateStore.entries.collectAsState()
    var lockMessage by remember { mutableStateOf<String?>(null) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    /** Returns true if any link was found. */
    fun fetchFromText(text: String): Boolean {
        val urls = LinkParser.extract(text)
        if (urls.isEmpty()) {
            toast("No valid links found")
            return false
        }
        val n = PreviewRepository.load(urls)
        toast(if (n == urls.size) "Fetching info for $n link(s)" else "Fetching $n new link(s); the rest are already listed")
        return true
    }

    val pickTxt = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) { readText(context, uri) }
            if (text == null) toast("Couldn't read that file") else fetchFromText(text)
        }
    }

    fun tryUnlock() {
        val activity = context.findFragmentActivity() ?: return
        when (PrivateLock.availability(context)) {
            PrivateLock.Availability.OK ->
                PrivateLock.authenticate(activity) { ok, error -> lockMessage = if (ok) null else error }
            PrivateLock.Availability.NOT_SET_UP ->
                lockMessage = "Set up a fingerprint or screen lock in your phone's settings to open private files."
            PrivateLock.Availability.UNAVAILABLE ->
                lockMessage = "Fingerprint and screen lock aren't available on this phone right now."
        }
    }

    // Ask for the fingerprint as soon as the Private tab opens.
    LaunchedEffect(tab) {
        if (tab == 1 && !PrivateLock.unlocked) tryUnlock()
    }
    // If the app locks again (for example after going to the background), close the player.
    LaunchedEffect(PrivateLock.unlocked) {
        if (!PrivateLock.unlocked) playing = null
    }

    playing?.let { entry ->
        PlayerScreen(entry) { playing = null }
        return
    }

    val selected = previews.filter { it.checked && it.state == PreviewState.READY && it.chosen != null }
    val knownBytes = selected.sumOf { it.chosen?.sizeBytes ?: 0L }
    val unknownCount = selected.count { (it.chosen?.sizeBytes ?: 0L) <= 0L }
    val totalSpeed = queue.filter { it.status == Status.RUNNING }.sumOf { it.speedBps }
    val activeCount = queue.count { it.isActive }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Header(
                tab = tab,
                onTab = { tab = it },
                privateLabel = if (PrivateLock.unlocked) "Private (${privateEntries.size})" else "Private",
                totalSpeed = totalSpeed,
                activeCount = activeCount,
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = tab == 0 && selected.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                DownloadBar(
                    count = selected.size,
                    knownBytes = knownBytes,
                    unknownCount = unknownCount,
                    privateMode = privateMode,
                    onPrivateChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        privateMode = it
                    },
                    onDownload = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val n = PreviewRepository.downloadChecked(privateMode)
                        toast(
                            if (n > 0) "Downloading $n item(s)" + (if (privateMode) " privately" else "")
                            else "Already in the queue"
                        )
                    },
                )
            }
        },
    ) { padding ->
        if (tab == 1) {
            val sideModifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
            if (PrivateLock.unlocked) {
                PrivateLibrary(modifier = sideModifier, onPlay = { playing = it })
            } else {
                LockedPanel(modifier = sideModifier, message = lockMessage, onUnlock = { tryUnlock() })
            }
        } else LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
        ) {
            item(key = "input") {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    label = { Text("Paste one link or a whole list") },
                    maxLines = 5,
                )
            }

            item(key = "buttons") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (fetchFromText(input)) input = ""
                        },
                        enabled = input.isNotBlank(),
                        modifier = Modifier.weight(1.3f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    ) { Text("Get info", maxLines = 1) }

                    FilledTonalButton(
                        onClick = {
                            val clip = clipboard.getText()?.text
                            if (clip.isNullOrBlank()) toast("Clipboard is empty")
                            else input = if (input.isBlank()) clip else input + "\n" + clip
                        },
                        modifier = Modifier.weight(0.9f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    ) { Text("Paste", maxLines = 1) }

                    FilledTonalButton(
                        onClick = { pickTxt.launch(arrayOf("text/plain", "text/*")) },
                        modifier = Modifier.weight(1.1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    ) { Text("Import .txt", maxLines = 1) }
                }
            }

            // ---------------------------------------------------------- preview section
            if (previews.isNotEmpty()) {
                item(key = "preview-header") { PreviewHeader(count = previews.size) }
                items(previews, key = { "p${it.id}" }) { PreviewCard(it, Modifier.animateItem()) }
            }

            // ------------------------------------------------------------ queue section
            item(key = "queue-header") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Downloads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (activeCount > 0) {
                            StatusChip(
                                "$activeCount active",
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        if (totalSpeed > 0) {
                            StatusChip(
                                "↓ ${formatSpeed(totalSpeed)}",
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    Row {
                        TextButton(onClick = {
                            scope.launch {
                                toast("Updating yt-dlp…")
                                val msg = withContext(Dispatchers.IO) { DownloadRepository.updateYtDlp() }
                                toast(msg)
                            }
                        }) { Text("Update engine") }
                        TextButton(onClick = { DownloadRepository.clearFinished() }) { Text("Clear done") }
                    }
                }
            }

            if (queue.isEmpty() && previews.isEmpty()) {
                item(key = "empty") { EmptyState() }
            }

            items(queue.asReversed(), key = { "q${it.id}" }) { QueueCard(it, Modifier.animateItem()) }
        }
    }
}

// ================================================================== header / bar

@Composable
private fun Header(tab: Int, onTab: (Int) -> Unit, privateLabel: String, totalSpeed: Long, activeCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderBrush)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LogoMark(size = 44.dp, circle = Color.White.copy(alpha = 0.2f))
            Column {
                Text("LinkGrab", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (activeCount > 0) "↓ ${formatSpeed(totalSpeed)} · $activeCount active"
                    else "Paste, preview, download",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillTab("Download", tab == 0) { onTab(0) }
            PillTab(privateLabel, tab == 1) { onTab(1) }
        }
    }
}

@Composable
private fun PillTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) Color.White else Color.White.copy(alpha = 0.16f),
        label = "pillBg",
    )
    val fg by animateColorAsState(
        if (selected) Color(0xFF3C3489) else Color.White,
        label = "pillText",
    )
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(label, color = fg, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

/** The app logo: a downward triangle over a bar, drawn on a circle. */
@Composable
private fun LogoMark(size: Dp, circle: Color, glyph: Color = Color.White) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.minDimension / 100f
        drawCircle(circle)
        val tri = Path().apply {
            moveTo(29 * s, 28 * s)
            lineTo(71 * s, 28 * s)
            lineTo(50 * s, 62 * s)
            close()
        }
        drawPath(tri, glyph)
        drawPath(tri, glyph, style = Stroke(width = 4 * s, join = StrokeJoin.Round))
        drawLine(glyph, Offset(33 * s, 77 * s), Offset(67 * s, 77 * s), strokeWidth = 7 * s, cap = StrokeCap.Round)
    }
}

@Composable
private fun DownloadBar(
    count: Int,
    knownBytes: Long,
    unknownCount: Int,
    privateMode: Boolean,
    onPrivateChange: (Boolean) -> Unit,
    onDownload: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (privateMode) cs.tertiaryContainer else cs.primaryContainer,
        label = "barColor",
    )
    val content = if (privateMode) cs.onTertiaryContainer else cs.onPrimaryContainer

    Surface(
        color = container,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPrivateChange(!privateMode) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = privateMode, onCheckedChange = onPrivateChange)
                Column {
                    Text("Download privately", color = content, fontWeight = FontWeight.Medium)
                    Text(
                        "Only visible and playable inside this app",
                        color = content.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("$count selected", color = content, fontWeight = FontWeight.Bold)
                    Text(
                        text = buildString {
                            append(if (knownBytes > 0) "Total ${formatSize(knownBytes)}" else "Size unknown")
                            if (knownBytes > 0 && unknownCount > 0) append(" + $unknownCount unknown")
                        },
                        color = content.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                    )
                }
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = if (privateMode) cs.tertiary else cs.primary),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                ) { Text(if (privateMode) "Download privately" else "Download", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, container: Color, content: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, color = content, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun SmallPill(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LogoMark(size = 96.dp, circle = MaterialTheme.colorScheme.primary)
        Text("Ready when you are", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Paste links, import a .txt file with one link per line, or share a link to this app from your browser. You'll see each video's thumbnail, name and size for every quality before anything downloads.\n\nFiles are saved to Downloads/LinkGrab.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ================================================================ preview section

@Composable
private fun PreviewHeader(count: Int) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Choose what to download ($count)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallPill("Select all") { PreviewRepository.setAllChecked(true) }
            SmallPill("None") { PreviewRepository.setAllChecked(false) }
            Box {
                SmallPill("Set all to ▾") { menuOpen = true }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val presets = listOf(
                        Triple("Best quality", null as Int?, false),
                        Triple("1080p", 1080, false),
                        Triple("720p", 720, false),
                        Triple("480p", 480, false),
                        Triple("360p", 360, false),
                        Triple("Audio only (MP3)", null as Int?, true),
                    )
                    presets.forEach { (label, height, audio) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                PreviewRepository.applyToAll(height, audio)
                                menuOpen = false
                            },
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f))
            TextButton(onClick = { PreviewRepository.clear() }) { Text("Clear") }
        }
    }
}

@Composable
private fun PreviewCard(item: PreviewItem, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val ready = item.state == PreviewState.READY
    val checked = item.checked && ready
    val bg by animateColorAsState(
        if (checked) cs.primaryContainer.copy(alpha = 0.45f) else cs.surface,
        label = "previewBg",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(
            if (checked) 1.5.dp else 1.dp,
            if (checked) cs.primary else cs.outline.copy(alpha = 0.25f),
        ),
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = ready) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    PreviewRepository.toggle(item.id)
                }
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    PreviewRepository.toggle(item.id)
                },
                enabled = ready,
            )
            Thumb(item.thumbnail, item.durationSec, width = 104.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = item.title ?: item.url,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                when (item.state) {
                    PreviewState.LOADING -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Fetching info…", style = MaterialTheme.typography.bodySmall)
                    }
                    PreviewState.ERROR -> {
                        Text(
                            item.error ?: "Failed",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.error,
                        )
                        TextButton(
                            onClick = { PreviewRepository.retry(item.id) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) { Text("Retry") }
                    }
                    PreviewState.READY -> QualityDropdown(item)
                }
            }
            IconButton(onClick = { PreviewRepository.remove(item.id) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Remove")
            }
        }
    }
}

@Composable
private fun QualityDropdown(item: PreviewItem) {
    var open by remember { mutableStateOf(false) }
    val current = item.chosen ?: return
    val cs = MaterialTheme.colorScheme

    Box(modifier = Modifier.fillMaxWidth()) {
        FilledTonalButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = cs.secondaryContainer,
                contentColor = cs.onSecondaryContainer,
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text = current.display,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
            )
            Text("▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            item.options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option.display) },
                    onClick = {
                        PreviewRepository.selectOption(item.id, index)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun Thumb(url: Any?, durationSec: Long?, width: Dp) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .width(width)
            .height(width * 9 / 16)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(cs.primaryContainer, cs.secondaryContainer))),
        contentAlignment = Alignment.Center,
    ) {
        LogoMark(size = width * 0.34f, circle = cs.primary.copy(alpha = 0.85f))
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        formatDuration(durationSec)?.let {
            Text(
                text = it,
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

// ================================================================== queue section

@Composable
private fun QueueCard(item: DownloadItem, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val style = statusStyle(item.status, cs)
    val animatedProgress by animateFloatAsState(item.progress.coerceIn(0f, 1f), label = "progress")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.25f)),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(style.accent),
            )
            Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (item.thumbnail != null) Thumb(item.thumbnail, null, width = 88.dp)

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = item.title ?: item.url,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(style.label, style.container, style.content)
                        StatusChip(
                            when {
                                item.mode == Mode.AUDIO -> "audio"
                                item.height != null -> "${item.height}p"
                                else -> "video"
                            },
                            cs.surfaceVariant,
                            cs.onSurfaceVariant,
                        )
                        if (item.isPrivate) StatusChip("private", cs.tertiaryContainer, cs.onTertiaryContainer)
                    }

                    if (item.status == Status.RUNNING) {
                        if (item.progress >= 0f) {
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = style.accent,
                                trackColor = cs.surfaceVariant,
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = style.accent,
                                trackColor = cs.surfaceVariant,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = if (item.progress >= 0f) "${(item.progress * 100).toInt()}%" else "Starting…",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                            if (item.speedBps > 0) {
                                StatusChip(
                                    "↓ ${formatSpeed(item.speedBps)}",
                                    cs.secondaryContainer,
                                    cs.onSecondaryContainer,
                                )
                            }
                            formatEta(item.etaSec)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        }
                    } else if (!item.message.isNullOrBlank()) {
                        Text(
                            text = item.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.status == Status.FAILED) cs.error else cs.onSurfaceVariant,
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        when (item.status) {
                            Status.QUEUED, Status.RUNNING ->
                                TextButton(onClick = { DownloadRepository.cancel(item.id) }) {
                                    Text("Cancel", color = cs.error)
                                }
                            Status.FAILED, Status.CANCELLED -> {
                                TextButton(onClick = { DownloadRepository.retry(item.id) }) { Text("Retry") }
                                TextButton(onClick = { DownloadRepository.remove(item.id) }) { Text("Remove") }
                            }
                            Status.DONE ->
                                TextButton(onClick = { DownloadRepository.remove(item.id) }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }
}

// ================================================================ private library

@Composable
private fun PrivateLibrary(modifier: Modifier = Modifier, onPlay: (PrivateStore.Entry) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val entries by PrivateStore.entries.collectAsState()
    var toDelete by remember { mutableStateOf<PrivateStore.Entry?>(null) }

    if (entries.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LogoMark(size = 80.dp, circle = cs.tertiary)
            Text("No private files yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Tick \"Download privately\" before you download, and the files will show up here. They can only be opened inside this app and are deleted if you uninstall it.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            items(entries.asReversed(), key = { it.id }) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cs.surface),
                    border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.25f)),
                ) {
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .fillMaxHeight()
                                .background(cs.tertiary),
                        )
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Thumb(PrivateStore.thumb(entry), null, width = 96.dp)
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StatusChip(
                                        if (entry.audio) "audio" else "video",
                                        cs.tertiaryContainer,
                                        cs.onTertiaryContainer,
                                    )
                                    StatusChip(
                                        formatSize(entry.sizeBytes).removePrefix("~"),
                                        cs.surfaceVariant,
                                        cs.onSurfaceVariant,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = { onPlay(entry) },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                    ) { Text("Play") }
                                    TextButton(onClick = { toDelete = entry }) {
                                        Text("Delete", color = cs.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete this file?") },
            text = { Text("\"${entry.title}\" will be permanently deleted from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    PrivateStore.delete(entry.id)
                    toDelete = null
                }) { Text("Delete", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PlayerScreen(entry: PrivateStore.Entry, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val file = remember(entry.id) { PrivateStore.file(entry) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setMediaController(MediaController(ctx))
                        setVideoPath(file.absolutePath)
                        setOnPreparedListener { start() }
                    }
                },
                onRelease = { it.stopPlayback() },
            )
            if (entry.audio) {
                Text(
                    text = "♪",
                    color = Color.White,
                    fontSize = 72.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            TextButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding(),
            ) { Text("Close", color = Color.White) }
        }
    }
}

// ======================================================================= lock

@Composable
private fun LockedPanel(modifier: Modifier = Modifier, message: String?, onUnlock: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(cs.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text("🔒", fontSize = 38.sp) }
        Text("Private files are locked", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = message ?: "Use your fingerprint or screen lock to open them.",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onUnlock,
            colors = ButtonDefaults.buttonColors(containerColor = cs.tertiary, contentColor = cs.onTertiary),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
        ) { Text("Unlock", fontWeight = FontWeight.Bold) }
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

private fun readText(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()
