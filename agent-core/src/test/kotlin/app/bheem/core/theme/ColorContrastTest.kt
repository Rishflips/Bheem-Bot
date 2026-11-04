package app.bheem.core.theme

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class ColorContrastTest {
    private fun pairs(p: Palette): List<Triple<String, Int, Int>> = listOf(
        Triple("onSurface/background", p.onSurface, p.background),
        Triple("onSurface/surface", p.onSurface, p.surface),
        Triple("onSurface/surfaceContainer", p.onSurface, p.surfaceContainer),
        Triple("onSurface/surfaceContainerHigh", p.onSurface, p.surfaceContainerHigh),
        Triple("onSurfaceVariant/surface", p.onSurfaceVariant, p.surface),
        Triple("onSurfaceVariant/surfaceContainer", p.onSurfaceVariant, p.surfaceContainer),
        Triple("onSurfaceVariant/surfaceContainerHigh", p.onSurfaceVariant, p.surfaceContainerHigh),
        Triple("primary/background", p.primary, p.background),
        Triple("primary/surface", p.primary, p.surface),
        Triple("primary/surfaceContainer", p.primary, p.surfaceContainer),
        Triple("onPrimary/primary", p.onPrimary, p.primary),
        Triple("onPrimaryContainer/primaryContainer", p.onPrimaryContainer, p.primaryContainer),
        Triple("secondary/surface", p.secondary, p.surface),
        Triple("onSecondaryContainer/secondaryContainer", p.onSecondaryContainer, p.secondaryContainer),
        Triple("tertiary/surface", p.tertiary, p.surface),
        Triple("tertiary/surfaceContainer", p.tertiary, p.surfaceContainer),
        Triple("onTertiary/tertiary", p.onTertiary, p.tertiary),
        Triple("onTertiaryContainer/tertiaryContainer", p.onTertiaryContainer, p.tertiaryContainer),
        Triple("error/surface", p.error, p.surface),
        Triple("onError/error", p.onError, p.error),
        Triple("onErrorContainer/errorContainer", p.onErrorContainer, p.errorContainer),
    )

    @Test
    fun everyTextPairInBothThemesIsAtLeast4Point5() {
        for ((name, palette) in listOf("light" to BheemPalette.light, "dark" to BheemPalette.dark)) {
            for ((pair, fg, bg) in pairs(palette)) {
                val ratio = Contrast.ratio(fg, bg)
                assertTrue(ratio >= 4.5, "$name $pair is $ratio")
            }
        }
    }

    @Test
    fun theOutlineLineIsAtLeast3OnTheSurfaceInBothThemes() {
        for (palette in listOf(BheemPalette.light, BheemPalette.dark)) assertTrue(Contrast.ratio(palette.outline, palette.surface) >= 3.0)
    }

    @Test
    fun theFormulaAgreesWithTheRatiosInTheDesignDocument() {
        fun near(expected: Double, actual: Double) = assertTrue(abs(expected - actual) < 0.05, "expected $expected, got $actual")
        near(16.66, Contrast.ratio(BheemPalette.light.onSurface, BheemPalette.light.surface))
        near(8.95, Contrast.ratio(BheemPalette.light.primary, BheemPalette.light.surface))
        near(9.25, Contrast.ratio(BheemPalette.light.onPrimary, BheemPalette.light.primary))
        near(13.91, Contrast.ratio(BheemPalette.dark.onSurface, BheemPalette.dark.surface))
        near(10.47, Contrast.ratio(BheemPalette.dark.primary, BheemPalette.dark.surface))
    }
}
