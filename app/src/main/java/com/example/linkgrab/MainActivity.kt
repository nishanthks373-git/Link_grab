package com.example.linkgrab

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.pm.ActivityInfo
import android.media.MediaPlayer
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Slider
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.util.Locale
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
    var playing by remember { mutableStateOf<PlayTarget?>(null) }
    val downloaded by DownloadedStore.entries.collectAsState()
    var hiddenOpen by remember { mutableStateOf(false) }
    var unlockRequest by remember { mutableStateOf(0) }
    var dialogId by remember { mutableStateOf<Long?>(null) }
    var bulkAsk by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingDownload?>(null) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun finishDownload(p: PendingDownload, choice: DuplicateChoice) {
        val n = PreviewRepository.commit(p, choice)
        toast(
            if (n > 0) "Downloading $n item(s)"
            else "Nothing new to download"
        )
    }

    /** Starts downloading the given preview items, asking first if a file with the same name exists. */
    fun startDownload(ids: List<Long>, hidden: Boolean = false) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val p = PreviewRepository.prepare(ids, hidden)
        when {
            p == null -> toast("Nothing to download")
            p.duplicateIndexes.isEmpty() -> finishDownload(p, DuplicateChoice.RENAME)
            else -> pending = p
        }
    }

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

    fun closeHidden() {
        hiddenOpen = false
        PrivateLock.relock()
    }

    fun tryUnlock() {
        val activity = context.findFragmentActivity() ?: return
        when (PrivateLock.availability(context)) {
            PrivateLock.Availability.OK ->
                PrivateLock.authenticate(activity) { ok, _ -> if (ok) hiddenOpen = true }
            PrivateLock.Availability.NOT_SET_UP -> toast("Set up a screen lock on your phone to continue")
            PrivateLock.Availability.UNAVAILABLE -> Unit
        }
    }

    // Two quick pulls down at the top of the Downloaded list ask for the fingerprint.
    var pulls by remember { mutableStateOf(0) }
    var lastPull by remember { mutableStateOf(0L) }
    val onPull: () -> Unit = {
        val now = System.currentTimeMillis()
        pulls = if (now - lastPull < 3000) pulls + 1 else 1
        lastPull = now
        if (pulls >= 2) {
            pulls = 0
            unlockRequest++
        }
    }

    LaunchedEffect(unlockRequest) {
        if (unlockRequest > 0 && tab == 1 && !hiddenOpen) tryUnlock()
    }
    LaunchedEffect(tab) {
        if (tab != 1 && hiddenOpen) closeHidden()
        if (tab == 1) withContext(Dispatchers.IO) { DownloadedStore.verify() }
    }
    // If the app locks again (for example after going to the background), close everything hidden.
    LaunchedEffect(PrivateLock.unlocked) {
        if (!PrivateLock.unlocked) {
            hiddenOpen = false
            if (playing?.hidden == true) playing = null
        }
    }
    BackHandler(enabled = hiddenOpen && playing == null) { closeHidden() }

    playing?.let { target ->
        PlayerScreen(target) { playing = null }
        return
    }

    if (bulkAsk) {
        var hidden by remember { mutableStateOf(false) }
        val ids = previews.filter { it.checked && it.state == PreviewState.READY && it.chosen != null }.map { it.id }
        AlertDialog(
            onDismissRequest = { bulkAsk = false },
            title = { Text("Download ${ids.size} item(s)", fontWeight = FontWeight.Bold) },
            text = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { hidden = !hidden },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = hidden, onCheckedChange = { hidden = it })
                    Text("Download privately", fontWeight = FontWeight.Medium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    bulkAsk = false
                    startDownload(ids, hidden)
                }) { Text("Download", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { bulkAsk = false }) { Text("Cancel") } },
        )
    }

    dialogId?.let { id ->
        val item = previews.firstOrNull { it.id == id }
        if (item == null || item.state != PreviewState.READY) dialogId = null
        else DownloadDialog(
            item = item,
            onDismiss = { dialogId = null },
            onConfirm = { index, hidden ->
                PreviewRepository.selectOption(id, index)
                dialogId = null
                startDownload(listOf(id), hidden)
            },
        )
    }

    val selected = previews.filter { it.checked && it.state == PreviewState.READY && it.chosen != null }
    val knownBytes = selected.sumOf { it.chosen?.sizeBytes ?: 0L }
    val unknownCount = selected.count { (it.chosen?.sizeBytes ?: 0L) <= 0L }
    val totalSpeed = queue.filter { it.status == Status.RUNNING }.sumOf { it.speedBps }
    val activeCount = queue.count { it.isActive }

    pending?.let { p ->
        val dupTitles = p.duplicateIndexes.sorted().map { p.requests[it].title ?: p.requests[it].url }
        val others = p.requests.size - dupTitles.size
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (dupTitles.size == 1) "File already exists" else "${dupTitles.size} files already exist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("You already have, or are already downloading:")
                    dupTitles.take(4).forEach {
                        Text("• $it", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    }
                    if (dupTitles.size > 4) Text("and ${dupTitles.size - 4} more")
                    Spacer(Modifier.height(4.dp))
                    Text("Download again as a new copy with _1 added to the name?")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    finishDownload(p, DuplicateChoice.RENAME)
                    pending = null
                }) { Text("Download as _1", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row {
                    if (others > 0) {
                        TextButton(onClick = {
                            finishDownload(p, DuplicateChoice.SKIP)
                            pending = null
                        }) { Text("Skip duplicates") }
                    }
                    TextButton(onClick = { pending = null }) { Text("Cancel") }
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Header(
                tab = tab,
                onTab = { tab = it },
                downloadedLabel = if (downloaded.isEmpty()) "Downloaded" else "Downloaded (${downloaded.size})",
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
                    onDownload = { bulkAsk = true },
                )
            }
        },
    ) { padding ->
        if (tab == 1) {
            val sideModifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
            if (hiddenOpen && PrivateLock.unlocked) {
                PrivateLibrary(
                    modifier = sideModifier,
                    onPlay = { playing = PlayTarget(it.id, it.title, it.audio, Uri.fromFile(PrivateStore.file(it)), true) },
                )
            } else {
                DownloadedLibrary(
                    modifier = sideModifier,
                    onPull = onPull,
                    onPlay = { playing = PlayTarget(it.id, it.title, it.audio, Uri.parse(it.uri), false) },
                    onMove = { list ->
                        scope.launch {
                            val n = withContext(Dispatchers.IO) { list.count { DownloadedStore.moveToHidden(it) } }
                            toast(
                                when {
                                    n == list.size -> "Moved"
                                    n == 0 -> "Couldn't move that file"
                                    else -> "Moved $n of ${list.size}"
                                }
                            )
                        }
                    },
                )
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
                item(key = "preview-header") {
                    PreviewHeader(count = previews.size)
                }
                items(previews, key = { "p${it.id}" }) {
                    PreviewCard(it, Modifier.animateItem(), onDownload = { dialogId = it.id })
                }
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
private fun Header(tab: Int, onTab: (Int) -> Unit, downloadedLabel: String, totalSpeed: Long, activeCount: Int) {
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
            PillTab(downloadedLabel, tab == 1) { onTab(1) }
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
    onDownload: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val container = cs.primaryContainer
    val content = cs.onPrimaryContainer

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
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                ) { Text("Download", fontWeight = FontWeight.Bold) }
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
private fun PreviewCard(item: PreviewItem, modifier: Modifier = Modifier, onDownload: () -> Unit) {
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(onClick = { PreviewRepository.remove(item.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove")
                }
                if (ready) DownloadIconButton(onClick = onDownload)
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
            Text("Nothing here yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
private fun PlayerScreen(entry: PlayTarget, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val activity = remember { context.findFragmentActivity() }

    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var positionMs by remember { mutableStateOf(0) }
    var durationMs by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    var speed by remember { mutableStateOf(1.0f) }
    var feedbackSide by remember { mutableStateOf(0) }
    var feedbackSeconds by remember { mutableStateOf(0) }
    var feedbackKey by remember { mutableStateOf(0) }

    // Full screen: hide the status and navigation bars and keep the screen on while playing.
    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Keep the seek bar and play button in sync with the video.
    LaunchedEffect(videoView) {
        val vv = videoView ?: return@LaunchedEffect
        while (true) {
            if (!dragging) positionMs = vv.currentPosition
            durationMs = vv.duration.coerceAtLeast(0)
            isPlaying = vv.isPlaying
            delay(250)
        }
    }

    // Hide the controls a few seconds after the last touch while playing.
    LaunchedEffect(controlsVisible, isPlaying, interaction) {
        if (controlsVisible && isPlaying) {
            delay(3000)
            controlsVisible = false
        }
    }

    // Fade the "+10s" / "-10s" hint away.
    LaunchedEffect(feedbackKey) {
        if (feedbackKey > 0) {
            delay(700)
            feedbackSide = 0
            feedbackSeconds = 0
        }
    }

    fun seekBy(deltaMs: Int) {
        val vv = videoView ?: return
        val total = vv.duration.coerceAtLeast(0)
        val target = (vv.currentPosition + deltaMs).coerceIn(0, if (total > 0) total else Int.MAX_VALUE)
        vv.seekTo(target)
        positionMs = target
    }

    fun doubleTapSeek(side: Int) {
        seekBy(side * 10_000)
        if (feedbackSide == side) feedbackSeconds += 10 else {
            feedbackSide = side
            feedbackSeconds = 10
        }
        feedbackKey++
        interaction++
    }

    fun togglePlay() {
        val vv = videoView ?: return
        if (vv.isPlaying) {
            vv.pause()
            isPlaying = false
        } else {
            if (durationMs > 0 && vv.currentPosition >= durationMs - 500) vv.seekTo(0)
            vv.start()
            isPlaying = true
        }
        interaction++
    }

    fun cycleSpeed() {
        val options = listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.75f)
        speed = options[(options.indexOf(speed) + 1) % options.size]
        val mp = mediaPlayer
        if (mp != null) {
            runCatching {
                val wasPlaying = videoView?.isPlaying == true
                mp.playbackParams = mp.playbackParams.setSpeed(speed)
                if (!wasPlaying) videoView?.pause()
            }
        }
        interaction++
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(entry.uri)
                    setOnPreparedListener { mp ->
                        mediaPlayer = mp
                        durationMs = duration
                        // Wide videos turn the phone to landscape so they fill the screen.
                        if (mp.videoWidth > mp.videoHeight) {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                        start()
                        isPlaying = true
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        controlsVisible = true
                    }
                }
            },
            update = { if (videoView !== it) videoView = it },
            onRelease = { it.stopPlayback() },
        )

        // Touch layer: tap shows or hides the controls, double tap left or right seeks 10 seconds.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            interaction++
                        },
                        onDoubleTap = { offset ->
                            doubleTapSeek(if (offset.x < size.width / 2f) -1 else 1)
                        },
                    )
                },
        )

        if (entry.audio) {
            Text(
                text = "♪",
                color = Color.White,
                fontSize = 72.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 180.dp),
            )
        }

        if (feedbackSide != 0) {
            Text(
                text = if (feedbackSide < 0) "« ${feedbackSeconds}s" else "${feedbackSeconds}s »",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(if (feedbackSide < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 48.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Text(
                        text = entry.title,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    val speedLabel = (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()) + "x"
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable { cycleSpeed() }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) { Text(speedLabel, color = Color.White, fontWeight = FontWeight.Medium) }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { togglePlay() },
                    contentAlignment = Alignment.Center,
                ) { Text(if (isPlaying) "❚❚" else "▶", color = Color.White, fontSize = 28.sp) }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(clock(if (dragging) dragValue.toInt() else positionMs), color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = if (dragging) dragValue else positionMs.toFloat(),
                        onValueChange = {
                            dragging = true
                            dragValue = it
                            interaction++
                        },
                        onValueChangeFinished = {
                            videoView?.seekTo(dragValue.toInt())
                            positionMs = dragValue.toInt()
                            dragging = false
                        },
                        valueRange = 0f..maxOf(durationMs, 1).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                    Text(clock(durationMs), color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun clock(ms: Int): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%d:%02d", m, sec)
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


/** Round download button drawn with a canvas (arrow into a tray). */
@Composable
private fun DownloadIconButton(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val glyph = cs.onPrimary
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(cs.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val s = this.size.minDimension / 24f
            val w = 2.6f * s
            drawLine(glyph, Offset(12 * s, 3 * s), Offset(12 * s, 15 * s), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(glyph, Offset(6 * s, 10 * s), Offset(12 * s, 16 * s), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(glyph, Offset(18 * s, 10 * s), Offset(12 * s, 16 * s), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(glyph, Offset(5 * s, 21 * s), Offset(19 * s, 21 * s), strokeWidth = w, cap = StrokeCap.Round)
        }
    }
}

// ============================================================ downloaded + dialog

class PlayTarget(val id: String, val title: String, val audio: Boolean, val uri: Uri, val hidden: Boolean)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadedLibrary(
    modifier: Modifier = Modifier,
    onPlay: (DownloadedStore.Entry) -> Unit,
    onMove: (List<DownloadedStore.Entry>) -> Unit,
    onPull: () -> Unit,
) {
    val listState = rememberLazyListState()
    val cs = MaterialTheme.colorScheme
    val entries by DownloadedStore.entries.collectAsState()
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<List<DownloadedStore.Entry>?>(null) }
    var toMove by remember { mutableStateOf<List<DownloadedStore.Entry>?>(null) }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Forget picks for files that are gone.
    val chosen = entries.filter { it.id in picked }
    val selecting = chosen.isNotEmpty()
    BackHandler(enabled = selecting) { picked = emptySet() }
    fun toggle(id: String) {
        picked = if (id in picked) picked - id else picked + id
    }

    Column(modifier = modifier.fillMaxSize()) {
    if (selecting) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${chosen.size} selected", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(
                onClick = { picked = if (chosen.size == entries.size) emptySet() else entries.map { it.id }.toSet() },
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Text(if (chosen.size == entries.size) "None" else "All") }
            TextButton(onClick = { toMove = chosen }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Move to private") }
            TextButton(onClick = { toDelete = chosen }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Delete", color = cs.error)
            }
            IconButton(onClick = { picked = emptySet() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Cancel")
            }
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            // Watches (without consuming) for a downward drag that starts with the list at the top.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                    var total = 0f
                    var change = down
                    while (change.pressed) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        change = event.changes.firstOrNull { it.id == down.id } ?: break
                        total += change.positionChange().y
                    }
                    if (atTop && total > 150f) onPull()
                }
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        if (entries.isEmpty()) {
            item(key = "empty") {
                Column(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LogoMark(size = 80.dp, circle = cs.primary)
                    Text("Nothing downloaded yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Finished downloads show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        items(entries.asReversed(), key = { it.id }) { entry ->
            val isPicked = entry.id in picked
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isPicked) cs.primaryContainer.copy(alpha = 0.45f) else cs.surface,
                ),
                border = BorderStroke(
                    if (isPicked) 1.5.dp else 1.dp,
                    if (isPicked) cs.primary else cs.outline.copy(alpha = 0.25f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .combinedClickable(
                            onClick = { if (selecting) toggle(entry.id) },
                            onLongClick = { toggle(entry.id) },
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .fillMaxHeight()
                            .background(cs.primary),
                    )
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (selecting) {
                            Checkbox(checked = isPicked, onCheckedChange = { toggle(entry.id) })
                        }
                        Thumb(DownloadedStore.thumb(entry), null, width = 96.dp)
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
                                    cs.primaryContainer,
                                    cs.onPrimaryContainer,
                                )
                                StatusChip(
                                    formatSize(entry.sizeBytes).removePrefix("~"),
                                    cs.surfaceVariant,
                                    cs.onSurfaceVariant,
                                )
                            }
                            if (!selecting) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = { onPlay(entry) },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                    ) { Text("Play") }
                                    TextButton(onClick = { toDelete = listOf(entry) }) {
                                        Text("Delete", color = cs.error)
                                    }
                                }
                                TextButton(
                                    onClick = { toMove = listOf(entry) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                ) { Text("Move to private") }
                            }
                        }
                    }
                }
            }
        }
    }
    }

    toMove?.let { list ->
        val label = if (list.size == 1) "\"${list[0].title}\"" else "${list.size} files"
        AlertDialog(
            onDismissRequest = { toMove = null },
            title = { Text("Move to private?") },
            text = { Text("$label will be removed from Downloads, so other apps and the gallery can no longer see ${if (list.size == 1) "it" else "them"}.") },
            confirmButton = {
                TextButton(onClick = {
                    onMove(list)
                    picked = emptySet()
                    toMove = null
                }) { Text("Move", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { toMove = null }) { Text("Cancel") } },
        )
    }

    toDelete?.let { list ->
        val label = if (list.size == 1) "\"${list[0].title}\"" else "${list.size} files"
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(if (list.size == 1) "Delete this file?" else "Delete ${list.size} files?") },
            text = { Text("$label will be permanently deleted from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    picked = emptySet()
                    scope.launch(Dispatchers.IO) { list.forEach { DownloadedStore.delete(it.id) } }
                }) { Text("Delete", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

/** Asks for the quality and whether to download privately, for this one download only. */
@Composable
private fun DownloadDialog(item: PreviewItem, onDismiss: () -> Unit, onConfirm: (Int, Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var index by remember { mutableStateOf(item.selected.coerceIn(0, (item.options.size - 1).coerceAtLeast(0))) }
    var hidden by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    item.title ?: item.url,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                Text("Resolution", fontWeight = FontWeight.Medium)
                Column(
                    modifier = Modifier
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    item.options.forEachIndexed { i, option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { index = i },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = index == i, onClick = { index = i })
                            Text(option.display, fontSize = 14.sp)
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { hidden = !hidden },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = hidden, onCheckedChange = { hidden = it })
                    Text("Download privately", fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(index, hidden) }) { Text("Download", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
