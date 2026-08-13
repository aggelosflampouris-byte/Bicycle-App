package com.fitnessapp.tracker.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitnessapp.tracker.BuildConfig
import com.fitnessapp.tracker.data.local.ThemeMode
import com.fitnessapp.tracker.theme.*
import com.fitnessapp.tracker.ui.onboarding.OnboardingViewModel
import com.fitnessapp.tracker.updater.UpdaterViewModel

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
    updaterViewModel: UpdaterViewModel = hiltViewModel(),
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val onboardingState by onboardingViewModel.uiState.collectAsStateWithLifecycle()
    val updateState by updaterViewModel.uiState.collectAsStateWithLifecycle()

    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordMode by remember { mutableStateOf("EXPORT") } // EXPORT or IMPORT
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { 
            pendingUri = it
            passwordMode = "EXPORT"
            showPasswordDialog = true
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { 
            pendingUri = it
            passwordMode = "IMPORT"
            showPasswordDialog = true
        }
    }

    var showSaveSuccessSnack by remember { mutableStateOf(false) }
    var showPasswordChangedSnack by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(showSaveSuccessSnack) {
        if (showSaveSuccessSnack) {
            snackbarHostState.showSnackbar("✅ Profile saved!", duration = SnackbarDuration.Short)
            showSaveSuccessSnack = false
        }
    }

    LaunchedEffect(showPasswordChangedSnack) {
        if (showPasswordChangedSnack) {
            snackbarHostState.showSnackbar("🔒 Password changed successfully!", duration = SnackbarDuration.Short)
            showPasswordChangedSnack = false
        }
    }



    Scaffold(
        containerColor = DeepNavy,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            SettingsHeader()

            // ── Personal Details ─────────────────────────────────────────────────
            SettingsSectionCard(
                icon = Icons.Default.Person,
                title = "Personal Details",
                subtitle = "Used for accurate calorie and performance calculations"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Name
                    SettingsTextField(
                        value = onboardingState.name,
                        onValueChange = onboardingViewModel::updateName,
                        label = "Your Name",
                        leadingIcon = Icons.Default.Person
                    )

                    // Gender
                    Text("Gender", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GenderChip(
                            modifier = Modifier.weight(1f),
                            label = "Male",
                            icon = Icons.Default.Male,
                            isSelected = onboardingState.gender == "male",
                            onClick = { onboardingViewModel.updateGender("male") }
                        )
                        GenderChip(
                            modifier = Modifier.weight(1f),
                            label = "Female",
                            icon = Icons.Default.Female,
                            isSelected = onboardingState.gender == "female",
                            onClick = { onboardingViewModel.updateGender("female") }
                        )
                    }

                    // Age, Weight, Height
                    SettingsTextField(
                        value = onboardingState.age,
                        onValueChange = onboardingViewModel::updateAge,
                        label = "Age (years)",
                        leadingIcon = Icons.Default.Cake,
                        keyboardType = KeyboardType.Number
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsTextField(
                            modifier = Modifier.weight(1f),
                            value = onboardingState.weightKg,
                            onValueChange = onboardingViewModel::updateWeight,
                            label = "Weight (kg)",
                            leadingIcon = Icons.Default.FitnessCenter,
                            keyboardType = KeyboardType.Decimal
                        )
                        SettingsTextField(
                            modifier = Modifier.weight(1f),
                            value = onboardingState.heightCm,
                            onValueChange = onboardingViewModel::updateHeight,
                            label = "Height (cm)",
                            leadingIcon = Icons.Default.Height,
                            keyboardType = KeyboardType.Decimal
                        )
                    }

                    // Save button
                    Button(
                        onClick = {
                            onboardingViewModel.saveWithDefaults { showSaveSuccessSnack = true }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricGreen,
                            contentColor = DeepNavy
                        )
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        val buttonText = if (onboardingState.hasProfile) "Edit Profile" else "Save Profile"
                        Text(buttonText, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    }
                }
            }

            // ── Appearance ────────────────────────────────────────────────────────
            SettingsSectionCard(
                icon = Icons.Default.Palette,
                title = "Appearance",
                subtitle = "Choose your preferred colour theme"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeMode.values().forEach { mode ->
                        ThemeModeRow(
                            mode = mode,
                            isSelected = settingsState.themeMode == mode,
                            onClick = { settingsViewModel.setTheme(mode) }
                        )
                    }
                }
            }

            // ── Features ────────────────────────────────────────────────────────
            SettingsSectionCard(
                icon = Icons.Default.EmojiEvents,
                title = "Features",
                subtitle = "Enable or disable optional app features"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Daily Challenges",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Receive automatically generated daily goals.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = settingsState.challengesEnabled,
                        onCheckedChange = { settingsViewModel.setChallengesEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DeepNavy,
                            checkedTrackColor = ElectricGreen,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = NavyDarker
                        )
                    )
                }
            }

            // ── Data Management ────────────────────────────────────────────────────────
            SettingsSectionCard(
                icon = Icons.Default.Storage,
                title = "Data Backup & Restore",
                subtitle = "Safely store your data in a local JSON file"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { exportLauncher.launch("smart_cycling_backup.json") },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = !settingsState.isBackupRunning,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDarker, contentColor = TextPrimary)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = !settingsState.isBackupRunning,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VividCyan.copy(alpha = 0.2f), contentColor = VividCyan)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import", fontWeight = FontWeight.Bold)
                    }
                }
            }
            // ── App Version & Updates ─────────────────────────────────────────────
            SettingsSectionCard(
                icon = Icons.Default.SystemUpdate,
                title = "Smart Track v${BuildConfig.VERSION_NAME}",
                subtitle = "Checks GitHub Releases for new versions"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (updateState.isUpToDate) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.CheckCircle, null, tint = ElectricGreen, modifier = Modifier.size(18.dp))
                            Text("Up to date", color = ElectricGreen, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("Tap to check for updates", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = { updaterViewModel.checkForUpdates(silent = false) },
                        enabled = !updateState.isChecking && !updateState.isDownloading,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricGreen.copy(alpha = 0.15f),
                            contentColor = ElectricGreen
                        )
                    ) {
                        if (updateState.isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ElectricGreen, strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Checking…")
                        } else {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Check", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                // Share App Row
                HorizontalDivider(color = GlassBorder, thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showShareDialog = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ElectricGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.QrCode, null, tint = ElectricGreen, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Share App", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Show QR code for latest release", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
            
            // ── Account Management ────────────────────────────────────────
            SettingsSectionCard(
                icon = Icons.Default.ManageAccounts,
                title = "Account Management",
                subtitle = "Manage your cloud profile"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Change Password button
                    Button(
                        onClick = { showChangePasswordDialog = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VividCyan.copy(alpha = 0.15f),
                            contentColor = VividCyan
                        )
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Change Password", fontWeight = FontWeight.Bold)
                    }

                    // Log Out button
                    Button(
                        onClick = {
                            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                            auth.signOut()
                            onLogout()
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpeedRed.copy(alpha = 0.15f),
                            contentColor = SpeedRed
                        )
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Log Out", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showChangePasswordDialog) {
            ChangePasswordDialog(
                onDismiss = { showChangePasswordDialog = false },
                onSuccess = {
                    showChangePasswordDialog = false
                    showPasswordChangedSnack = true
                }
            )
        }

        if (showShareDialog) {
            QrCodeDialog(
                url = "https://github.com/aggelosflampouris-byte/Bicycle-App/releases/latest",
                onDismiss = { showShareDialog = false }
            )
        }

        if (showPasswordDialog) {
            var password by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { 
                    showPasswordDialog = false 
                    pendingUri = null
                },
                title = { Text(if (passwordMode == "EXPORT") "Set Backup Password" else "Enter Backup Password") },
                text = {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VividCyan,
                            focusedLabelColor = VividCyan
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPasswordDialog = false
                            pendingUri?.let { uri ->
                                if (passwordMode == "EXPORT") settingsViewModel.exportData(uri, password)
                                else settingsViewModel.importData(uri, password)
                            }
                            pendingUri = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VividCyan, contentColor = DeepNavy)
                    ) {
                        Text("Submit")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasswordDialog = false; pendingUri = null }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = NavyDarker,
                titleContentColor = TextPrimary,
                textContentColor = TextSecondary
            )
        }
    }
}

