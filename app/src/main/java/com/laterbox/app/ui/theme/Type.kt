package com.laterbox.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.laterbox.app.R

val NewsreaderFamily = FontFamily(
    Font(R.font.newsreader, FontWeight.Normal),
    Font(R.font.newsreader, FontWeight.Medium),
    Font(R.font.newsreader, FontWeight.SemiBold),
    Font(R.font.newsreader_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.newsreader_italic, FontWeight.Medium, FontStyle.Italic)
)

val InterFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrainsmono, FontWeight.Normal),
    Font(R.font.jetbrainsmono, FontWeight.Medium)
)

@Immutable
data class LaterboxTypography(
    // Newsreader (Serif Display / Editorial)
    val editorialDisplay: TextStyle,
    val editorialTitle: TextStyle,
    val wordmark: TextStyle,
    val sheetTitle: TextStyle = editorialTitle.copy(fontSize = 20.sp, lineHeight = 26.sp),
    val displayLarge: TextStyle = editorialDisplay,

    // Inter (Sans-serif UI & Content)
    val detailTitle: TextStyle,
    val titleLarge: TextStyle = detailTitle.copy(fontSize = 18.sp, lineHeight = 24.sp),
    val cardTitle: TextStyle,
    val titleSmall: TextStyle = cardTitle,
    val bodyPrimary: TextStyle,
    val bodySecondary: TextStyle = bodyPrimary.copy(fontSize = 13.sp, lineHeight = 18.sp),
    val bodySmall: TextStyle = bodySecondary,
    val uiLabel: TextStyle,
    val uiMedium: TextStyle = uiLabel,
    val labelMedium: TextStyle = uiLabel.copy(fontSize = 13.sp, lineHeight = 18.sp),
    val fieldLabel: TextStyle = uiLabel.copy(fontSize = 12.sp, lineHeight = 16.sp),
    val buttonLabel: TextStyle = uiLabel.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    val badgeLabel: TextStyle = uiLabel.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold),

    // JetBrains Mono (Technical & Metadata)
    val metadataMono: TextStyle,
    val metaText: TextStyle = metadataMono,
    val codeText: TextStyle = metadataMono,
    val caption: TextStyle = metadataMono.copy(fontSize = 10.sp, lineHeight = 14.sp),
    val badgeText: TextStyle = metadataMono.copy(fontSize = 10.sp, lineHeight = 12.sp)
)

val DefaultLaterboxTypography = LaterboxTypography(
    editorialDisplay = TextStyle(
        fontFamily = NewsreaderFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    editorialTitle = TextStyle(
        fontFamily = NewsreaderFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.25).sp
    ),
    wordmark = TextStyle(
        fontFamily = NewsreaderFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.5).sp
    ),
    cardTitle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp
    ),
    detailTitle = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp
    ),
    bodyPrimary = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    uiLabel = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    metadataMono = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    )
)

val LocalLaterboxTypography = staticCompositionLocalOf { DefaultLaterboxTypography }

fun LaterboxTypography.scaled(scaleFactor: Float): LaterboxTypography {
    if (scaleFactor == 1.0f) return this
    fun TextStyle.scale(): TextStyle = this.copy(
        fontSize = (this.fontSize.value * scaleFactor).sp,
        lineHeight = (this.lineHeight.value * scaleFactor).sp
    )

    return LaterboxTypography(
        editorialDisplay = editorialDisplay.scale(),
        editorialTitle = editorialTitle.scale(),
        wordmark = wordmark.scale(),
        sheetTitle = sheetTitle.scale(),
        displayLarge = displayLarge.scale(),
        detailTitle = detailTitle.scale(),
        titleLarge = titleLarge.scale(),
        cardTitle = cardTitle.scale(),
        titleSmall = titleSmall.scale(),
        bodyPrimary = bodyPrimary.scale(),
        bodySecondary = bodySecondary.scale(),
        bodySmall = bodySmall.scale(),
        uiLabel = uiLabel.scale(),
        uiMedium = uiMedium.scale(),
        labelMedium = labelMedium.scale(),
        fieldLabel = fieldLabel.scale(),
        buttonLabel = buttonLabel.scale(),
        badgeLabel = badgeLabel.scale(),
        metadataMono = metadataMono.scale(),
        metaText = metaText.scale(),
        codeText = codeText.scale(),
        caption = caption.scale(),
        badgeText = badgeText.scale()
    )
}
