package com.enrpau.dualscreendex.parser.cli

import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializer
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.AttackMechanic
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.BattleMechanicsAbi
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.BattleRecordAbi
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.BattleRoleContract
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.BattleRoleProvenance
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.MechanicPredicate
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.MoveMechanicsAbi
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.MultiplyAttack
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.ScalarField
import com.enrpau.dualscreendex.parser.dataset.abilities.analysis.ScalarWidth
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7InstructionSet
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ParseResult
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import com.enrpau.dualscreendex.parser.parse.ParserOrchestrator
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Read-only exact-first-50 survey. ROM-specific controls remain test diagnostics only. */
class Arm7MechanicsCompatibilitySurveyTest {
    @Test
    fun `analyzer-only proof never promotes the production integration stage`() {
        assertEquals(
            MechanicsStage.UNSUPPORTED,
            integrationStage(
                MechanicsStage.UNSUPPORTED,
                MechanicsStage.COMPLETE_PROOF,
            ),
        )
    }

    @Test
    fun `survey exact first fifty twice without inventing mechanics inputs`() {
        val manifestPath = configuredPath("DUALDEX_CORPUS_MANIFEST")
        val baselinePath = configuredPath("DUALDEX_REVIEW_BASELINE")
        val outputPath = configuredPath("DUALDEX_MECHANICS_SURVEY_OUTPUT")
        val manifest = JsonParser.parseString(Files.readString(manifestPath)).asJsonArray
        val selected = linkedMapOf<String, JsonObject>()
        manifest.forEach { element ->
            val row = element.asJsonObject
            selected.putIfAbsent(row.string("RomSha256").lowercase(), row)
        }
        val first50 = selected.values.take(50)
        assertEquals(50, first50.size)
        val baseline = baseline(baselinePath)

        val rows = first50.mapIndexed { zeroIndex, manifestRow ->
            survey(zeroIndex + 1, manifestRow, baseline)
        }
        val applicable = rows.filter { it.platform == Platform.GBA.name }
        val packet = SurveyPacket(
            schemaVersion = 4,
            baseCommit = configuredValue("DUALDEX_MECHANICS_SURVEY_COMMIT"),
            manifest = manifestPath.toAbsolutePath().toString(),
            total = rows.size,
            applicableGba = applicable.size,
            notApplicable = rows.size - applicable.size,
            completeProofs = applicable.count { it.run1.stage == MechanicsStage.COMPLETE_PROOF },
            analyzerOnlyProofs = applicable.count {
                it.run1.stage != MechanicsStage.COMPLETE_PROOF &&
                    it.analyzerRun1?.stage == MechanicsStage.COMPLETE_PROOF
            },
            withheld = applicable.count { it.run1.stage != MechanicsStage.COMPLETE_PROOF },
            deterministic = rows.all { it.deterministic },
            first33RegressionPassed = rows.take(33).all { it.first33Regression == true },
            stageCounts = applicable.groupingBy { it.run1.stage.name }.eachCount().toSortedMap(),
            structuralClusters = applicable.groupingBy {
                listOf(
                    it.family ?: "NO_FAMILY",
                    it.staticMoveAbi ?: "NO_MOVE_ABI",
                    it.run1.battleAbiCluster ?: "NO_BATTLE_ABI",
                    it.run1.routineCluster ?: "NO_ROUTINE",
                ).joinToString("|")
            }.eachCount().toSortedMap(),
            rows = rows,
        )
        outputPath.toAbsolutePath().parent?.let(Files::createDirectories)
        Files.writeString(
            outputPath,
            GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(packet) + "\n",
        )
        assertEquals(true, packet.deterministic)
    }

