package com.csqtt.client

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.csqtt.client.ui.v2.design.V2DarkPalette
import com.csqtt.client.ui.v2.design.v2DarkColorsForPalette
import org.junit.Assert.*
import org.junit.Test

class V2DarkPaletteTest {
    @Test fun designPresetsKeepExistingDesignsAndAddBoth250Palettes() {
        assertEquals(4, UiDesignPresets.options.size)
        assertEquals(4, UiDesignPresets.options.map { it.first }.toSet().size)
        assertEquals("nordic_slate", UiDesignPresets.darkPalette(UiDesignPresets.NEW))
        assertEquals(V2DarkPalette.ORIGINAL, UiDesignPresets.darkPalette(UiDesignPresets.ORIGINAL_250))
        assertEquals(V2DarkPalette.SOFT, UiDesignPresets.darkPalette(UiDesignPresets.SOFT_250))
        UiDesignPresets.options.forEach { (key, _) -> assertEquals(key, UiDesignPresets.normalize(key)) }
        assertEquals(UiDesignPresets.NEW, UiDesignPresets.normalize("unknown"))
    }
    @Test fun originalUsesClassicTokensExactly() {
        val original = v2DarkColorsForPalette(V2DarkPalette.ORIGINAL)
        assertEquals(BrandDarkColorScheme.background, original.background)
        assertEquals(BrandDarkColorScheme.surface, original.surface)
        assertEquals(BrandDarkColorScheme.primary, original.accent)
        assertEquals(BrandDarkColorScheme.onSurface, original.textPrimary)
        assertEquals(BrandDarkColorScheme.outlineVariant, original.outline)
    }

    @Test fun softOnlyRaisesDarkSurfacesAndKeepsAccents() {
        val original = v2DarkColorsForPalette(V2DarkPalette.ORIGINAL)
        val soft = v2DarkColorsForPalette(V2DarkPalette.SOFT)
        assertEquals(Color(0xFF09090A), original.background)
        assertEquals(Color(0xFF101011), soft.background)
        assertTrue(soft.background.luminance() > original.background.luminance())
        assertTrue(soft.surface.luminance() > original.surface.luminance())
        assertEquals(original, soft.copy(background = original.background, surface = original.surface,
            surfaceElevated = original.surfaceElevated, field = original.field, navSelected = original.navSelected,
            bottomSurface = original.bottomSurface, terminal = original.terminal))
        assertEquals(V2DarkPalette.ORIGINAL, V2DarkPalette.normalize("indigo"))
    }
}
