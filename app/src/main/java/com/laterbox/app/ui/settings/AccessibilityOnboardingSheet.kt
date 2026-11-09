package com.laterbox.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.laterbox.app.capture.accessibility.AccessibilityServiceStateHelper
import com.laterbox.app.capture.accessibility.AppExclusionItem
import com.laterbox.app.ui.components.LaterboxIcons
import com.laterbox.app.ui.components.LaterboxPrimaryButton
import com.laterbox.app.ui.components.LaterboxSecondaryButton
import com.laterbox.app.ui.components.LaterboxShapeTokens
import com.laterbox.app.ui.components.LaterboxSheetSurface
import com.laterbox.app.ui.theme.LaterboxTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityOnboardingSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    onEnableClicked: () -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
        contentColor = colors.textPrimary,
        shape = LaterboxShapeTokens.Sheet
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.base, vertical = spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = LaterboxIcons.Globe),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = colors.accentPrimary
                )
                Spacer(modifier = Modifier.width(spacing.sm))
                Text(
                    text = "Link Selection Shortcut",
                    style = typography.sheetTitle,
                    color = colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(spacing.sm))

            Text(
                text = "When enabled, Android delivers text-selection events to Laterbox so you can save links instantly with a floating shortcut when selecting text in supported apps.",
                style = typography.bodySecondary,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(spacing.md))

            // Honest privacy points box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(LaterboxShapeTokens.Card)
                    .background(colors.surfacePrimary)
                    .padding(spacing.md)
            ) {
                Column {
                    Text(
                        text = "How your privacy is protected",
                        style = typography.bodyPrimary.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(spacing.xs))
                    Text(
                        text = "• Processed 100% locally on your device in memory.\n" +
                            "• Only validated web links trigger a floating suggestion.\n" +
                            "• Unrelated text, passwords, and messages are discarded immediately and never saved, logged, or transmitted.\n" +
                            "• Even if disabled, the native 'Save to Laterbox' menu and Share sheet are always available.",
                        style = typography.metaText,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                LaterboxSecondaryButton(
                    text = "Not Now",
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(spacing.sm))
                LaterboxPrimaryButton(
                    text = "Open Settings",
                    onClick = {
                        onEnableClicked()
                        AccessibilityServiceStateHelper.openAccessibilitySettings(context)
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1.5f)
                )
            }

            Spacer(modifier = Modifier.height(spacing.md))
        }
    }
}

@Composable
fun ExcludedAppsDialog(
    excludedPackages: Set<String>,
    onTogglePackage: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing
    val context = LocalContext.current

    val apps = remember { AccessibilityServiceStateHelper.getInstalledLaunchableApps(context) }

    Dialog(onDismissRequest = onDismissRequest) {
        LaterboxSheetSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.base)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.base)
            ) {
                Text(
                    text = "Excluded Applications",
                    style = typography.sheetTitle,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                Text(
                    text = "Select apps where Laterbox should never offer floating link capture suggestions:",
                    style = typography.bodySecondary,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(spacing.sm))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        val isChecked = excludedPackages.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTogglePackage(app.packageName) }
                                .padding(vertical = spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { onTogglePackage(app.packageName) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = colors.accentPrimary,
                                    checkmarkColor = colors.backgroundPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(spacing.xs))
                            Text(
                                text = app.appName,
                                style = typography.bodyPrimary,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.md))
                LaterboxPrimaryButton(
                    text = "Done",
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