    private fun survey(
        index: Int,
        manifest: JsonObject,
        baseline: Map<String, BaselineObservation>,
    ): SurveyRow {
        val path = Path.of(manifest.string("ExtractedPath"))
        val expectedSha = manifest.string("RomSha256").lowercase()
        val bytes = Files.readAllBytes(path)
        val firstRom = RomImage(bytes)
        val secondRom = RomImage(bytes.copyOf())
        assertEquals(expectedSha, firstRom.sha256)
        assertEquals(expectedSha, secondRom.sha256)
        val first = ParserOrchestrator.analyze(firstRom)
        val second = ParserOrchestrator.analyze(secondRom)
        val firstLayout = selectedLayout(first)
        val secondLayout = selectedLayout(second)
        val firstReferenceErrors = referenceErrors(index, firstRom, first, firstLayout)
        val secondReferenceErrors = referenceErrors(index, secondRom, second, secondLayout)
        val firstMechanics = mechanics(firstRom, first, firstLayout)
        val secondMechanics = mechanics(secondRom, second, secondLayout)
        val firstAnalyzer = analyzerMechanics(firstRom, first, firstLayout)
        val secondAnalyzer = analyzerMechanics(secondRom, second, secondLayout)
        val deterministic = first.status == second.status &&
            first.selectedFamily == second.selectedFamily &&
            firstLayout?.resolvedDatasets?.moveDetails?.table == secondLayout?.resolvedDatasets?.moveDetails?.table &&
            firstLayout?.resolvedDatasets?.abilityNames?.baseAbilityCount ==
            secondLayout?.resolvedDatasets?.abilityNames?.baseAbilityCount &&
            firstReferenceErrors == secondReferenceErrors &&
            firstMechanics == secondMechanics &&
            firstAnalyzer == secondAnalyzer
        val prior = baseline[expectedSha]
        val regression = if (index <= 33) {
            prior != null &&
                prior.status == first.status.name &&
                prior.family == first.selectedFamily?.name &&
                prior.referenceErrors == firstReferenceErrors
        } else null
        return SurveyRow(
            index = index,
            name = manifest.string("EntryPath"),
            sha256 = expectedSha,
            platform = RomHeaderReader.read(firstRom).platform.name,
            headerTitle = first.header.title,
            gameCode = first.header.gameCode,
            parserStatus = first.status.name,
            family = first.selectedFamily?.name,
            staticMoveAbi = firstLayout?.resolvedDatasets?.moveDetails?.table?.abi?.name,
            staticMoveRoot = firstLayout?.resolvedDatasets?.moveDetails?.table?.offset,
            abilityCount = firstLayout?.resolvedDatasets?.abilityNames?.baseAbilityCount,
            run1 = firstMechanics,
            run2 = secondMechanics,
            analyzerRun1 = firstAnalyzer,
            analyzerRun2 = secondAnalyzer,
            analyzerDeterministic = firstAnalyzer == secondAnalyzer,
            deterministic = deterministic,
            first33Regression = regression,
            referenceErrors = firstReferenceErrors,
        )
    }

