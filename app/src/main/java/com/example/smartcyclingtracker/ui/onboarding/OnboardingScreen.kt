package com.example.smartcyclingtracker.ui.onboarding

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartcyclingtracker.theme.*

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        // Background radial gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(ElectricGreen.copy(alpha = 0.08f), DeepNavy),
                        radius = 800f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(ElectricGreen, ElectricGreenDarker)
                        )
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

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Welcome to\nVeloTrack",
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tell us about yourself for personalized\ncalorie & performance tracking",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Form
            OnboardingForm(
                uiState = uiState,
                onNameChange = viewModel::updateName,
                onGenderChange = viewModel::updateGender,
                onAgeChange = viewModel::updateAge,
                onWeightChange = viewModel::updateWeight,
                onHeightChange = viewModel::updateHeight
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Save button
            Button(
                onClick = { viewModel.saveWithDefaults(onComplete) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricGreen,
                    contentColor = DeepNavy
                )
            ) {
                Text(
                    "GET STARTED",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Skip option
            TextButton(onClick = { viewModel.skipWithDefaults(onComplete) }) {
                Text(
                    "Skip — use defaults (75kg, 175cm, 35yo)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun OnboardingForm(
    uiState: OnboardingUiState,
    onNameChange: (String) -> Unit,
    onGenderChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onHeightChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Name field
        StyledTextField(
            value = uiState.name,
            onValueChange = onNameChange,
            label = "Your Name",
            leadingIcon = Icons.Default.Person,
            keyboardType = KeyboardType.Text
        )

        // Gender selector
        Text(
            "Gender",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GenderButton(
                modifier = Modifier.weight(1f),
                label = "Male",
                icon = Icons.Default.Male,
                isSelected = uiState.gender == "male",
                onClick = { onGenderChange("male") }
            )
            GenderButton(
                modifier = Modifier.weight(1f),
                label = "Female",
                icon = Icons.Default.Female,
                isSelected = uiState.gender == "female",
                onClick = { onGenderChange("female") }
            )
        }

        // Age, weight, height
        StyledTextField(
            value = uiState.age,
            onValueChange = onAgeChange,
            label = "Age (years)",
            leadingIcon = Icons.Default.Cake,
            keyboardType = KeyboardType.Number
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StyledTextField(
                modifier = Modifier.weight(1f),
                value = uiState.weightKg,
                onValueChange = onWeightChange,
                label = "Weight (kg)",
                leadingIcon = Icons.Default.FitnessCenter,
                keyboardType = KeyboardType.Decimal
            )
            StyledTextField(
                modifier = Modifier.weight(1f),
                value = uiState.heightCm,
                onValueChange = onHeightChange,
                label = "Height (cm)",
                leadingIcon = Icons.Default.Height,
                keyboardType = KeyboardType.Decimal
            )
        }
    }
}

@Composable
private fun StyledTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextSecondary) },
        leadingIcon = {
            Icon(leadingIcon, contentDescription = null, tint = ElectricGreen)
        },
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
private fun GenderButton(
    modifier: Modifier = Modifier,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) ElectricGreen.copy(alpha = 0.2f) else NavyCard
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) ElectricGreen else GlassBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) ElectricGreen else TextSecondary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = if (isSelected) ElectricGreen else TextSecondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
