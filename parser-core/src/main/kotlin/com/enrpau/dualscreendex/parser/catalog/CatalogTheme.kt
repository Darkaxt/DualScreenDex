package com.enrpau.dualscreendex.parser.catalog

enum class CatalogThemeMethod { DIRECT_UI_PALETTE, MULTI_ASSET_QUANTIZATION, NEUTRAL_FALLBACK }

enum class CatalogThemeAssetClass { INTERFACE, TRAINER, WORLD_MAP, LOCAL_MAP, SPECIES }

enum class CatalogThemePaletteModel { RGB, DMG_INTENSITY }

data class DirectCatalogThemePalette(
    val colors: List<Int>,
    val colorModel: CatalogThemePaletteModel,
)

data class CatalogThemeTokens(
    val field: Int,
    val fieldPattern: Int,
    val header: Int,
    val headerShadow: Int,
    val menu: Int,
    val menuShadow: Int,
    val panel: Int,
    val border: Int,
    val text: Int,
    val textShadow: Int,
    val accent: Int,
    val accentText: Int,
) {
    init {
        listOf(
            field, fieldPattern, header, headerShadow, menu, menuShadow,
            panel, border, text, textShadow, accent, accentText,
        ).forEach { require(it in 0..0xFFFFFF) { "theme colors must be 24-bit RGB" } }
    }
}

data class CatalogTheme(
    val method: CatalogThemeMethod,
    val assetClasses: Set<CatalogThemeAssetClass>,
    val contrastCorrected: Boolean,
    val tokens: CatalogThemeTokens,
) {
    init {
        validate()
    }

    fun validate(): CatalogTheme = apply {
        require(method == CatalogThemeMethod.NEUTRAL_FALLBACK || assetClasses.isNotEmpty())
        require(method != CatalogThemeMethod.NEUTRAL_FALLBACK || assetClasses.isEmpty())
        require(contrastRatio(tokens.text, tokens.panel) >= NORMAL_TEXT_CONTRAST)
        require(contrastRatio(tokens.accentText, tokens.accent) >= NORMAL_TEXT_CONTRAST)
        require(contrastRatio(tokens.border, tokens.panel) >= CONTROL_CONTRAST)
    }

    companion object {
        const val NORMAL_TEXT_CONTRAST = 4.5
        const val CONTROL_CONTRAST = 3.0

        fun neutral(): CatalogTheme = CatalogTheme(
            method = CatalogThemeMethod.NEUTRAL_FALLBACK,
            assetClasses = emptySet(),
            contrastCorrected = false,
            tokens = CatalogThemeTokens(
                field = 0x536052,
                fieldPattern = 0x5E6A5C,
                header = 0x315B43,
                headerShadow = 0x1C3426,
                menu = 0xE2D7AC,
                menuShadow = 0x776C4F,
                panel = 0xF8F2DA,
                border = 0x514538,
                text = 0x1B211C,
                textShadow = 0xFFFFFF,
                accent = 0xA53E2F,
                accentText = 0xFFFFFF,
            ),
        )

        internal fun contrastRatio(first: Int, second: Int): Double {
            val lighter = maxOf(relativeLuminance(first), relativeLuminance(second))
            val darker = minOf(relativeLuminance(first), relativeLuminance(second))
            return (lighter + 0.05) / (darker + 0.05)
        }

        private fun relativeLuminance(rgb: Int): Double {
            fun channel(value: Int): Double {
                val normalized = value / 255.0
                return if (normalized <= 0.04045) normalized / 12.92 else
                    Math.pow((normalized + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * channel(rgb shr 16 and 0xff) +
                0.7152 * channel(rgb shr 8 and 0xff) +
                0.0722 * channel(rgb and 0xff)
        }
    }
}