    private fun mechanics(
        rom: RomImage,
        parse: ParseResult,
        layout: ResolvedRomLayout?,
    ): MechanicsOutcome {
        if (parse.header.platform != Platform.GBA) {
            return MechanicsOutcome(MechanicsStage.NOT_APPLICABLE, "ARM7TDMI ability mechanics do not apply")
        }
        if (parse.status != SelectionStatus.SELECTED || layout == null) {
            return MechanicsOutcome(
                MechanicsStage.PARSER_LAYOUT_UNAVAILABLE,
                "parser did not select one engine layout",
            )
        }
        val move = layout.resolvedDatasets.moveDetails
            ?: return MechanicsOutcome(
                MechanicsStage.STATIC_TYPED_LAYOUT_UNAVAILABLE,
                "parser did not select a typed move ABI",
            )
        val ability = layout.resolvedDatasets.abilityNames
            ?: return MechanicsOutcome(
                MechanicsStage.STATIC_TYPED_LAYOUT_UNAVAILABLE,
                "parser did not select a typed ability domain",
            )
        val resolved = layout.resolvedDatasets.abilityMechanics
        if (resolved == null) {
            val evidence = parse.capabilities.firstOrNull { it.capability == RomCapability.ABILITY_MECHANICS }
            val stage = if (evidence?.status == CapabilityStatus.AMBIGUOUS) {
                MechanicsStage.AMBIGUOUS
            } else {
                MechanicsStage.UNSUPPORTED
            }
            return MechanicsOutcome(
                stage,
                evidence?.reasons?.joinToString("; ") ?: "parser did not propagate a complete mechanics proof",
                staticTypedCluster = "${move.table.abi.name}:${ability.baseAbilityCount}",
            )
        }
        val integration = MechanicsOutcome(
            stage = MechanicsStage.COMPLETE_PROOF,
            reason = "normal parser path propagated complete typed caller, field, predicate, effect, and writeback proof",
            staticTypedCluster = "${move.table.abi.name}:${ability.baseAbilityCount}",
            battleAbiCluster = buildString {
                append(
                    when (resolved.abi.roleContract) {
                        is BattleRoleContract.DirectPointers -> "DIRECT_POINTERS"
                        is BattleRoleContract.IndexedArray -> "INDEXED_ARRAY"
                    },
                )
                append(":stride=0x${resolved.abi.record.stride.toString(16)}")
                append(":attack=${resolved.abi.record.attack.label()}")
                append(":ability=${resolved.abi.record.ability.label()}")
            },
            routineCluster = buildString {
                append("THUMB_DAMAGE_DIRECT")
                append(":decodedCallers=${resolved.proof.decodedCallSites.size}")
                append(":roleProofs=${resolved.proof.callerEvidence.size}")
                append(":moveRefs=${resolved.proof.moveTableReferenceSites.size}")
            },
            routineEntry = resolved.routineEntry,
            battleArrayRoot = resolved.proof.callerEvidence.firstOrNull()?.battleArrayRoot,
            decodedCallSites = resolved.proof.decodedCallSites.size,
            provenCallerSites = resolved.proof.callerEvidence.size,
            moveReferenceSites = resolved.proof.moveTableReferenceSites.size,
            tuples = resolved.mechanics.sortedBy(AttackMechanic::abilityId).map(::tuple),
        )
        return integration.copy(stage = integrationStage(integration.stage, null))
    }

    private fun analyzerMechanics(
        rom: RomImage,
        parse: ParseResult,
        layout: ResolvedRomLayout?,
    ): MechanicsOutcome? {
        if (parse.header.platform != Platform.GBA ||
            parse.status != SelectionStatus.SELECTED ||
            layout == null
        ) return null
        val move = layout.resolvedDatasets.moveDetails ?: return null
        val ability = layout.resolvedDatasets.abilityNames ?: return null
        val sourceControl = sourceControls[rom.sha256] ?: return null
        val result = BattleRoleProvenance.analyze(
            rom,
            sourceControl.entry,
            sourceControl.instructionSet,
            sourceControl.abi(move.table.offset.toInt() + GBA_ROM_BASE),
            MAX_DECODED,
        )
        val oracleMatched = result.attackMechanics.toSet() == sourceControl.expected
        return MechanicsOutcome(
            stage = if (oracleMatched) MechanicsStage.COMPLETE_PROOF else MechanicsStage.SEMANTIC_MISMATCH,
            reason = if (oracleMatched) {
                "exact source-control tuples, no extras; ${result.incompletePaths} bounded paths withheld"
            } else {
                "semantic output differs from the exact source-control oracle; " +
                    "${result.incompletePaths} bounded paths withheld"
            },
            staticTypedCluster = "${move.table.abi.name}:${ability.baseAbilityCount}",
            battleAbiCluster = sourceControl.battleAbiCluster,
            routineCluster = sourceControl.routineCluster,
            decodedInstructions = result.decodedInstructions,
            incompletePaths = result.incompletePaths,
            tuples = result.attackMechanics.sortedBy(AttackMechanic::abilityId).map(::tuple),
        )
    }

