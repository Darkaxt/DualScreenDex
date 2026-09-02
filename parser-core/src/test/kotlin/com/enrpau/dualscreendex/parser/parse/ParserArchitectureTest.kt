package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndexFactory
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserArchitectureTest {
    @Test
    fun oneSessionSharesOneLazyGbaReferenceIndexAcrossEveryFamilyProbe() {
        val rom = RomImage(gbaRomBytes())
        var sessionsCreated = 0
        var referenceIndexesBuilt = 0

        val result = ParserOrchestrator.analyze(rom) { analyzedRom, header, exactProfile ->
            sessionsCreated++
            RomAnalysisSession(
                rom = analyzedRom,
                header = header,
                exactProfile = exactProfile,
                gbaReferenceIndexFactory = GbaReferenceIndexFactory { _, _ ->
                    referenceIndexesBuilt++
                    GbaReferenceIndex.countsOnlyForTesting(emptyMap())
                },
            )
        }

        assertEquals(1, sessionsCreated)
        assertEquals(1, referenceIndexesBuilt)
        assertEquals(EngineFamily.entries.toSet(), result.probes.map { it.family }.toSet())
    }

    @Test
    fun productionCallersCannotBypassTheSessionOwnedReferenceIndex() {
        val root = productionSourceRoot()
        val forbiddenReferences = Files.walk(root).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) && path.toString().endsWith(".kt") }
                .flatMap { path ->
                    Files.readAllLines(path)
                        .mapIndexedNotNull { index, line ->
                            val relative = root.relativize(path).toString().replace('\\', '/')
                            if (
                                !relative.contains("/analysis/") &&
                                !relative.endsWith("/parse/GbaReferenceIndex.kt") &&
                                line.contains("GbaReferenceIndexBuilder")
                            ) {
                                "${root.relativize(path)}:${index + 1}"
                            } else {
                                null
                            }
                        }
                        .stream()
                }
                .toList()
        }

        assertTrue(
            "production GBA reference-index callers must consume RomAnalysisSession: $forbiddenReferences",
            forbiddenReferences.isEmpty(),
        )
    }

    @Test
    fun catalogMaterializersCannotConstructAnalysisSessionsOrIndexes() {
        val catalogRoot = productionSourceRoot()
            .resolve("com/enrpau/dualscreendex/parser/catalog")
        val forbiddenReferences = Files.walk(catalogRoot).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) && path.toString().endsWith(".kt") }
                .flatMap { path ->
                    Files.readAllLines(path)
                        .mapIndexedNotNull { index, line ->
                            if (
                                line.contains("RomAnalysisSession(") ||
                                line.contains(".gbaReferenceIndex") ||
                                line.contains("GbaReferenceIndexBuilder")
                            ) {
                                "${catalogRoot.relativize(path)}:${index + 1}"
                            } else {
                                null
                            }
                        }
                        .stream()
                }
                .toList()
        }

        assertTrue(
            "catalog materializers must consume propagated layout evidence: $forbiddenReferences",
            forbiddenReferences.isEmpty(),
        )
    }

    @Test
    fun genThreeCatalogTypeChartConsumesThePropagatedTypedProjection() {
        val catalog = Files.readString(
            productionSourceRoot().resolve("com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt"),
        )
        val semantic = Files.readString(
            productionSourceRoot().resolve("com/enrpau/dualscreendex/parser/family/SemanticDomainStrategy.kt"),
        )

        assertTrue(catalog.contains("resolvedDatasets.typeChart?.catalogMatchups()"))
        assertEquals(1, "TypeChartResolver.resolve".toRegex().findAll(semantic).count())
    }

    @Test
    fun ordinaryGenThreeDescriptionConsumersUseThePropagatedTypedProjectionOnce() {
        val catalog = Files.readString(
            productionSourceRoot().resolve("com/enrpau/dualscreendex/parser/catalog/RelationshipMaterializers.kt"),
        )
        val semantic = Files.readString(
            productionSourceRoot().resolve("com/enrpau/dualscreendex/parser/family/SemanticDomainStrategy.kt"),
        )
        val orchestrator = Files.readString(
            productionSourceRoot().resolve("com/enrpau/dualscreendex/parser/parse/ParserOrchestrator.kt"),
        )

        assertTrue(catalog.contains("resolvedDatasets.descriptions?.catalogDescriptions()"))
        assertEquals(
            1,
            semantic.lineSequence().count {
                it.contains("DescriptionResolver(DescriptionCodec(codec), codec).resolve")
            },
        )
        assertTrue(semantic.contains("codec = identity.probeCodec"))
        assertTrue(!semantic.contains("DescriptionResolver().resolve"))
        assertEquals(1, "RelationshipMaterializers.descriptions".toRegex().findAll(orchestrator).count())
    }

    @Test
    fun ordinaryGenThreeEvolutionConsumersUseTheSelectedTypedProjectionOnce() {
        val catalog = Files.readString(
            productionSourceRoot().resolve("com/enrpau/dualscreendex/parser/catalog/RelationshipMaterializers.kt"),
        )
        val dependent = Files.readString(
            productionSourceRoot().resolve("com/enrpau/dualscreendex/parser/family/DependentDatasetsStrategy.kt"),
        )

        assertTrue(catalog.contains("resolvedDatasets.evolutions?.catalogEvolutions()"))
        assertEquals(1, dependent.lineSequence().count { it.contains("EvolutionResolver().resolveGen3") })
        assertTrue(dependent.contains("selectedLayout = selected"))
    }

    @Test
    fun ordinaryGenThreeLearnsetConsumersUseSelectedTypedLayoutsOnly() {
        val materializer = Files.readString(
            productionSourceRoot().resolve("com/enrpau/dualscreendex/parser/catalog/RelationshipMaterializers.kt"),
        )
        val rulesets = Files.readString(
            productionSourceRoot().resolve("com/enrpau/dualscreendex/parser/catalog/LearnsetRulesetMaterializer.kt"),
        )
        val dependent = Files.readString(
            productionSourceRoot().resolve("com/enrpau/dualscreendex/parser/family/DependentDatasetsStrategy.kt"),
        )
        val orchestrator = Files.readString(
            productionSourceRoot().resolve("com/enrpau/dualscreendex/parser/parse/ParserOrchestrator.kt"),
        )

        assertTrue(materializer.contains("resolvedDatasets.learnsets?.catalogPrimaryEntries()"))
        assertTrue(rulesets.contains("resolvedDatasets.learnsets ?: return emptyList()"))
        assertTrue(rulesets.contains("typed.catalogRulesetCandidates()"))
        assertEquals(1, dependent.lineSequence().count { it.contains("LearnsetResolver().resolveSelectedGen3") })
        assertTrue(orchestrator.contains("resolvedDatasets.learnsets?.catalogPrimaryEntries()?.keys"))
    }

    @Test
    fun ordinaryGenThreeMoveDetailsUseTheSelectedTypedProjectionOnce() {
        val materializer = Files.readString(
            productionSourceRoot().resolve("com/enrpau/dualscreendex/parser/catalog/RecordMaterializers.kt"),
        )
        val core = Files.readString(
            productionSourceRoot().resolve("com/enrpau/dualscreendex/parser/family/CoreDatasetsStrategy.kt"),
        )

        assertTrue(materializer.contains("resolvedDatasets.moveDetails?.catalogDetails()"))
        assertEquals(1, core.lineSequence().count { it.contains("MoveDetailsResolver.resolve(") })
        assertTrue(core.contains("selectedLayout = selected"))
    }

    @Test
    fun ordinaryGenThreeAbilityNamesUseTheSelectedTypedProjectionOnce() {
        val materializer = Files.readString(
            productionSourceRoot().resolve("com/enrpau/dualscreendex/parser/catalog/RecordMaterializers.kt"),
        )
        val catalog = Files.readString(
            productionSourceRoot().resolve("com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt"),
        )
        val semantic = Files.readString(
            productionSourceRoot().resolve("com/enrpau/dualscreendex/parser/family/SemanticDomainStrategy.kt"),
        )

        assertTrue(materializer.contains("resolvedDatasets.abilityNames?.catalogAbilities()"))
        assertTrue(!materializer.contains("referencedAbilityCount"))
        assertTrue(!catalog.contains("maximumReferencedAbilityId"))
        assertEquals(
            1,
            semantic.lineSequence().count {
                it.contains("AbilityNameResolver(AbilityNameCodec(identity.probeCodec)).resolve")
            },
        )
        assertTrue(semantic.contains("AbilityNameCodec(codec).decode(session, candidate, semanticDomain)"))
        assertTrue(!semantic.contains("AbilityNameResolver().resolve"))
        assertTrue(!semantic.contains("AbilityNameCodec().decode"))
        assertTrue(semantic.contains("selectedLayout = selected"))
    }

    @Test
    fun specializedMapTextPathsConsumeCodecTokensInsteadOfLegacyBytes() {
        listOf(
            "Gen1WorldMapResolver.kt",
            "Gen1LocalMapPoiResolver.kt",
            "Gen2LandmarkNameCodec.kt",
            "Gen2LocalMapPoiResolver.kt",
            "Gen3LocalMapPoiResolver.kt",
        ).forEach { fileName ->
            val source = Files.readString(
                productionSourceRoot().resolve("com/enrpau/dualscreendex/parser/parse/$fileName"),
            )
            assertTrue("$fileName still decodes one byte at a time", !source.contains(".decodeByte("))
            assertTrue("$fileName does not consume codec tokens", source.contains(".decodeToken("))
        }
    }

    private fun productionSourceRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath()
        return listOf(
            workingDirectory.resolve("parser-core/src/main/kotlin"),
            workingDirectory.resolve("src/main/kotlin"),
        ).firstOrNull(Files::isDirectory)
            ?: error("parser-core production source root not found from $workingDirectory")
    }

    private fun gbaRomBytes(): ByteArray = ByteArray(0x200).apply {
        byteArrayOf(
            0x24,
            0xFF.toByte(),
            0xAE.toByte(),
            0x51,
            0x69,
            0x9A.toByte(),
            0xA2.toByte(),
            0x21,
            0x3D,
            0x84.toByte(),
            0x82.toByte(),
            0x0A,
        ).copyInto(this, destinationOffset = 0x04)
        "UNKNOWN GBA".toByteArray().copyInto(this, destinationOffset = 0xA0)
        "ZZZZ".toByteArray().copyInto(this, destinationOffset = 0xAC)
    }
}
