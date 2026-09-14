package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AssistantViewModel
import com.example.ui.components.MicrophoneButton
import com.example.ui.components.VoiceWaveform
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.VioletNeon
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: AssistantViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening by viewModel.speechRecognizer.isListening.collectAsStateWithLifecycle()
    val isContinuous by viewModel.continuousSpeechRecognizer.isContinuousMode.collectAsStateWithLifecycle()
    val statusMessage by viewModel.continuousSpeechRecognizer.statusMessage.collectAsStateWithLifecycle()
    val partialText by viewModel.continuousSpeechRecognizer.partialText.collectAsStateWithLifecycle()
    val rmsDb by viewModel.speechRecognizer.rmsDb.collectAsStateWithLifecycle()
    val spokenText by viewModel.speechRecognizer.spokenText.collectAsStateWithLifecycle()
    val speechError by viewModel.speechRecognizer.error.collectAsStateWithLifecycle()

    val alarms by viewModel.allAlarms.collectAsStateWithLifecycle()
    val activeTimer by viewModel.activeTimer.collectAsStateWithLifecycle()
    val pendingReminders by viewModel.pendingReminders.collectAsStateWithLifecycle()
    val events by viewModel.allEvents.collectAsStateWithLifecycle()
    val favoriteContacts by viewModel.favoriteContacts.collectAsStateWithLifecycle()
    val recentHistory by viewModel.recentHistory.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // Real-time clock ticker
    var currentTimeString by remember { mutableStateOf("") }
    var currentDateString by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        while (true) {
            val now = Date()
            currentTimeString = timeFormat.format(now)
            currentDateString = dateFormat.format(now)
            delay(1000)
        }
    }

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 4..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Hello"
        }
    }

    val suggestionChips = listOf(
        "Set an alarm for 7 AM",
        "Set a timer for 15 minutes",
        "Remind me to study at 8 PM",
        "What's on my calendar today?",
        "Call Rahul",
        "Kal subah 7 baje ka alarm laga do"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Greeting & Clock
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "$greeting! I'm ${settings.assistantName}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentDateString,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentTimeString,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Hero Voice Assistant Interaction Center
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_voice_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isListening) (if (isContinuous) "Continuous Listening Active" else "Listening to you...") else if (isContinuous) "Continuous Mode: Resuming..." else "Tap to speak",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isListening) CyanNeon else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    VoiceWaveform(
                        isListening = isListening,
                        rmsDb = rmsDb,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    MicrophoneButton(
                        isListening = isListening,
                        onClick = {
                            if (isListening) {
                                viewModel.speechRecognizer.stopListening()
                            } else {
                                val lang = if (settings.language == "hi" || settings.language == "hinglish") "hi-IN" else "en-US"
                                viewModel.speechRecognizer.startListening(lang)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Continuous Listening Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FilterChip(
                            selected = isContinuous,
                            onClick = { viewModel.toggleContinuousListening() },
                            label = {
                                Text(
                                    text = if (isContinuous) "Continuous Listening ON" else "Enable Continuous Listening",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            modifier = Modifier.testTag("toggle_continuous_listening_chip")
                        )
                    }

                    if (partialText.isNotEmpty() && isListening) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "\"$partialText\"",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = CyanNeon
                        )
                    } else if (spokenText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "\"$spokenText\"",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (speechError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = speechError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Quick Command Suggestions Chips
        item {
            Column {
                Text(
                    text = "Try Saying",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(suggestionChips) { chip ->
                        AssistChip(
                            onClick = {
                                viewModel.processUserInput(chip)
                                onNavigate("assistant")
                            },
                            label = { Text(chip) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("suggestion_chip_$chip")
                        )
                    }
                }
            }
        }

        // Upcoming Alarm Widget
        item {
            val nextAlarm = alarms.firstOrNull { it.isEnabled }
            DashboardWidgetCard(
                icon = Icons.Default.Alarm,
                iconTint = CyanNeon,
                title = "Upcoming Alarm",
                subtitle = nextAlarm?.let { "${it.getFormattedTime()} (${it.getRepeatSummary()})" } ?: "No active alarms",
                actionText = "Manage",
                onActionClick = { onNavigate("alarms") },
                trailingContent = {
                    if (nextAlarm != null) {
                        Switch(
                            checked = nextAlarm.isEnabled,
                            onCheckedChange = { viewModel.toggleAlarm(nextAlarm) },
                            modifier = Modifier.testTag("home_alarm_switch")
                        )
                    }
                }
            )
        }

        // Active Timer Widget
        if (activeTimer != null) {
            item {
                DashboardWidgetCard(
                    icon = Icons.Default.HourglassBottom,
                    iconTint = EmeraldNeon,
                    title = "Active Timer: ${activeTimer!!.label}",
                    subtitle = activeTimer!!.getFormattedTime() + if (activeTimer!!.isPaused) " (Paused)" else "",
                    actionText = "Timers",
                    onActionClick = { onNavigate("timers") },
                    trailingContent = {
                        IconButton(
                            onClick = {
                                if (activeTimer!!.isRunning) {
                                    viewModel.pauseTimer(activeTimer!!)
                                } else {
                                    viewModel.resumeTimer(activeTimer!!)
                                }
                            },
                            modifier = Modifier.testTag("home_timer_toggle")
                        ) {
                            Icon(
                                imageVector = if (activeTimer!!.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Toggle Timer",
                                tint = EmeraldNeon
                            )
                        }
                    }
                )
            }
        }

        // Next Reminder Widget
        item {
            val nextReminder = pendingReminders.firstOrNull()
            DashboardWidgetCard(
                icon = Icons.Default.NotificationsActive,
                iconTint = VioletNeon,
                title = "Next Reminder",
                subtitle = nextReminder?.let { "${it.title} • ${it.getFormattedDateTime()}" } ?: "All caught up! No pending reminders.",
                actionText = "Reminders",
                onActionClick = { onNavigate("reminders") }
            )
        }

        // Upcoming Calendar Event Widget
        item {
            val nextEvent = events.firstOrNull()
            DashboardWidgetCard(
                icon = Icons.Default.CalendarMonth,
                iconTint = Color(0xFFF59E0B),
                title = "Upcoming Meeting",
                subtitle = nextEvent?.let { "${it.title} (${it.getFormattedTimeRange()}) • ${it.location}" } ?: "Calendar is clear.",
                actionText = "Calendar",
                onActionClick = { onNavigate("calendar") }
            )
        }

        // Speed Dial / Quick Call Widget
        item {
            DashboardWidgetCard(
                icon = Icons.Default.Call,
                iconTint = EmeraldNeon,
                title = "Speed Dial & Phone",
                subtitle = if (favoriteContacts.isNotEmpty()) {
                    "Favorites: " + favoriteContacts.take(3).joinToString { it.name }
                } else {
                    "Tap to make calls or sync contacts"
                },
                actionText = "Contacts",
                onActionClick = { onNavigate("contacts") },
                trailingContent = {
                    if (favoriteContacts.isNotEmpty()) {
                        val topContact = favoriteContacts.first()
                        FilledTonalButton(
                            onClick = { viewModel.callContact(topContact) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = EmeraldNeon.copy(alpha = 0.2f),
                                contentColor = EmeraldNeon
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("quick_call_home_btn")
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(topContact.name.take(8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }

        // Recent Command Activity Header
        if (recentHistory.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Activity",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "View All",
                        style = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .clickable { onNavigate("history") }
                            .padding(4.dp)
                    )
                }
            }

            items(recentHistory.take(3)) { item ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.command,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = item.result,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.getFormattedTime().split(",")[0],
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardWidgetCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    actionText: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailingContent()
            } else {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onActionClick)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
