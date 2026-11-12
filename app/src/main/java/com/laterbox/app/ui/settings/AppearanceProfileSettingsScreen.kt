package com.laterbox.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laterbox.app.data.preferences.FontSizeScale
import com.laterbox.app.data.preferences.ThemeMode
import com.laterbox.app.data.preferences.UserPreferences
import com.laterbox.app.ui.theme.LaterboxTheme

@Composable
fun AppearanceProfileSettingsScreen(
    preferences: UserPreferences,
    onThemeModeChange: (ThemeMode) -> Unit,
    onFontSizeScaleChange: (FontSizeScale) -> Unit,
    onUserNameChange: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LaterboxTheme.colors
    val typography = LaterboxTheme.typography
    val spacing = LaterboxTheme.spacing

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundPrimary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSubpageHeader(
                title = "Appearance & Profile",
                onBackClick = onBackClick
            )

            Spacer(modifier = Modifier.height(spacing.xs))

            // Profile / Greeting Name
            SettingsSectionHeader(title = "Profile")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        text = "Your Name",
                        style = typography.uiMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Used locally to personalize your reminder greetings.",
                        style = typography.bodySecondary.copy(fontSize = 12.sp),
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(spacing.xxs))

                    var userNameInput by remember(preferences.userName) { mutableStateOf(preferences.userName) }

                    OutlinedTextField(
                        value = userNameInput,
                        onValueChange = {
                            userNameInput = it
                            onUserNameChange(it)
                        },
                        placeholder = {
                            Text(
                                text = "e.g. Alex",
                                style = typography.bodySecondary,
                                color = colors.textTertiary
                            )
                        },
                        singleLine = true,
                        textStyle = typography.bodyPrimary.copy(color = colors.textPrimary),
                        shape = RoundedCornerShape(spacing.radiusSmall),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentPrimary,
                            unfocusedBorderColor = colors.borderSubtle,
                            focusedContainerColor = colors.surfaceSecondary,
                            unfocusedContainerColor = colors.surfaceSecondary,
                            cursorColor = colors.accentPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.lg))

            // Appearance: Theme Mode & Font Size
            SettingsSectionHeader(title = "Appearance")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Text(
                        text = "Theme",
                        style = typography.uiMedium,
                        color = colors.textPrimary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        ThemeOptionChip(
                            label = "System Default",
                            isSelected = preferences.themeMode == ThemeMode.SYSTEM,
                            onClick = { onThemeModeChange(ThemeMode.SYSTEM) }
                        )
                        ThemeOptionChip(
                            label = "Light (Ivory)",
                            isSelected = preferences.themeMode == ThemeMode.LIGHT,
                            onClick = { onThemeModeChange(ThemeMode.LIGHT) }
                        )
                        ThemeOptionChip(
                            label = "Dark (Charcoal)",
                            isSelected = preferences.themeMode == ThemeMode.DARK,
                            onClick = { onThemeModeChange(ThemeMode.DARK) }
                        )
                    }

                    SettingsDivider()

                    Text(
                        text = "Reader Font Size",
                        style = typography.uiMedium,
                        color = colors.textPrimary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        FontSizeScale.values().forEach { scale ->
                            ThemeOptionChip(
                                label = scale.label,
                                isSelected = preferences.fontSizeScale == scale,
                                onClick = { onFontSizeScaleChange(scale) }
                            )
                        }
                    }

                    // Font preview
                    Text(
                        text = "The quick brown fox jumps over the lazy dog.",
                        style = typography.bodyPrimary.copy(
                            fontSize = (15 * preferences.fontSizeScale.scaleFactor).sp
                        ),
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = spacing.xs)
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
