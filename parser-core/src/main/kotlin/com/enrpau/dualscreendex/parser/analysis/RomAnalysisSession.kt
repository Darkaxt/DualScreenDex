package com.enrpau.dualscreendex.parser.analysis

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.RomProfile

/** Identity derived only after an exact profile digest matches the analyzed ROM digest. */
class ExactProfileIdentity private constructor(
    val name: String,
    val sha256: String,
) {
    override fun equals(other: Any?): Boolean =
        other is ExactProfileIdentity && name == other.name && sha256 == other.sha256

    override fun hashCode(): Int = 31 * name.hashCode() + sha256.hashCode()

    override fun toString(): String = "ExactProfileIdentity(name=$name, sha256=$sha256)"

    companion object {
        internal fun derive(
            profile: RomProfile,
            rom: RomImage,
            header: RomHeader,
        ): ExactProfileIdentity? {
            val normalized = profile.sha256.takeIf(::isAsciiSha256)?.lowercase() ?: return null
            if (
                !normalized.equals(rom.sha256, ignoreCase = false) ||
                profile.name.isBlank() ||
                profile.romSize != rom.size ||
                profile.platform != header.platform
            ) {
                return null
            }
            return ExactProfileIdentity(name = profile.name, sha256 = normalized)
        }

        private fun isAsciiSha256(value: String): Boolean = value.length == 64 && value.all { character ->
            character in '0'..'9' || character in 'a'..'f' || character in 'A'..'F'
        }
    }
}

/**
 * Immutable inputs and lazily shared evidence for one parser run.
 *
 * The reference factory is injectable so the single-build contract can be tested without timing
 * or a whole-ROM fixture. Production callers use the bounded legacy builder through the adapter.
 */
class RomAnalysisSession(
    val rom: RomImage,
    val header: RomHeader,
    exactProfile: RomProfile? = null,
    val limits: ResolutionLimits = ResolutionLimits(),
    private val gbaReferenceIndexFactory: GbaReferenceIndexFactory = DefaultGbaReferenceIndexFactory,
) {
    val exactProfileIdentity: ExactProfileIdentity? = exactProfile?.let {
        ExactProfileIdentity.derive(it, rom, header)
    }
    val exactProfileSnapshot: ExactProfileSnapshot? = exactProfileIdentity?.let { identity ->
        ExactProfileSnapshot.from(requireNotNull(exactProfile), identity)
    }

    val gbaReferenceIndex: GbaReferenceIndex? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (header.platform == Platform.GBA) {
            gbaReferenceIndexFactory.build(rom, limits)
        } else {
            null
        }
    }
}
