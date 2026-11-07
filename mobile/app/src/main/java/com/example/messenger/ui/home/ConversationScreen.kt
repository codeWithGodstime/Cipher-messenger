package com.example.messenger.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.os.Build
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.messenger.ui.theme.CipherTheme
import com.example.messenger.ui.theme.MessengerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

@Composable
fun ConversationScreen(
    thread: ChatThread,
    messages: List<CipherMessage>,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onTogglePin: () -> Unit,
    onNotice: (String) -> Unit,
    onSendMedia: (Uri, String) -> Unit = { _, _ -> },
    onSendVoice: (File) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val spacing = CipherTheme.spacing
    val context = LocalContext.current
    var draft by rememberSaveable(thread.id) { mutableStateOf("") }
    var menu by rememberSaveable(thread.id) { mutableStateOf(false) }
    var sheet by rememberSaveable(thread.id) { mutableStateOf(false) }
    var recording by remember(thread.id) { mutableStateOf(false) }
    val voiceRecorder = remember { mutableStateOf<MediaRecorder?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val rows = conversationRows(messages)
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onSendMedia(uri, "image")
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onSendMedia(uri, "video")
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onSendMedia(uri, "audio")
    }
    val pickDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onSendMedia(uri, "file")
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        cameraUri = null
        if (saved && uri != null) onSendMedia(uri, "image")
    }
    fun launchCamera() {
        val uri = newCameraUri(context)
        if (uri == null) {
            onNotice("Couldn't open the camera.")
            return
        }
        cameraUri = uri
        takePicture.launch(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else onNotice("Camera permission is needed to take a photo.")
    }
    fun ensureCamera() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
    }
    fun releaseVoiceRecorder() {
        val active = voiceRecorder.value ?: return
        voiceRecorder.value = null
        runCatching { active.stop() }
        runCatching { active.release() }
    }
    fun startVoice() {
        val file = File(context.cacheDir, VOICE_DRAFT)
        runCatching { if (file.exists()) file.delete() }
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
        } catch (error: Exception) {
            runCatching { recorder.release() }
            onNotice("Couldn't start the voice note.")
            return
        }
        voiceRecorder.value = recorder
        recording = true
    }
    fun stopVoice() {
        val active = voiceRecorder.value
        voiceRecorder.value = null
        recording = false
        if (active == null) return
        val file = File(context.cacheDir, VOICE_DRAFT)
        try {
            active.stop()
        } catch (error: Exception) {
            runCatching { active.release() }
            onNotice("Couldn't save the voice note.")
            return
        }
        runCatching { active.release() }
        onSendVoice(file)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice() else onNotice("Microphone permission is needed for a voice note.")
    }
    fun onMic() {
        if (recording) {
            stopVoice()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startVoice() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    DisposableEffect(thread.id) {
        onDispose { releaseVoiceRecorder() }
    }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val keyboard = LocalSoftwareKeyboardController.current

    // Newest row is index 0, so the open end of the thread stays in view
    // when the list gets shorter as the keyboard opens.
    LaunchedEffect(thread.id, rows.size) {
        listState.scrollToItem(0)
    }
    LaunchedEffect(imeBottom) {
        if (listState.firstVisibleItemIndex <= 1) {
            listState.scrollToItem(0)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        ) {
            ConversationHeader(
                thread = thread,
                menuOpen = menu,
                onMenuOpen = { menu = true },
                onMenuDismiss = { menu = false },
                onBack = onBack,
                onTogglePin = {
                    menu = false
                    onTogglePin()
                },
                onNotice = {
                    menu = false
                    onNotice(it)
                },
            )
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = spacing.gutter),
                contentPadding = PaddingValues(vertical = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.md, Alignment.Bottom),
            ) {
                item(key = "tail") { Spacer(Modifier.height(spacing.xs)) }
                items(rows.asReversed(), key = { it.key }) { row ->
                    when (row) {
                        is ConversationRow.Day -> DayChip(row.label)
                        is ConversationRow.Bubble -> MessageBubble(
                            message = row.message,
                            thread = thread,
                            onNotice = onNotice,
                        )
                    }
                }
                item(key = "banner") { EncryptionBanner() }
            }
            Composer(
                value = draft,
                onValueChange = { draft = it },
                onAttach = { sheet = true },
                recording = recording,
                onCamera = { ensureCamera() },
                onMic = { onMic() },
                onSend = {
                    val body = draft.trim()
                    if (body.isEmpty()) return@Composer
                    onSend(body)
                    draft = ""
                    keyboard?.show()
                },
            )
        }
        if (sheet) {
            AttachmentSheet(
                onDismiss = { sheet = false },
                onPhoto = {
                    sheet = false
                    pickPhoto.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onVideo = {
                    sheet = false
                    pickVideo.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                    )
                },
                onAudio = {
                    sheet = false
                    pickAudio.launch("audio/*")
                },
                onDocument = {
                    sheet = false
                    pickDocument.launch(arrayOf("*/*"))
                },
                onNotice = {
                    sheet = false
                    onNotice(it)
                },
            )
        }
    }
}

