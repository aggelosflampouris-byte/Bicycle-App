package com.fitnessapp.tracker.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitnessapp.tracker.theme.*
import kotlinx.coroutines.launch

@Composable
fun AiChatScreen(
    onBack: (() -> Unit)? = null,
    showBackButton: Boolean = (onBack != null),
    triggerAnalysis: Boolean = false,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    LaunchedEffect(triggerAnalysis) {
        if (triggerAnalysis) {
            viewModel.openSessionPicker()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = NavyDarker,
                drawerContentColor = TextPrimary,
                modifier = Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { scope.launch { drawerState.close() } }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close History", tint = TextPrimary)
                        }
                        Text("Chat History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    IconButton(onClick = { 
                        viewModel.createNewChatSession() 
                        scope.launch { drawerState.close() }
                    }) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "New Chat", tint = ElectricGreen)
                    }
                }
                HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 8.dp))
                
                var sessionToDelete by remember { mutableStateOf<Long?>(null) }
                
                if (sessionToDelete != null) {
                    com.fitnessapp.tracker.ui.components.DeleteConfirmationDialog(
                        onConfirm = {
                            viewModel.deleteSession(sessionToDelete!!)
                            sessionToDelete = null
                        },
                        onDismiss = { sessionToDelete = null }
                    )
                }

                LazyColumn {
                    items(
                        items = uiState.sessions,
                        key = { it.id }
                    ) { session ->
                        val isSelected = uiState.activeSessionId == session.id
                        NavigationDrawerItem(
                            label = { 
                                Text(
                                    text = session.title, 
                                    color = if (isSelected) DeepNavy else TextPrimary,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodyMedium
                                ) 
                            },
                            selected = isSelected,
                            onClick = {
                                viewModel.switchSession(session.id)
                                scope.launch { drawerState.close() }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = ElectricGreen,
                                unselectedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            badge = {
                                IconButton(
                                    onClick = { sessionToDelete = session.id },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete, 
                                        contentDescription = "Delete", 
                                        tint = if (isSelected) DeepNavy else SpeedRed, 
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepNavy)
                .then(
                    if (showBackButton) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier
                )
                .imePadding()
        ) {
        var showClearChatDialog by remember { mutableStateOf(false) }

        if (showClearChatDialog) {
            com.fitnessapp.tracker.ui.components.DeleteConfirmationDialog(
                title = "Clear Chat",
                message = "Are you sure you want to clear this entire conversation?",
                onConfirm = {
                    viewModel.clearChat()
                    showClearChatDialog = false
                },
                onDismiss = { showClearChatDialog = false }
            )
        }

            // App bar
            ChatAppBar(
                showBackButton = showBackButton,
                onBack = onBack, 
                onMenuClick = { scope.launch { drawerState.open() } },
                onClear = { showClearChatDialog = true }
            )

        // AI Coach identity banner
        PersonalCoachBanner()

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RectangleShape),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = uiState.messages,
                key = { it.id }
            ) { message ->
                ChatBubble(message = message)
            }

            // Loading indicator — show while waiting for HF API response
            if (uiState.isLoading) {
                item {
                    TypingIndicator()
                }
            }
        }

        var showPreRideDialog by remember { mutableStateOf(false) }

        if (showPreRideDialog) {
            PreRideBriefingDialog(
                onDismiss = { showPreRideDialog = false },
                onSubmit = { dist, weather, temp, windSpeed, windDir ->
                    showPreRideDialog = false
                    viewModel.triggerPreRideBriefing(dist, weather, temp, windSpeed, windDir)
                }
            )
        }

        // Quick Action Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                onClick = { viewModel.triggerFatigueAnalysis() },
                label = { Text("📈 Fatigue & Recovery Audit", style = MaterialTheme.typography.labelSmall) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = NavyCard,
                    labelColor = VividCyan
                ),
                border = BorderStroke(0.5.dp, VividCyan.copy(alpha = 0.5f))
            )
            SuggestionChip(
                onClick = { showPreRideDialog = true },
                label = { Text("🌦️ Pre-Ride & Nutrition Plan", style = MaterialTheme.typography.labelSmall) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = NavyCard,
                    labelColor = ElectricGreen
                ),
                border = BorderStroke(0.5.dp, ElectricGreen.copy(alpha = 0.5f))
            )
            SuggestionChip(
                onClick = { viewModel.sendMessage("💡 Give me 3 key technique and pacing tips for my next workout.") },
                label = { Text("💡 Pacing & Cadence Tips", style = MaterialTheme.typography.labelSmall) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = NavyCard,
                    labelColor = TextPrimary
                ),
                border = BorderStroke(0.5.dp, GlassBorder)
            )
        }

        // Input bar
        ChatInputBar(
            inputText = inputText,
            onInputChange = { inputText = it },
            isLoading = uiState.isLoading,
            onSend = {
                if (inputText.isNotBlank()) {
                    viewModel.sendMessage(inputText.trim())
                    inputText = ""
                }
            },
            onShareHistory = { viewModel.openSessionPicker() }
        )
        
        if (uiState.showSessionPicker) {
            SessionPickerBottomSheet(
                sessions = uiState.availableSessions,
                onDismiss = { viewModel.dismissSessionPicker() },
                onShare = { selectedIds -> viewModel.shareSelectedSessions(selectedIds) }
            )
        }
    }
    }
}