    private fun integrationStage(
        integration: MechanicsStage,
        @Suppress("UNUSED_PARAMETER") analyzer: MechanicsStage?,
    ): MechanicsStage = integration

    private fun referenceErrors(
        index: Int,
        rom: RomImage,
        parse: ParseResult,
        layout: ResolvedRomLayout?,
    ): List<String> = if (index <= 33 && parse.status == SelectionStatus.SELECTED && layout != null) {
        CatalogSamples.from(CatalogMaterializer.materialize(rom, parse, layout)).referenceErrors
    } else emptyList()

    private fun tuple(mechanic: AttackMechanic): String = buildString {
        append(mechanic.abilityId)
        append(":attack:")
        append(mechanic.predicates.sortedBy { it.toString() }.joinToString("+") { predicate ->
            when (predicate) {
                is MechanicPredicate.AttackerAbility -> "ability=${predicate.abilityId}"
                is MechanicPredicate.AttackerStatusNonZero -> "status&0x${predicate.mask.toString(16)}!=0"
                is MechanicPredicate.MoveSplit -> "split=${predicate.splitId}"
            }
        })
        append(":x${mechanic.effect.numerator}/${mechanic.effect.denominator}")
    }

    private fun ScalarField.label(): String =
        "${width.name.lowercase()}@0x${offset.toString(16)}"

    private fun selectedLayout(parse: ParseResult): ResolvedRomLayout? =
        parse.probes.singleOrNull { it.family == parse.selectedFamily }?.resolvedLayout

    private fun configuredPath(name: String): Path {
        return Path.of(configuredValue(name))
    }

    private fun configuredValue(name: String): String {
        val configured = System.getenv(name)
        assumeTrue("set $name to run exact first-50 survey", !configured.isNullOrBlank())
        return configured!!
    }

    private fun baseline(path: Path): Map<String, BaselineObservation> {
        val root = JsonParser.parseString(Files.readString(path)).asJsonObject
        return root.getAsJsonArray("observations").associate { element ->
            val row = element.asJsonObject
            val observation = row.getAsJsonObject("observation")
            row.string("romSha256").lowercase() to BaselineObservation(
                status = observation.string("status"),
                family = observation.get("family")?.takeUnless { it.isJsonNull }?.asString,
                referenceErrors = observation.getAsJsonArray("referenceErrors")?.map { it.asString }.orEmpty(),
            )
        }
    }

    private fun JsonObject.string(name: String): String = get(name).asString

    private data class BaselineObservation(
        val status: String,
        val family: String?,
        val referenceErrors: List<String>,
    )

    private data class SurveyPacket(
        val schemaVersion: Int,
        val baseCommit: String,
        val manifest: String,
        val total: Int,
        val applicableGba: Int,
        val notApplicable: Int,
        val completeProofs: Int,
        val analyzerOnlyProofs: Int,
        val withheld: Int,
        val deterministic: Boolean,
        val first33RegressionPassed: Boolean,
        val stageCounts: Map<String, Int>,
        val structuralClusters: Map<String, Int>,
        val rows: List<SurveyRow>,
    )

    private data class SurveyRow(
        val index: Int,
        val name: String,
        val sha256: String,
        val platform: String,
        val headerTitle: String,
        val gameCode: String?,
        val parserStatus: String,
        val family: String?,
        val staticMoveAbi: String?,
        val staticMoveRoot: Long?,
        val abilityCount: Int?,
        val run1: MechanicsOutcome,
        val run2: MechanicsOutcome,
        val analyzerRun1: MechanicsOutcome?,
        val analyzerRun2: MechanicsOutcome?,
        val analyzerDeterministic: Boolean,
        val deterministic: Boolean,
        val first33Regression: Boolean?,
        val referenceErrors: List<String>,
    )