@Composable
private fun ConversationHeader(
    thread: ChatThread,
    menuOpen: Boolean,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onBack: () -> Unit,
    onTogglePin: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val spacing = CipherTheme.spacing
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                modifier = Modifier.weight(1f),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Box {
                    CipherAvatar(
                        initials = thread.initials,
                        tone = thread.tone,
                        size = 40.dp,
                        contentDescription = thread.title,
                    )
                    if (thread.online) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = spacing.xs)) {
                    Text(
                        text = thread.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = thread.presence,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (thread.online) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Row {
                IconButton(onClick = { onNotice("Voice calls are not connected in this build.") }) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Voice call",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onNotice("Video calls are not connected in this build.") }) {
                    Icon(
                        imageVector = Icons.Filled.Videocam,
                        contentDescription = "Video call",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = onMenuOpen) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = onMenuDismiss) {
                        DropdownMenuItem(
                            text = {
                                Text(if (thread.pinned) "Unpin" else "Pin")
                            },
                            onClick = onTogglePin,
                        )
                        DropdownMenuItem(
                            text = { Text("Search in chat") },
                            onClick = { onNotice("Search in this chat is not connected in this build.") },
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
    }
}

private sealed interface ConversationRow {
    val key: String

    data class Day(val label: String, val index: Int) : ConversationRow {
        override val key: String = "day:$index"
    }

    data class Bubble(val message: CipherMessage) : ConversationRow {
        override val key: String = message.id
    }
}

private fun conversationRows(messages: List<CipherMessage>): List<ConversationRow> {
    val rows = ArrayList<ConversationRow>(messages.size + 2)
    var previousDay = ""
    var dayIndex = 0
    for (message in messages) {
        val day = message.dayLabel.ifBlank { "Today" }
        if (day != previousDay) {
            rows += ConversationRow.Day(day, dayIndex)
            dayIndex += 1
            previousDay = day
        }
        rows += ConversationRow.Bubble(message)
    }
    return rows
}

@Composable
private fun DayChip(label: String) {
    val spacing = CipherTheme.spacing
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            )
        }
    }
}