@Composable
private fun PreRideBriefingDialog(
    onDismiss: () -> Unit,
    onSubmit: (distanceKm: Double, weather: String, tempC: Double, windSpeed: Double, windDir: String) -> Unit
) {
    var distanceStr by remember { mutableStateOf("30") }
    var weatherStr by remember { mutableStateOf("Sunny / Clear") }
    var tempStr by remember { mutableStateOf("22") }
    var windSpeedStr by remember { mutableStateOf("15") }
    var windDirStr by remember { mutableStateOf("North") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = ElectricGreen)
                Text(
                    text = "Pre-Ride Strategy Planner",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter your planned session details to get custom pacing, wind tactics, and hydration targets:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = distanceStr,
                    onValueChange = { distanceStr = it },
                    label = { Text("Planned Distance (km)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricGreen)
                )
                OutlinedTextField(
                    value = tempStr,
                    onValueChange = { tempStr = it },
                    label = { Text("Temperature (°C)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricGreen)
                )
                OutlinedTextField(
                    value = windSpeedStr,
                    onValueChange = { windSpeedStr = it },
                    label = { Text("Wind Speed (km/h)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricGreen)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val dist = distanceStr.toDoubleOrNull() ?: 25.0
                    val temp = tempStr.toDoubleOrNull() ?: 20.0
                    val wind = windSpeedStr.toDoubleOrNull() ?: 10.0
                    onSubmit(dist, weatherStr, temp, wind, windDirStr)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen, contentColor = DeepNavy)
            ) {
                Text("Generate Strategy", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = NavyCard,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun ChatAppBar(
    showBackButton: Boolean,
    onBack: (() -> Unit)?,
    onMenuClick: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavyMedium)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBackButton && onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
        } else {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "Chat History", tint = TextPrimary)
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "AI Coach",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (showBackButton) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.History, contentDescription = "Chat History", tint = TextPrimary)
            }
        }
        IconButton(onClick = onClear) {
            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear chat", tint = SpeedRed)
        }
    }
}

@Composable
private fun PersonalCoachBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(ElectricGreen.copy(alpha = 0.15f), VividCyan.copy(alpha = 0.1f))
                )
            )
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(ElectricGreen, ElectricGreenDarker)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = DeepNavy,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "AI Coach",
                    style = MaterialTheme.typography.titleLarge,
                    color = ElectricGreen,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Powered by Qwen2.5-72B • Context-aware coaching",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(ElectricGreen.copy(alpha = 0.2f))
                    .align(Alignment.Bottom),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = ElectricGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Card(
                shape = RoundedCornerShape(
                    topStart = if (isUser) 16.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) ElectricGreen.copy(alpha = 0.2f) else NavyCard
                ),
                border = if (!isUser) BorderStroke(1.dp, GlassBorder) else null
            ) {
                Text(
                    text = message.text + if (message.isStreaming) "▌" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) ElectricGreen else TextPrimary,
                    modifier = Modifier.padding(12.dp),
                    lineHeight = 22.sp
                )
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(ElectricGreen.copy(alpha = 0.2f))
                    .align(Alignment.Bottom),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = ElectricGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(600, delayMillis = 0), RepeatMode.Reverse
        ), label = "dot1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(600, delayMillis = 200), RepeatMode.Reverse
        ), label = "dot2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(600, delayMillis = 400), RepeatMode.Reverse
        ), label = "dot3"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NavyCard)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(alpha1, alpha2, alpha3).forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .graphicsLayer { this.alpha = alpha }
                    .background(ElectricGreen)
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit,
    onShareHistory: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavyMedium)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onShareHistory,
            enabled = !isLoading,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Default.Analytics,
                contentDescription = "Share History",
                tint = if (!isLoading) ElectricGreen else TextDisabled
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text("Ask AI Coach...", color = TextDisabled)
            },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricGreen,
                unfocusedBorderColor = GlassBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = ElectricGreen,
                focusedContainerColor = NavyCard,
                unfocusedContainerColor = NavyCard
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            singleLine = true,
            enabled = !isLoading
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            enabled = inputText.isNotBlank() && !isLoading,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (inputText.isNotBlank() && !isLoading) ElectricGreen else NavyCard
                )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = ElectricGreen,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (inputText.isNotBlank()) DeepNavy else TextDisabled
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionPickerBottomSheet(
    sessions: List<com.fitnessapp.tracker.data.local.entity.WorkoutSessionEntity>,
    onDismiss: () -> Unit,
    onShare: (Set<Long>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NavyDarker,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Select Sessions to Share",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items(sessions, key = { it.id }) { session ->
                    val isSelected = selectedIds.contains(session.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selectedIds = if (isSelected) {
                                    selectedIds - session.id
                                } else {
                                    selectedIds + session.id
                                }
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = ElectricGreen,
                                uncheckedColor = TextSecondary,
                                checkmarkColor = DeepNavy
                            )
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            val dist = "%.1f".format(session.totalDistanceMeters / 1000.0)
                            val type = session.activityType
                            val date = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(session.startTime))
                            Text(
                                text = "$type - $date",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Distance: ${dist}km",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onShare(selectedIds) },
                enabled = selectedIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricGreen,
                    disabledContainerColor = NavyMedium,
                    contentColor = DeepNavy,
                    disabledContentColor = TextDisabled
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(
                    text = "Share ${selectedIds.size} Session(s)",
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