    private data class MechanicsOutcome(
        val stage: MechanicsStage,
        val reason: String,
        val staticTypedCluster: String? = null,
        val battleAbiCluster: String? = null,
        val routineCluster: String? = null,
        val decodedInstructions: Int? = null,
        val incompletePaths: Int? = null,
        val routineEntry: Int? = null,
        val battleArrayRoot: Int? = null,
        val decodedCallSites: Int? = null,
        val provenCallerSites: Int? = null,
        val moveReferenceSites: Int? = null,
        val tuples: List<String> = emptyList(),
    )

    private enum class MechanicsStage {
        NOT_APPLICABLE,
        PARSER_LAYOUT_UNAVAILABLE,
        STATIC_TYPED_LAYOUT_UNAVAILABLE,
        AMBIGUOUS,
        BUDGET,
        UNSUPPORTED,
        SEMANTIC_MISMATCH,
        COMPLETE_PROOF,
    }

    private data class SourceControl(
        val entry: Int,
        val instructionSet: Arm7InstructionSet,
        val battleAbiCluster: String,
        val routineCluster: String,
        val abi: (Int) -> BattleMechanicsAbi,
        val expected: Set<AttackMechanic>,
    )

    private companion object {
        const val GBA_ROM_BASE = 0x0800_0000
        const val MAX_DECODED = 4_096
        val classicExpected = setOf(
            AttackMechanic(
                37,
                setOf(MechanicPredicate.AttackerAbility(37), MechanicPredicate.MoveSplit(0)),
                MultiplyAttack(2, 1),
            ),
            AttackMechanic(
                74,
                setOf(MechanicPredicate.AttackerAbility(74), MechanicPredicate.MoveSplit(0)),
                MultiplyAttack(2, 1),
            ),
            AttackMechanic(
                55,
                setOf(MechanicPredicate.AttackerAbility(55), MechanicPredicate.MoveSplit(0)),
                MultiplyAttack(3, 2),
            ),
            AttackMechanic(
                62,
                setOf(
                    MechanicPredicate.AttackerAbility(62),
                    MechanicPredicate.AttackerStatusNonZero(0xFF),
                    MechanicPredicate.MoveSplit(0),
                ),
                MultiplyAttack(3, 2),
            ),
        )
        val sourceControls = mapOf(
            "01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c" to SourceControl(
                entry = 0x5097C,
                instructionSet = Arm7InstructionSet.THUMB,
                battleAbiCluster = "INDEXED_ARRAY:stride=0x5c:attack=u16@2:ability=u16@0x20:status=u32@0x50",
                routineCluster = "CLASSIC_CALC_ATTACK_Q4_12",
                abi = { moveRoot ->
                    BattleMechanicsAbi(
                        record = BattleRecordAbi(
                            stride = 0x5C,
                            attack = ScalarField(0x02, ScalarWidth.U16),
                            ability = ScalarField(0x20, ScalarWidth.U16),
                            status = ScalarField(0x50, ScalarWidth.U32),
                        ),
                        move = MoveMechanicsAbi(
                            tableRoot = moveRoot,
                            stride = 20,
                            effect = ScalarField(0, ScalarWidth.U16),
                            power = ScalarField(2, ScalarWidth.U16),
                            type = ScalarField(4, ScalarWidth.U8),
                            category = ScalarField(16, ScalarWidth.U8),
                            effectiveSplitContextPointer = 0x0202_3598,
                            effectiveSplitPackedField = ScalarField(0x2D4, ScalarWidth.U8),
                            effectiveSplitMask = 0x60,
                        ),
                        activeAbilityIds = setOf(37, 55, 62, 74),
                        roleContract = BattleRoleContract.IndexedArray(0x0202_30F8, 1, 2),
                        moveParameterRegister = 0,
                    )
                },
                expected = classicExpected,
            ),
        )
    }
}
