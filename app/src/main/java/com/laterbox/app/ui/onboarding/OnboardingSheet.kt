package com.laterbox.app.ui.onboarding

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon as AndroidIcon
import android.os.Build
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.R
import com.laterbox.app.quicksave.QuickSaveTileService
import com.laterbox.app.ui.components.LaterboxSecondaryButton
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
                    (1..3).forEach { stepIndex ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (currentStep == stepIndex) LaterboxTheme.colors.accentPrimary
                                    else LaterboxTheme.colors.borderSubtle
                                )
                        )
                        if (stepIndex < 3) Spacer(modifier = Modifier.width(6.dp))
                    }
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
                when (step) {
                    1 -> StepOneContent()
                    2 -> StepTwoContent()
                    else -> StepThreeContent()
                }
            }

            Spacer(modifier = Modifier.height(LaterboxTheme.spacing.l))

            // Action Button
            Button(
                onClick = {
                    if (currentStep < 3) {
                        currentStep += 1
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
                    text = when (currentStep) {
                        1 -> "Next: Privacy & Offline →"
                        2 -> "Next: Quick Capture Tile →"
                        else -> "Enter Laterbox"
                    },
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
            text = "STEP 1 OF 3 — THE SHARE SHEET",
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
            text = "When you discover an article in Chrome, a video on YouTube, or a discussion on Reddit, tap Android's Share icon and select Laterbox. A quiet sheet saves it and returns you seamlessly to your task.",
            style = LaterboxTheme.typography.bodyPrimary,
            color = LaterboxTheme.colors.textSecondary
        )
        Spacer(modifier = Modifier.height(LaterboxTheme.spacing.m))

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
            text = "STEP 2 OF 3 — LOCAL-FIRST & PRIVATE",
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

@Composable
private fun StepThreeContent() {
    val context = LocalContext.current
    Column {
        Text(
            text = "STEP 3 OF 3 — QUICK SETTINGS TILE",
            style = LaterboxTheme.typography.metadataMono,
            color = LaterboxTheme.colors.accentPrimary,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(LaterboxTheme.spacing.xs))
        Text(
            text = "Save copied links with a single tap.",
            style = LaterboxTheme.typography.editorialDisplay.copy(fontSize = 24.sp, lineHeight = 30.sp),
            color = LaterboxTheme.colors.textPrimary
        )
        Spacer(modifier = Modifier.height(LaterboxTheme.spacing.s))
        Text(
            text = "Copy any webpage link, swipe down Android Quick Settings, and tap 'Save to Laterbox'. Laterbox reads your copied URL safely once in the foreground and opens a compact quick-save preview.",
            style = LaterboxTheme.typography.bodyPrimary,
            color = LaterboxTheme.colors.textSecondary
        )
        Spacer(modifier = Modifier.height(LaterboxTheme.spacing.m))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LaterboxSecondaryButton(
                text = "Add 'Save to Laterbox' Tile",
                onClick = {
                    val statusBarManager = context.getSystemService(StatusBarManager::class.java)
                    val componentName = ComponentName(context, QuickSaveTileService::class.java)
                    statusBarManager?.requestAddTileService(
                        componentName,
                        context.getString(R.string.save_to_laterbox),
                        AndroidIcon.createWithResource(context, R.drawable.ic_tile_save),
                        { it.run() },
                        { result ->
                            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                                Toast.makeText(context, "Tile added to Quick Settings!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
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
                    text = "Pull down Quick Settings  →  Edit (Pencil)  →  Drag 'Save to Laterbox'",
                    style = LaterboxTheme.typography.metadataMono,
                    color = LaterboxTheme.colors.textPrimary
                )
            }
        }
    }
}