@Composable
private fun SettingsHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(ElectricGreen, ElectricGreenDarker))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Settings, null, tint = DeepNavy, modifier = Modifier.size(28.dp))
        }
        Column {
            Text("Settings", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            Text("Personalise your Smart Track experience",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Section header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, contentDescription = null, tint = ElectricGreen, modifier = Modifier.size(22.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            HorizontalDivider(color = GlassBorder, thickness = 0.5.dp)
            content()
        }
    }
}

@Composable
private fun ThemeModeRow(mode: ThemeMode, isSelected: Boolean, onClick: () -> Unit) {
    val (icon, label, desc) = when (mode) {
        ThemeMode.DARK -> Triple(Icons.Default.DarkMode, "Dark", "Deep navy with electric green accents")
        ThemeMode.LIGHT -> Triple(Icons.Default.LightMode, "Light", "Clean white with green accents")
        ThemeMode.SYSTEM -> Triple(Icons.Default.SettingsBrightness, "Follow System", "Matches your device setting")
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) ElectricGreen.copy(alpha = 0.12f) else Color.Transparent
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.5.dp,
            color = if (isSelected) ElectricGreen else GlassBorder
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, tint = if (isSelected) ElectricGreen else TextSecondary, modifier = Modifier.size(22.dp))
                Column {
                    Text(label, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) ElectricGreen else TextPrimary)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, null, tint = ElectricGreen, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun GenderChip(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) ElectricGreen.copy(alpha = 0.2f) else Color.Transparent
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.5.dp,
            color = if (isSelected) ElectricGreen else GlassBorder
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = if (isSelected) ElectricGreen else TextSecondary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall,
                color = if (isSelected) ElectricGreen else TextSecondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun SettingsTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextSecondary) },
        leadingIcon = { Icon(leadingIcon, contentDescription = null, tint = ElectricGreen) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ElectricGreen,
            unfocusedBorderColor = GlassBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = ElectricGreen,
            focusedContainerColor = NavyCard,
            unfocusedContainerColor = NavyCard
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true
    )
}

@Composable
fun QrCodeDialog(url: String, onDismiss: () -> Unit) {
    val bitmap = remember(url) {
        val writer = QRCodeWriter()
        val size = 512
        val bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bmp.asImageBitmap()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = NavyCard),
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = ElectricGreen,
                    modifier = Modifier.size(32.dp)
                )
                
                Text(
                    text = "Scan to Download",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                
                Text(
                    text = "Scan this QR code to download the latest Smart Track APK directly from GitHub.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                // The QR Code Image
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NavyDarker)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(url))
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Link",
                            tint = ElectricGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricGreen.copy(alpha = 0.15f),
                        contentColor = ElectricGreen
                    )
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
