package app.bheem.core.theme

import kotlin.math.pow

/** Colours as 0xRRGGBB. Plain data so a JVM test can check contrast; the app turns them into Compose colours. */
data class Palette(
    val background: Int,
    val surface: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val outline: Int,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val error: Int,
    val onError: Int,
    val errorContainer: Int,
    val onErrorContainer: Int,
)

/** Teal for Bheem's own voice, turmeric for what waits for the owner's tap, on warm paper and warm charcoal (docs/UI-DESIGN.md, section D). */
object BheemPalette {
    val light = Palette(
        background = 0xF8F4EE, surface = 0xFDFBF8, surfaceContainer = 0xF1ECE4, surfaceContainerHigh = 0xEAE4DA,
        onSurface = 0x1C1B19, onSurfaceVariant = 0x56534D, outline = 0x78746D,
        primary = 0x1E4D5C, onPrimary = 0xFFFFFF, primaryContainer = 0xCDE6EC, onPrimaryContainer = 0x07303B,
        secondary = 0x3F5A38, secondaryContainer = 0xD6E3D0, onSecondaryContainer = 0x1F2A1C,
        tertiary = 0x8A4500, onTertiary = 0xFFFFFF, tertiaryContainer = 0xFFDCC2, onTertiaryContainer = 0x331300,
        error = 0xB3261E, onError = 0xFFFFFF, errorContainer = 0xF9DEDC, onErrorContainer = 0x410E0B,
    )

    val dark = Palette(
        background = 0x101214, surface = 0x15171A, surfaceContainer = 0x1F2226, surfaceContainerHigh = 0x292D32,
        onSurface = 0xE6E2DB, onSurfaceVariant = 0xBCB7AE, outline = 0x8E8981,
        primary = 0x8ED0E0, onPrimary = 0x00353F, primaryContainer = 0x0F3E4C, onPrimaryContainer = 0xCDE6EC,
        secondary = 0xA9C7A0, secondaryContainer = 0x2F4229, onSecondaryContainer = 0xD6E3D0,
        tertiary = 0xFFB77E, onTertiary = 0x4A2800, tertiaryContainer = 0x6B3400, onTertiaryContainer = 0xFFDCC2,
        error = 0xF2B8B5, onError = 0x601410, errorContainer = 0x8C1D18, onErrorContainer = 0xF9DEDC,
    )
}

/** WCAG 2 contrast ratio between two 0xRRGGBB colours. */
object Contrast {
    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(rgb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((rgb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
