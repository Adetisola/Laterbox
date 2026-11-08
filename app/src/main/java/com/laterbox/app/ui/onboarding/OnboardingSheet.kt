package com.laterbox.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.ui.theme.LaterboxTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var currentStep by remember { mutableIntStateOf(1) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LaterboxTheme.colors.surfacePrimary,
        tonalElevation = 0.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LaterboxTheme.spacing.l, vertical = LaterboxTheme.spacing.m)
        ) {
            // Header: Step Indicators & Skip Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (currentStep == 1) LaterboxTheme.colors.accentPrimary
                                else LaterboxTheme.colors.borderSubtle
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (currentStep == 2) LaterboxTheme.colors.accentPrimary
                                else LaterboxTheme.colors.borderSubtle
                            )
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Skip",
                        style = LaterboxTheme.typography.metadataMono,
                        color = LaterboxTheme.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(LaterboxTheme.spacing.m))

            // Step Content with Crossfade
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboarding_step"
            ) { step ->
                if (step == 1) {
                    StepOneContent()
                } else {
                    StepTwoContent()
                }
            }

            Spacer(modifier = Modifier.height(LaterboxTheme.spacing.l))

            // Action Button
            Button(
                onClick = {
                    if (currentStep == 1) {
                        currentStep = 2
                    } else {
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LaterboxTheme.spacing.minTouchTarget),
                shape = RoundedCornerShape(LaterboxTheme.spacing.radiusSmall),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LaterboxTheme.colors.accentPrimary,
                    contentColor = LaterboxTheme.colors.surfaceElevated
                )
            ) {
                Text(
                    text = if (currentStep == 1) "Next: Quiet Library →" else "Enter Laterbox",
                    style = LaterboxTheme.typography.uiMedium.copy(fontWeight = FontWeight.Medium)
                )
            }

            Spacer(modifier = Modifier.height(LaterboxTheme.spacing.m))
        }
    }
}

@Composable
private fun StepOneContent() {
    Column {
        Text(
            text = "STEP 1 OF 2 — THE SHARE SHEET",
            style = LaterboxTheme.typography.metadataMono,
            color = LaterboxTheme.colors.accentPrimary,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(LaterboxTheme.spacing.xs))
        Text(
            text = "Save links instantly from any app.",
            style = LaterboxTheme.typography.editorialDisplay.copy(fontSize = 24.sp, lineHeight = 30.sp),
            color = LaterboxTheme.colors.textPrimary
        )
        Spacer(modifier = Modifier.height(LaterboxTheme.spacing.s))
        Text(
            text = "When you discover an article in Chrome, a video on YouTube, or a discussion on Reddit, simply tap Android's Share icon and select Laterbox. A quiet sheet saves it and returns you seamlessly to your task.",
            style = LaterboxTheme.typography.bodyPrimary,
            color = LaterboxTheme.colors.textSecondary
        )
        Spacer(modifier = Modifier.height(LaterboxTheme.spacing.m))

        // Visual diagram card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(LaterboxTheme.spacing.cardRadius))
                .background(LaterboxTheme.colors.surfaceSecondary)
                .border(
                    width = LaterboxTheme.spacing.hairline,
                    color = LaterboxTheme.colors.borderSubtle,
                    shape = RoundedCornerShape(LaterboxTheme.spacing.cardRadius)
                )
                .padding(LaterboxTheme.spacing.m),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Chrome • YouTube • Reddit  →  [ Share ]  →  Laterbox",
                style = LaterboxTheme.typography.metadataMono,
                color = LaterboxTheme.colors.textPrimary
            )
        }
    }
}

@Composable
private fun StepTwoContent() {
    Column {
        Text(
            text = "STEP 2 OF 2 — LOCAL-FIRST & PRIVATE",
            style = LaterboxTheme.typography.metadataMono,
            color = LaterboxTheme.colors.accentPrimary,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(LaterboxTheme.spacing.xs))
        Text(
            text = "Your calm, distraction-free reading library.",
            style = LaterboxTheme.typography.editorialDisplay.copy(fontSize = 24.sp, lineHeight = 30.sp),
            color = LaterboxTheme.colors.textPrimary
        )
        Spacer(modifier = Modifier.height(LaterboxTheme.spacing.s))
        Text(
            text = "No streaks, no notification badges, and no algorithmic recommendations. All data stays local to your phone with full-text search, offline access, and easy JSON backup anytime.",
            style = LaterboxTheme.typography.bodyPrimary,
            color = LaterboxTheme.colors.textSecondary
        )
        Spacer(modifier = Modifier.height(LaterboxTheme.spacing.m))

        // Visual diagram card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(LaterboxTheme.spacing.cardRadius))
                .background(LaterboxTheme.colors.surfaceSecondary)
                .border(
                    width = LaterboxTheme.spacing.hairline,
                    color = LaterboxTheme.colors.borderSubtle,
                    shape = RoundedCornerShape(LaterboxTheme.spacing.cardRadius)
                )
                .padding(LaterboxTheme.spacing.m),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓ Local SQLite   ✓ Full-text Search   ✓ Zero Telemetry",
                style = LaterboxTheme.typography.metadataMono,
                color = LaterboxTheme.colors.textPrimary
            )
        }
    }
}
