package ai.rever.boss.plugin.dynamic.replay

import ai.rever.boss.plugin.ui.BossTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * UI for the Session Replay Viewer panel.
 *
 * Three regions top to bottom:
 *   - the picker / list region (top)
 *   - the timeline + transport (middle)
 *   - the step view (bottom)
 */
@Composable
fun ReplayContent(viewModel: ReplayViewModel) {
    BossTheme {
        val session by viewModel.session.collectAsState()
        val statusMessage by viewModel.statusMessage.collectAsState()
        val errorMessage by viewModel.errorMessage.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PickerRegion(viewModel = viewModel)

                Divider(color = MaterialTheme.colors.onBackground.copy(alpha = 0.08f))

                if (session != null) {
                    SessionRegion(viewModel = viewModel)
                } else {
                    EmptyState()
                }

                Spacer(modifier = Modifier.weight(1f))

                if (statusMessage != null || errorMessage != null) {
                    ToastRow(
                        statusMessage = statusMessage,
                        errorMessage = errorMessage,
                        onDismiss = { viewModel.clearMessages() },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerRegion(viewModel: ReplayViewModel) {
    val availableSessions by viewModel.availableSessions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var path by remember { mutableStateOf("") }
    var directory by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Session Replay",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onSurface,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                label = { Text("Session file (.json)", fontSize = 10.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = MaterialTheme.colors.onBackground,
                ),
            )
            Button(
                onClick = { viewModel.loadSession(path) },
                enabled = !isLoading && path.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = MaterialTheme.colors.onPrimary,
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text("Load", fontSize = 11.sp)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                value = directory,
                onValueChange = { directory = it },
                label = { Text("Directory of sessions", fontSize = 10.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
            )
            IconButton(
                onClick = { viewModel.listSessions(directory) },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "List sessions in directory",
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        if (availableSessions.isNotEmpty()) {
            SessionList(
                paths = availableSessions,
                onPicked = { p ->
                    path = p
                    viewModel.loadSession(p)
                },
            )
        }
    }
}

@Composable
private fun SessionList(paths: List<String>, onPicked: (String) -> Unit) {
    val listState = rememberLazyListState()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.12f),
                shape = RoundedCornerShape(4.dp),
            ),
        color = MaterialTheme.colors.background,
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(paths, key = { _, p -> p }) { _, p ->
                Text(
                    text = p.substringAfterLast('/'),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colors.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPicked(p) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SessionRegion(viewModel: ReplayViewModel) {
    val session by viewModel.session.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val playing by viewModel.playing.collectAsState()
    val speed by viewModel.speed.collectAsState()

    val s = session ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Timeline(
            stepCount = s.actions.size,
            currentIndex = currentIndex,
            onClickIndex = { viewModel.jumpTo(it) },
        )

        Transport(
            playing = playing,
            speed = speed,
            onPlayPause = { viewModel.togglePlay() },
            onStepBack = { viewModel.stepBack() },
            onStepForward = { viewModel.stepForward() },
            onJumpStart = { viewModel.jumpToStart() },
            onJumpEnd = { viewModel.jumpToEnd() },
            onSpeed = { viewModel.setSpeed(it) },
            onCopySession = { viewModel.copyNarrativeToClipboard() },
            onCopyStep = { viewModel.copyStepNarrativeToClipboard() },
        )

        StepView(
            step = s.actions.getOrNull(currentIndex),
            previous = s.actions.getOrNull(currentIndex - 1),
            index = currentIndex,
            total = s.actions.size,
            relativeToStart = if (s.actions.firstOrNull()?.timestamp == 0L) 0L else
                (s.actions.getOrNull(currentIndex)?.timestamp ?: 0L) - (s.actions.firstOrNull()?.timestamp ?: 0L),
            previousRelativeToStart = if (currentIndex == 0 || s.actions.firstOrNull()?.timestamp == 0L) 0L else
                (s.actions.getOrNull(currentIndex - 1)?.timestamp ?: 0L) - (s.actions.firstOrNull()?.timestamp ?: 0L),
        )
    }
}

@Composable
private fun Timeline(
    stepCount: Int,
    currentIndex: Int,
    onClickIndex: (Int) -> Unit,
) {
    if (stepCount <= 0) return
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
        ) {
            // Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colors.onBackground.copy(alpha = 0.15f)),
            )

            // Tick marks
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(stepCount) { i ->
                    val isCurrent = i == currentIndex
                    val isPast = i < currentIndex
                    val color = when {
                        isCurrent -> MaterialTheme.colors.primary
                        isPast -> MaterialTheme.colors.primary.copy(alpha = 0.7f)
                        else -> MaterialTheme.colors.onBackground.copy(alpha = 0.35f)
                    }
                    Box(
                        modifier = Modifier
                            .size(if (isCurrent) 10.dp else 6.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable(interactionSource = interactionSource, indication = null) {
                                onClickIndex(i)
                            },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Step ${currentIndex + 1} of $stepCount",
            fontSize = 10.sp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun Transport(
    playing: Boolean,
    speed: Double,
    onPlayPause: () -> Unit,
    onStepBack: () -> Unit,
    onStepForward: () -> Unit,
    onJumpStart: () -> Unit,
    onJumpEnd: () -> Unit,
    onSpeed: (Double) -> Unit,
    onCopySession: () -> Unit,
    onCopyStep: () -> Unit,
) {
    var speedMenu by remember { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onJumpStart, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Jump to start",
                    tint = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onStepBack, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "Step back",
                    tint = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onStepForward, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Step forward",
                    tint = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onJumpEnd, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Jump to end",
                    tint = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Box {
                Button(
                    onClick = { speedMenu = true },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.surface,
                        contentColor = MaterialTheme.colors.onSurface,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("${formatSpeed(speed)}x", fontSize = 11.sp)
                }
                DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    listOf(0.5, 1.0, 2.0, 5.0).forEach { v ->
                        DropdownMenuItem(onClick = {
                            onSpeed(v)
                            speedMenu = false
                        }) {
                            Text("${formatSpeed(v)}x", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(
                onClick = onCopySession,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.surface,
                    contentColor = MaterialTheme.colors.onSurface,
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy narrative", fontSize = 11.sp)
            }
            Button(
                onClick = onCopyStep,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.surface,
                    contentColor = MaterialTheme.colors.onSurface,
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy step", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun StepView(
    step: RecordedStep?,
    previous: RecordedStep?,
    index: Int,
    total: Int,
    relativeToStart: Long,
    previousRelativeToStart: Long,
) {
    if (step == null) return
    val paragraph = Narrative.forStep(step, previous, relativeToStart, previousRelativeToStart)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "${step.label} - step ${index + 1} / $total",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onBackground,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.surface,
            shape = RoundedCornerShape(4.dp),
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (step.url?.isNotBlank() == true) {
                    DetailRow("URL", step.url!!)
                }
                if (step.selector.value?.isNotBlank() == true) {
                    DetailRow("Selector (${step.selector.display()})", step.selector.value!!)
                }
                if (step.elementText?.isNotBlank() == true) {
                    DetailRow("Element text", step.elementText!!)
                }
                if (step.elementType?.isNotBlank() == true) {
                    DetailRow("Element type", "<${step.elementType!!}>")
                }
                if (step.value?.isNotBlank() == true) {
                    DetailRow("Value", step.value!!)
                }
                DetailRow("Timestamp", "${step.timestamp}")
                if (relativeToStart > 0) {
                    DetailRow("From session start", "${relativeToStart} ms")
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.background,
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colors.onBackground.copy(alpha = 0.1f),
            ),
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "Narrative",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = paragraph,
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onBackground,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "$label: ",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colors.onBackground,
        )
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Pick a session file or a directory",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Accepts rparecorder recordings: a bare array of actions, or an " +
                    "object with name / description / actions.",
                fontSize = 10.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun ToastRow(
    statusMessage: String?,
    errorMessage: String?,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(statusMessage, errorMessage) {
        kotlinx.coroutines.delay(3_000)
        onDismiss()
    }
    val isError = errorMessage != null
    val text = errorMessage ?: statusMessage ?: return
    val color = if (isError) Color(0xFFEF5350) else Color(0xFF4CAF50)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = MaterialTheme.colors.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatSpeed(v: Double): String {
    return if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
}
