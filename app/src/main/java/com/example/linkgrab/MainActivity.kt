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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) { MainScreen() }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    val selected = previews.filter { it.checked && it.state == PreviewState.READY && it.chosen != null }
    val knownBytes = selected.sumOf { it.chosen?.sizeBytes ?: 0L }
    val unknownCount = selected.count { (it.chosen?.sizeBytes ?: 0L) <= 0L }

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

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("LinkGrab") })
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Download") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(if (PrivateLock.unlocked) "Private (${privateEntries.size})" else "Private") })
                }
            }
        },
        bottomBar = {
            if (tab == 0 && selected.isNotEmpty()) {
                Surface(tonalElevation = 6.dp, modifier = Modifier.navigationBarsPadding()) {
                    Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 16.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = privateMode, onCheckedChange = { privateMode = it })
                        Column {
                            Text("Download privately", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Only visible and playable inside this app",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${selected.size} selected", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = buildString {
                                    append(if (knownBytes > 0) "Total ${formatSize(knownBytes)}" else "Size unknown")
                                    if (knownBytes > 0 && unknownCount > 0) append(" + $unknownCount unknown")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = {
                            val n = PreviewRepository.downloadChecked(privateMode)
                            toast(
                                if (n > 0) "Downloading $n item(s)" + (if (privateMode) " privately" else "")
                                else "Already in the queue"
                            )
                        }) { Text("Download") }
                    }
                    }
                }
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item(key = "input") {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Paste one link or a whole list") },
                    maxLines = 5,
                )
            }

            item(key = "buttons") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { if (fetchFromText(input)) input = "" },
                        enabled = input.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) { Text("Get info") }

                    OutlinedButton(
                        onClick = { pickTxt.launch(arrayOf("text/plain", "text/*")) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Import .txt") }
                }
            }

            // ------------------------------------------------------------ preview section
            if (previews.isNotEmpty()) {
                item(key = "preview-header") {
                    PreviewHeader(count = previews.size)
                }
                items(previews, key = { "p${it.id}" }) { PreviewCard(it) }
            }

            // ------------------------------------------------------------- queue section
            item(key = "queue-header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Downloads (${queue.count { it.isActive }} active, ${queue.size} total)",
                        style = MaterialTheme.typography.titleSmall,
                    )
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
                item(key = "empty") {
                    Text(
                        "Nothing yet. Paste links, import a .txt file with one link per line, or share a link to this app from your browser. You'll see each video's thumbnail, name, and sizes for every quality before anything downloads.\n\nFiles are saved to Downloads/LinkGrab.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(queue.asReversed(), key = { "q${it.id}" }) { QueueCard(it) }
        }
    }
}

// ================================================================= preview section

@Composable
private fun PreviewHeader(count: Int) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Choose what to download ($count)", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { PreviewRepository.setAllChecked(true) }) { Text("Select all") }
            TextButton(onClick = { PreviewRepository.setAllChecked(false) }) { Text("None") }
            Box {
                TextButton(onClick = { menuOpen = true }) { Text("Set all to ▾") }
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
private fun PreviewCard(item: PreviewItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = item.checked && item.state == PreviewState.READY,
                onCheckedChange = { PreviewRepository.toggle(item.id) },
                enabled = item.state == PreviewState.READY,
            )
            Thumb(item.thumbnail, item.durationSec, width = 104.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.title ?: item.url,
                    style = MaterialTheme.typography.bodyMedium,
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
                            color = MaterialTheme.colorScheme.error,
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

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
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
    Box(
        modifier = Modifier
            .width(width)
            .height(width * 9 / 16)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
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
private fun QueueCard(item: DownloadItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (item.thumbnail != null) Thumb(item.thumbnail, null, width = 88.dp)

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = item.title ?: item.url,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(
                            when (item.status) {
                                Status.QUEUED -> "Queued"
                                Status.RUNNING ->
                                    if (item.progress in 0f..1f) "Downloading ${(item.progress * 100).toInt()}%" else "Downloading…"
                                Status.DONE -> "Done"
                                Status.FAILED -> "Failed"
                                Status.CANCELLED -> "Cancelled"
                            }
                        )
                        append(
                            when {
                                item.mode == Mode.AUDIO -> " · audio"
                                item.height != null -> " · ${item.height}p"
                                else -> " · video"
                            }
                        )
                        if (item.isPrivate) append(" · private")
                        if (item.status != Status.RUNNING && !item.message.isNullOrBlank()) append("\n${item.message}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == Status.FAILED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (item.status == Status.RUNNING) {
                    if (item.progress in 0f..1f) {
                        LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    when (item.status) {
                        Status.QUEUED, Status.RUNNING ->
                            TextButton(onClick = { DownloadRepository.cancel(item.id) }) { Text("Cancel") }
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

private fun readText(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()


// ================================================================ private library

@Composable
private fun PrivateLibrary(modifier: Modifier = Modifier, onPlay: (PrivateStore.Entry) -> Unit) {
    val entries by PrivateStore.entries.collectAsState()
    var toDelete by remember { mutableStateOf<PrivateStore.Entry?>(null) }

    if (entries.isEmpty()) {
        Column(modifier = modifier.padding(top = 16.dp)) {
            Text(
                "No private files yet. Tick \"Download privately\" before you download, and the files will show up here. They can only be opened inside this app and are deleted if you uninstall it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            items(entries.asReversed(), key = { it.id }) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Thumb(PrivateStore.thumb(entry), null, width = 96.dp)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = (if (entry.audio) "Audio" else "Video") + " · " + formatSize(entry.sizeBytes).removePrefix("~"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row {
                                TextButton(onClick = { onPlay(entry) }) { Text("Play") }
                                TextButton(onClick = { toDelete = entry }) { Text("Delete") }
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
                }) { Text("Delete") }
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Private files are locked", style = MaterialTheme.typography.titleMedium)
        Text(
            text = message ?: "Use your fingerprint or screen lock to open them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onUnlock) { Text("Unlock") }
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
