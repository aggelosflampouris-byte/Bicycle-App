package com.fitnessapp.tracker.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.fitnessapp.tracker.theme.*

@Composable
fun DeleteConfirmationDialog(
    title: String = "Delete Confirmation",
    message: String = "Are you sure you want to delete this?",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyDarker,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(text = title, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(text = message)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SpeedRed,
                    contentColor = DeepNavy
                )
            ) {
                Text("Delete", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = TextSecondary
                )
            ) {
                Text("Cancel")
            }
        }
    )
}
