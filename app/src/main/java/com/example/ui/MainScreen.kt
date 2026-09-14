package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.example.ui.screens.AlarmsScreen
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TimersScreen
import com.example.ui.theme.CyanNeon

data class NavItem(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: AssistantViewModel) {
    val currentRoute by viewModel.currentDestination.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pendingReminders by viewModel.pendingReminders.collectAsStateWithLifecycle()
    val activeTimer by viewModel.activeTimer.collectAsStateWithLifecycle()

    var showMoreMenu by remember { mutableStateOf(false) }

    val primaryNavItems = listOf(
        NavItem("home", "Home", Icons.Default.Home, "nav_home"),
        NavItem("assistant", "Assistant", Icons.AutoMirrored.Filled.Chat, "nav_assistant"),
        NavItem("alarms", "Alarms", Icons.Default.Alarm, "nav_alarms"),
        NavItem("timers", "Timers", Icons.Default.HourglassBottom, "nav_timers"),
        NavItem("reminders", "Reminders", Icons.Default.NotificationsActive, "nav_reminders")
    )

    val railItems = listOf(
        NavItem("home", "Home", Icons.Default.Home, "rail_home"),
        NavItem("assistant", "Assistant", Icons.AutoMirrored.Filled.Chat, "rail_assistant"),
        NavItem("alarms", "Alarms", Icons.Default.Alarm, "rail_alarms"),
        NavItem("timers", "Timers", Icons.Default.HourglassBottom, "rail_timers"),
        NavItem("reminders", "Reminders", Icons.Default.NotificationsActive, "rail_reminders"),
        NavItem("contacts", "Contacts", Icons.Default.Call, "rail_contacts"),
        NavItem("calendar", "Calendar", Icons.Default.CalendarMonth, "rail_calendar"),
        NavItem("history", "History", Icons.Default.History, "rail_history"),
        NavItem("settings", "Settings", Icons.Default.Settings, "rail_settings")
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp

        if (isWide) {
            // Adaptive Navigation Rail layout for tablets / landscape
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    header = {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 16.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CyanNeon),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("A", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                    },
                    modifier = Modifier.fillMaxHeight()
                ) {
                    railItems.forEach { item ->
                        NavigationRailItem(
                            selected = currentRoute == item.route,
                            onClick = { viewModel.setDestination(item.route) },
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = CyanNeon,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    ScreenContent(currentRoute, viewModel)
                }
            }
        } else {
            // Compact mobile layout with Bottom Navigation + Top Bar overflow
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(CyanNeon),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("A", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = settings.assistantName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.setDestination("contacts") },
                                modifier = Modifier.testTag("action_contacts")
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Contacts")
                            }

                            IconButton(
                                onClick = { viewModel.setDestination("calendar") },
                                modifier = Modifier.testTag("action_calendar")
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar")
                            }

                            IconButton(
                                onClick = { viewModel.setDestination("history") },
                                modifier = Modifier.testTag("action_history")
                            ) {
                                Icon(Icons.Default.History, contentDescription = "History")
                            }

                            Box {
                                IconButton(
                                    onClick = { showMoreMenu = true },
                                    modifier = Modifier.testTag("action_more")
                                ) {
                                    Icon(Icons.Default.MoreHoriz, contentDescription = "More options")
                                }

                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Contacts & Speed Dial") },
                                        leadingIcon = { Icon(Icons.Default.Call, contentDescription = null) },
                                        onClick = {
                                            viewModel.setDestination("contacts")
                                            showMoreMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Calendar") },
                                        leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                                        onClick = {
                                            viewModel.setDestination("calendar")
                                            showMoreMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Command History") },
                                        leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                        onClick = {
                                            viewModel.setDestination("history")
                                            showMoreMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings") },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        onClick = {
                                            viewModel.setDestination("settings")
                                            showMoreMenu = false
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        primaryNavItems.forEach { item ->
                            val isSelected = currentRoute == item.route
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { viewModel.setDestination(item.route) },
                                icon = {
                                    if (item.route == "reminders" && pendingReminders.isNotEmpty()) {
                                        BadgedBox(badge = { Badge { Text("${pendingReminders.size}") } }) {
                                            Icon(item.icon, contentDescription = item.title)
                                        }
                                    } else if (item.route == "timers" && activeTimer != null) {
                                        BadgedBox(badge = { Badge { Text("•") } }) {
                                            Icon(item.icon, contentDescription = item.title)
                                        }
                                    } else {
                                        Icon(item.icon, contentDescription = item.title)
                                    }
                                },
                                label = { Text(item.title, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CyanNeon,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    ScreenContent(currentRoute, viewModel)
                }
            }
        }
    }
}

@Composable
fun ScreenContent(route: String, viewModel: AssistantViewModel) {
    when (route) {
        "home" -> HomeScreen(
            viewModel = viewModel,
            onNavigate = { viewModel.setDestination(it) }
        )
        "assistant" -> AssistantScreen(viewModel = viewModel)
        "alarms" -> AlarmsScreen(viewModel = viewModel)
        "timers" -> TimersScreen(viewModel = viewModel)
        "reminders" -> RemindersScreen(viewModel = viewModel)
        "calendar" -> CalendarScreen(viewModel = viewModel)
        "contacts" -> ContactsScreen(viewModel = viewModel)
        "history" -> HistoryScreen(viewModel = viewModel)
        "settings" -> SettingsScreen(viewModel = viewModel)
        else -> HomeScreen(
            viewModel = viewModel,
            onNavigate = { viewModel.setDestination(it) }
        )
    }
}
