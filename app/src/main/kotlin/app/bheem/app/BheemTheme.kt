package app.bheem.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bheem.core.theme.BheemPalette
import app.bheem.core.theme.Palette

private fun c(rgb: Int) = Color(0xFF000000L or rgb.toLong())

private fun Palette.toScheme(dark: Boolean): ColorScheme {
    val base = if (dark) {
        darkColorScheme(primary = c(primary), onPrimary = c(onPrimary))
    } else {
        lightColorScheme(primary = c(primary), onPrimary = c(onPrimary))
    }
    return base.copy(
        background = c(background), onBackground = c(onSurface),
        surface = c(surface), onSurface = c(onSurface),
        surfaceVariant = c(surfaceContainer), onSurfaceVariant = c(onSurfaceVariant),
        surfaceContainerLowest = c(surface), surfaceContainerLow = c(surface),
        surfaceContainer = c(surfaceContainer), surfaceContainerHigh = c(surfaceContainerHigh), surfaceContainerHighest = c(surfaceContainerHigh),
        outline = c(outline), outlineVariant = c(outline),
        primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer),
        secondary = c(secondary), onSecondary = c(surface),
        secondaryContainer = c(secondaryContainer), onSecondaryContainer = c(onSecondaryContainer),
        tertiary = c(tertiary), onTertiary = c(onTertiary),
        tertiaryContainer = c(tertiaryContainer), onTertiaryContainer = c(onTertiaryContainer),
        error = c(error), onError = c(onError),
        errorContainer = c(errorContainer), onErrorContainer = c(onErrorContainer),
    )
}

private val BheemTypography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val BheemShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** One fixed palette on every phone, light and dark. Phone wallpaper colours are deliberately not used. */
@Composable
fun BheemTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = (if (dark) BheemPalette.dark else BheemPalette.light).toScheme(dark),
        typography = BheemTypography,
        shapes = BheemShapes,
        content = content,
    )
}
