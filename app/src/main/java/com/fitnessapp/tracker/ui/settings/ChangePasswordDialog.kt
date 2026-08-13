package com.fitnessapp.tracker.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitnessapp.tracker.theme.*

/**
 * Two-step change password dialog.
 * Step 1: Re-authenticate with current password (security gate).
 * Step 2: Enter and confirm the new password.
 */
@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit = onDismiss,
    viewModel: ChangePasswordViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = if (state.step == ChangePasswordStep.VERIFY) "Verify Identity" else "Set New Password",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            AnimatedContent(targetState = state.step, label = "change_pw_step") { step ->
                when (step) {
                    ChangePasswordStep.VERIFY -> VerifyStep(state, viewModel)
                    ChangePasswordStep.UPDATE -> UpdateStep(state, viewModel)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (state.step) {
                        ChangePasswordStep.VERIFY -> viewModel.verify()
                        ChangePasswordStep.UPDATE -> viewModel.updatePassword(onSuccess)
                    }
                },
                enabled = !state.isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricGreen,
                    contentColor = DeepNavy
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = DeepNavy,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (state.step == ChangePasswordStep.VERIFY) "Verify" else "Change Password",
                        fontWeight = FontWeight.Bold
                    )
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
private fun VerifyStep(state: ChangePasswordUiState, viewModel: ChangePasswordViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "For your security, please enter your current password to continue.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        PasswordField(
            value = state.currentPassword,
            onValueChange = viewModel::onCurrentPasswordChange,
            label = "Current Password"
        )
        ErrorText(state.error)
    }
}

@Composable
private fun UpdateStep(state: ChangePasswordUiState, viewModel: ChangePasswordViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Enter your new password. Minimum 8 characters.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        PasswordField(
            value = state.newPassword,
            onValueChange = viewModel::onNewPasswordChange,
            label = "New Password"
        )
        PasswordField(
            value = state.confirmNewPassword,
            onValueChange = viewModel::onConfirmNewPasswordChange,
            label = "Confirm New Password"
        )
        if (state.confirmNewPassword.isNotEmpty()) {
            val matches = state.newPassword == state.confirmNewPassword
            Text(
                text = if (matches) "✓ Passwords match" else "✗ Passwords do not match",
                color = if (matches) ElectricGreen else SpeedRed,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        ErrorText(state.error)
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (!error.isNullOrBlank()) {
        Text(
            text = error,
            color = SpeedRed,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Lock, null, tint = ElectricGreen) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle visibility",
                    tint = TextSecondary
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
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
