package com.fitnessapp.tracker.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitnessapp.tracker.theme.*

@Composable
fun AuthScreen(
    onLoginSuccess: () -> Unit,
    onSignUpSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(DeepNavy, NavyDarker))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // App logo/icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(ElectricGreen, ElectricGreenDarker))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsBike,
                    contentDescription = null,
                    tint = DeepNavy,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Smart Track",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Text(
                text = if (uiState.isLogin) "Welcome back! Sign in to continue."
                       else "Create your account to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            // ── Mode toggle tabs ──────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    listOf("Sign In" to true, "Sign Up" to false).forEach { (label, isLoginMode) ->
                        val isSelected = uiState.isLogin == isLoginMode
                        Button(
                            onClick = { if (!isSelected) viewModel.toggleAuthMode() },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) ElectricGreen else androidx.compose.ui.graphics.Color.Transparent,
                                contentColor = if (isSelected) DeepNavy else TextSecondary
                            ),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp)
                        ) {
                            Text(label, fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal, fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Form fields ───────────────────────────────────────────────────

            // Username (sign-up only)
            AnimatedVisibility(
                visible = !uiState.isLogin,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    AuthTextField(
                        value = uiState.username,
                        onValueChange = viewModel::onUsernameChange,
                        label = "Username",
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = ElectricGreen) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Email
            AuthTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = "Email",
                leadingIcon = { Icon(Icons.Default.Email, null, tint = ElectricGreen) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password
            AuthTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Password",
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = ElectricGreen) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password",
                            tint = TextSecondary
                        )
                    }
                }
            )

            // Confirm password (sign-up only)
            AnimatedVisibility(
                visible = !uiState.isLogin,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    AuthTextField(
                        value = uiState.confirmPassword,
                        onValueChange = viewModel::onConfirmPasswordChange,
                        label = "Confirm Password",
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = ElectricGreen) },
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle confirm password",
                                    tint = TextSecondary
                                )
                            }
                        }
                    )
                    // Password match indicator
                    AnimatedVisibility(visible = uiState.confirmPassword.isNotEmpty()) {
                        val matches = uiState.password == uiState.confirmPassword
                        Text(
                            text = if (matches) "✓ Passwords match" else "✗ Passwords do not match",
                            color = if (matches) ElectricGreen else SpeedRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }
                }
            }

            // Forgot password (sign-in only)
            AnimatedVisibility(
                visible = uiState.isLogin,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = { showForgotPasswordDialog = true }) {
                        Text(
                            text = "Forgot Password?",
                            color = VividCyan,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Error message ─────────────────────────────────────────────────
            AnimatedVisibility(visible = uiState.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = SpeedRed.copy(alpha = 0.12f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SpeedRed.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = uiState.error ?: "",
                        color = SpeedRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Primary action button ─────────────────────────────────────────
            Button(
                onClick = {
                    viewModel.authenticate(
                        onLoginSuccess = onLoginSuccess,
                        onSignUpSuccess = onSignUpSuccess
                    )
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !uiState.isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricGreen,
                    contentColor = DeepNavy
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = DeepNavy,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = if (uiState.isLogin) "Sign In" else "Create Account",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // ── Forgot Password Dialog ────────────────────────────────────────────────
    if (showForgotPasswordDialog) {
        ForgotPasswordDialog(
            initialEmail = uiState.email,
            onDismiss = { showForgotPasswordDialog = false },
            onSendReset = { email, onDone -> viewModel.sendPasswordReset(email, onDone) }
        )
    }
}

@Composable
private fun ForgotPasswordDialog(
    initialEmail: String,
    onDismiss: () -> Unit,
    onSendReset: (email: String, onDone: (success: Boolean, message: String) -> Unit) -> Unit
) {
    var email by remember { mutableStateOf(initialEmail) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text("Reset Password", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Enter your account email and we'll send you a password reset link.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; resultMessage = null },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, null, tint = ElectricGreen) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricGreen,
                        unfocusedBorderColor = GlassBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                if (resultMessage != null) {
                    Text(
                        text = resultMessage!!,
                        color = if (isSuccess) ElectricGreen else SpeedRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isSuccess) {
                        isSending = true
                        onSendReset(email) { success, message ->
                            isSending = false
                            isSuccess = success
                            resultMessage = message
                        }
                    } else {
                        onDismiss()
                    }
                },
                enabled = !isSending,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricGreen,
                    contentColor = DeepNavy
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DeepNavy, strokeWidth = 2.dp)
                } else {
                    Text(if (isSuccess) "Done" else "Send Link", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ElectricGreen,
            unfocusedBorderColor = GlassBorder,
            focusedLabelColor = ElectricGreen,
            unfocusedLabelColor = TextSecondary,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = ElectricGreen,
            focusedContainerColor = NavyCard,
            unfocusedContainerColor = NavyCard
        )
    )
}