@Composable
private fun EncryptionBanner(modifier: Modifier = Modifier) {
    val spacing = CipherTheme.spacing
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryFixed.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = "Messages are end-to-end encrypted. No one outside of this chat can read or listen to them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: CipherMessage,
    thread: ChatThread,
    onNotice: (String) -> Unit,
) {
    val spacing = CipherTheme.spacing
    val context = LocalContext.current
    val bubble = if (message.outgoing) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }
    val contentColor = if (message.outgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val bubbleColor = if (message.outgoing) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val elevation = if (message.outgoing) 2.dp else 1.dp
    val attachment = message.attachment
    val localPath = attachment?.localPath
    val openFile: (() -> Unit)? = if (attachment != null && !localPath.isNullOrBlank()) {
        {
            openLocalAttachment(context, localPath, attachment.mime, onNotice)
        }
    } else {
        null
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!message.outgoing) {
            CipherAvatar(
                initials = thread.initials,
                tone = thread.tone,
                size = 28.dp,
                contentDescription = null,
                modifier = Modifier.padding(end = spacing.sm, bottom = spacing.xs),
            )
        }
        Column(
            horizontalAlignment = if (message.outgoing) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            if (openFile != null) {
                Surface(
                    onClick = openFile,
                    shape = bubble,
                    color = bubbleColor,
                    shadowElevation = elevation,
                ) {
                    BubbleBody(message, contentColor)
                }
            } else {
                Surface(
                    shape = bubble,
                    color = bubbleColor,
                    shadowElevation = elevation,
                ) {
                    BubbleBody(message, contentColor)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                modifier = Modifier.padding(top = spacing.xs, start = spacing.xs, end = spacing.xs),
            ) {
                Text(
                    text = message.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (message.outgoing && message.read) {
                    Icon(
                        imageVector = Icons.Filled.DoneAll,
                        contentDescription = "Read",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BubbleBody(
    message: CipherMessage,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    val spacing = CipherTheme.spacing
    val attachment = message.attachment
    if (attachment == null) {
        Text(
            text = message.body,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
        )
        return
    }
    if (attachment.kind == "image") {
        Column(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            if (attachment.localPath.isNullOrBlank()) {
                Text(
                    text = "Photo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    modifier = Modifier.padding(horizontal = spacing.xs),
                )
                Text(
                    text = "Waiting for the encrypted file…",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.75f),
                    modifier = Modifier.padding(horizontal = spacing.xs),
                )
            } else {
                AttachmentPreview(
                    path = attachment.localPath,
                    contentDescription = attachment.name,
                    color = contentColor,
                )
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = spacing.xs),
                )
            }
        }
        return
    }
    val icon = when (attachment.kind) {
        "video" -> Icons.Filled.PlayArrow
        "audio" -> Icons.Filled.Headphones
        else -> Icons.Filled.Description
    }
    Row(
        modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.widthIn(max = 180.dp)) {
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatByteSize(attachment.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun AttachmentPreview(
    path: String,
    contentDescription: String,
    color: androidx.compose.ui.graphics.Color,
) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        val decoded = withContext(Dispatchers.IO) { decodePreview(path, 1280) }
        bitmap = decoded?.asImageBitmap()
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Text(
            text = "Photo",
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.padding(CipherTheme.spacing.xs),
        )
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onAttach: () -> Unit,
    recording: Boolean,
    onCamera: () -> Unit,
    onMic: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CipherTheme.spacing
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter, vertical = spacing.sm),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onAttach) {
                Icon(
                    imageVector = Icons.Filled.AddCircle,
                    contentDescription = "Add attachment",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                singleLine = true,
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = if (recording) "Recording…" else "Message...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (recording) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        inner()
                    }
                },
            )
            if (recording && value.isNotEmpty()) {
                Text(
                    text = "Recording…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            IconButton(onClick = onCamera) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = "Camera",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onMic) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = if (recording) "Stop voice note" else "Voice note",
                    tint = if (recording) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Surface(
                onClick = onSend,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "Send message",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentSheet(
    onDismiss: () -> Unit,
    onPhoto: () -> Unit,
    onVideo: () -> Unit,
    onAudio: () -> Unit,
    onDocument: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val spacing = CipherTheme.spacing
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    .clickable(onClick = onDismiss),
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures { } },
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.lg),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 48.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Share Content",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(spacing.lg),
                    ) {
                        ShareAction(Icons.Filled.Image, "Photo", MaterialTheme.colorScheme.primaryFixed, MaterialTheme.colorScheme.primary, onPhoto)
                        ShareAction(Icons.Filled.Videocam, "Video", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimary, onVideo)
                        ShareAction(Icons.Filled.Description, "Document", MaterialTheme.colorScheme.secondaryFixed, MaterialTheme.colorScheme.secondary, onDocument)
                        ShareAction(Icons.Filled.Headphones, "Audio", MaterialTheme.colorScheme.tertiaryFixed, MaterialTheme.colorScheme.tertiary, onAudio)
                        ShareAction(Icons.Filled.LocationOn, "Location", MaterialTheme.colorScheme.primaryFixedDim, MaterialTheme.colorScheme.onPrimaryFixed) {
                            onNotice("Location is not connected in this build.")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareAction(
    icon: ImageVector,
    label: String,
    background: androidx.compose.ui.graphics.Color,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CipherTheme.spacing.sm),
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = background,
            shadowElevation = 1.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = label, tint = tint)
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun newCameraUri(context: android.content.Context): Uri? {
    val dir = File(context.cacheDir, "camera")
    if (!dir.exists() && !dir.mkdirs()) return null
    val file = File(dir, "${UUID.randomUUID()}.jpg")
    if (!file.exists() && !file.createNewFile()) return null
    return runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
}

private fun openLocalAttachment(
    context: android.content.Context,
    path: String,
    mime: String,
    onNotice: (String) -> Unit,
) {
    val file = File(path)
    if (!file.isFile) return
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull() ?: return
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime.ifBlank { "application/octet-stream" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    if (runCatching { context.startActivity(intent) }.isFailure) {
        onNotice("No app on this device can open this file.")
    }
}

private fun decodePreview(path: String, maxEdge: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val width = bounds.outWidth
    val height = bounds.outHeight
    if (width <= 0 || height <= 0) return null
    var sample = 1
    while (width / sample > maxEdge || height / sample > maxEdge) {
        sample *= 2
    }
    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )
}

private fun formatByteSize(bytes: Long): String {
    val size = bytes.coerceAtLeast(0L)
    return when {
        size < 1024L -> "$size B"
        size < 1024L * 1024L -> "${size / 1024L} KB"
        else -> "%.1f MB".format(Locale.US, size / (1024.0 * 1024.0))
    }
}

private const val VOICE_DRAFT = "voice-draft.m4a"

@Preview(showBackground = true, widthDp = 390, heightDp = 884)
@Composable
private fun ConversationPreview() {
    MessengerTheme {
        val thread = CipherSamples.threads.first { it.id == "elena" }
        ConversationScreen(
            thread = thread,
            messages = CipherSamples.messages.getValue(thread.id),
            onBack = {},
            onSend = {},
            onTogglePin = {},
            onNotice = {},
        )
    }
}
