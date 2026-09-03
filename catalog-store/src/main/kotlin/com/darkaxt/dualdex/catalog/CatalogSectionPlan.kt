package com.darkaxt.dualdex.catalog

import com.enrpau.dualscreendex.parser.catalog.CatalogLocalization
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import java.util.Collections

internal class CatalogSectionPlan private constructor(
    sections: Set<String>,
    overlaysBySection: Map<String, LanguageTag>,
) {
    val sections: Set<String> = Collections.unmodifiableSet(LinkedHashSet(sections))
    val overlaysBySection: Map<String, LanguageTag> =
        Collections.unmodifiableMap(LinkedHashMap(overlaysBySection))
    val overlaySections: Set<String> = Collections.unmodifiableSet(LinkedHashSet(overlaysBySection.keys))

    init {
        require(sections == CatalogSchema.requiredSections + overlaysBySection.keys) {
            "catalog section plan must contain exactly the shared and localized sections"
        }
        require(sections.size <= CatalogSchema.maximumCatalogSections) {
            "catalog section plan exceeds the section-count limit"
        }
    }

    fun languageForOverlay(sectionName: String): LanguageTag? = overlaysBySection[sectionName]

    companion object {
        fun from(localization: CatalogLocalization): CatalogSectionPlan {
            val plan = from(localization.manifest)
            require(localization.overlays.keys == plan.overlaysBySection.values.toSet()) {
                "catalog localization does not match its persisted section plan"
            }
            return plan
        }

        fun from(manifest: RomLanguageManifest): CatalogSectionPlan {
            val resolvedLanguages = manifest.projections
                .filter { it.status == LanguageResolutionStatus.RESOLVED }
                .map { it.language }
            require(resolvedLanguages.size <= CatalogLocalization.MAXIMUM_LANGUAGE_OVERLAYS) {
                "catalog language overlay count limit exceeded"
            }
            val overlays = resolvedLanguages.associateTo(linkedMapOf()) { language ->
                overlaySectionName(language) to language
            }
            require(overlays.size == resolvedLanguages.size) {
                "catalog language projections collide after section-name normalization"
            }
            return CatalogSectionPlan(
                sections = CatalogSchema.requiredSections + overlays.keys,
                overlaysBySection = overlays,
            )
        }

        fun overlaySectionName(language: LanguageTag): String {
            val suffix = language.value
            require(suffix.length <= MAXIMUM_LANGUAGE_TAG_CHARACTERS) {
                "catalog overlay language tag is too long"
            }
            require(LanguageTag.of(suffix).value == suffix) {
                "catalog overlay language tag must be canonical"
            }
            return CatalogSchema.languageOverlayPrefix + suffix
        }

        fun parseOverlaySectionName(sectionName: String): LanguageTag? {
            if (!sectionName.startsWith(CatalogSchema.languageOverlayPrefix)) return null
            val suffix = sectionName.removePrefix(CatalogSchema.languageOverlayPrefix)
            require(suffix.isNotEmpty() && suffix.length <= MAXIMUM_LANGUAGE_TAG_CHARACTERS) {
                "catalog overlay section has an invalid language tag"
            }
            val language = LanguageTag.of(suffix)
            require(language.value == suffix && overlaySectionName(language) == sectionName) {
                "catalog overlay section language tag must be canonical"
            }
            return language
        }
    }
}

private const val MAXIMUM_LANGUAGE_TAG_CHARACTERS = 63
