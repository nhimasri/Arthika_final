package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import androidx.compose.ui.graphics.Brush

@Immutable
data class ArthikaSemanticColors(
    val heroCardBg: Color,
    val heroGradientStart: Color,
    val heroGradientMid: Color,
    val heroGradientEnd: Color,
    val heroCardTrack: Color,
    val heroCardFooterBg: Color,
    val mint: Color,
    val mintEnd: Color,
    val mintTint: Color,
    val softBlue: Color,
    val softBlueTint: Color,
    val warning: Color,
    val warningEnd: Color,
    val warningTint: Color,
    val danger: Color,
    val dangerEnd: Color,
    val dangerTint: Color,
    val neutralTint: Color,
    val cardBorder: Color,
    val secondaryText: Color
) {
    val heroBrush: Brush
        get() = Brush.linearGradient(listOf(heroGradientStart, heroGradientMid, heroGradientEnd))

    val mintBarBrush: Brush
        get() = Brush.horizontalGradient(listOf(mint, mintEnd))

    val warningBarBrush: Brush
        get() = Brush.horizontalGradient(listOf(warning, warningEnd))

    val dangerBarBrush: Brush
        get() = Brush.horizontalGradient(listOf(danger, dangerEnd))
}

private val LightSemanticColors = ArthikaSemanticColors(
    heroCardBg = DeepNavy,
    heroGradientStart = DeepNavy,
    heroGradientMid = DeepNavyElevated,
    heroGradientEnd = SapphireNavy,
    heroCardTrack = Color(0xFF283B61),
    heroCardFooterBg = Color(0x66101B32),
    mint = MintGreen,
    mintEnd = EmeraldMint,
    mintTint = SoftMintTint,
    softBlue = SecondaryBlue,
    softBlueTint = SoftBlueTint,
    warning = WarningAmber,
    warningEnd = WarningGold,
    warningTint = SoftWarningTint,
    danger = DangerCoral,
    dangerEnd = DangerCrimson,
    dangerTint = SoftDangerTint,
    neutralTint = SoftNeutralTint,
    cardBorder = LightBorder,
    secondaryText = LightSecondaryText
)

private val DarkSemanticColors = ArthikaSemanticColors(
    heroCardBg = Color(0xFF0F1B33),
    heroGradientStart = Color(0xFF0E182E),
    heroGradientMid = Color(0xFF162647),
    heroGradientEnd = Color(0xFF1D335E),
    heroCardTrack = Color(0xFF243558),
    heroCardFooterBg = Color(0x800B1325),
    mint = Color(0xFF42C78E),
    mintEnd = Color(0xFF2DD49B),
    mintTint = Color(0xFF14352A),
    softBlue = BrightCerulean,
    softBlueTint = Color(0xFF1A2B42),
    warning = WarningAmber,
    warningEnd = WarningGold,
    warningTint = Color(0xFF382716),
    danger = DangerCoral,
    dangerEnd = DangerCrimson,
    dangerTint = Color(0xFF3B201B),
    neutralTint = Color(0xFF222E46),
    cardBorder = DarkBorder,
    secondaryText = DarkSecondaryText
)

val LocalArthikaColors = staticCompositionLocalOf { LightSemanticColors }

private val LightColorScheme = lightColorScheme(
    primary = DeepNavy,
    onPrimary = Color.White,
    primaryContainer = SoftBlueTint,
    onPrimaryContainer = DeepNavy,
    secondary = SecondaryBlue,
    onSecondary = Color.White,
    secondaryContainer = SoftBlueTint,
    onSecondaryContainer = DeepNavy,
    tertiary = MintGreen,
    onTertiary = Color.White,
    tertiaryContainer = SoftMintTint,
    onTertiaryContainer = Color(0xFF0D472F),
    background = LightBackground,
    onBackground = LightPrimaryText,
    surface = LightSurface,
    onSurface = LightPrimaryText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightSecondaryText,
    outline = LightBorder,
    error = DangerCoral,
    onError = Color.White,
    errorContainer = SoftDangerTint,
    onErrorContainer = Color(0xFF7D2512)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF739FD0),
    onPrimary = Color(0xFF081426),
    primaryContainer = DeepNavyElevated,
    onPrimaryContainer = DarkPrimaryText,
    secondary = SecondaryBlue,
    onSecondary = Color.White,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkPrimaryText,
    tertiary = Color(0xFF42C78E),
    onTertiary = Color(0xFF08291B),
    tertiaryContainer = Color(0xFF14352A),
    onTertiaryContainer = Color(0xFFA8F0CE),
    background = DarkBackground,
    onBackground = DarkPrimaryText,
    surface = DarkSurface,
    onSurface = DarkPrimaryText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkSecondaryText,
    outline = DarkBorder,
    error = DangerCoral,
    onError = Color.White,
    errorContainer = Color(0xFF3B201B),
    onErrorContainer = Color(0xFFFFB4A1)
)

val ArthikaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK
}

@Composable
fun ArthikaTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalArthikaColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ArthikaTypography,
            shapes = ArthikaShapes,
            content = content
        )
    }
}

object ArthikaTheme {
    val colors: ArthikaSemanticColors
        @Composable get() = LocalArthikaColors.current
}
