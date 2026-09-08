package com.darkaxt.dualdex.web

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Test-only binding to a separately reconciled proof, not a proof evaluator or production selector. */
internal object WesternGen1SemanticOracle {
    const val proofSha256 = "97a2c0c61694adfaf3887f3bf5041ad82c2ae4b708e1afcd002b6d10e3d841c5"
    const val reviewSha256 = "2e29d0303cd08279fe0648a775e7f6eaf5f3a4f6ea1fc4d767cc2637837900e0"
    const val freezeSha256 = "bcbb6de0041cece82bc5ac5270055a983d1b89c8d7881af8c1c06e0f10effd29"
    const val maxProofBytes = 20 * 1024 * 1024 // reviewed acquisition batch: 15,626,284 bytes
    private const val metadataLimit = 4 * 1024 * 1024
    private const val complete = "STATIC_PROOF_COMPLETE_NOT_SEMANTIC_ACCEPTANCE"
    private const val derived = "STATIC_DERIVED_NOT_PRODUCTION_ACCEPTED"
    const val displayNormalization = "PokemonTextCodec display policy: replace Regex(\\s+) with U+0020, then Kotlin trim; no case/accent/Unicode normalization"

    data class Control(
        val identity: JsonObject,
        val rawNames: Map<Int, String>,
        val references: List<JsonObject>,
        val numericPois: List<JsonObject>,
        val baselineSnapshot: JsonObject,
    ) {
        val names: Map<Int, String> = rawNames.mapValues { displayName(it.value) }
    }

    /** Independent copy of the existing display policy. Never calls the production codec. */
    internal fun displayName(raw: String): String = raw.replace(Regex("\\s+"), " ").trim()

    internal fun <T> preflight(optIn: String?, externalSha: String?, metadata: () -> T): T {
        require(optIn == "1") { "DUALDEX_WESTERN_GEN1_SEMANTIC_ACCEPTANCE must be exactly 1" }
        require(externalSha == proofSha256) { "external SHA must identify the separately reviewed Gen I acquisition" }
        return metadata()
    }

    fun readConfigured(): List<Control> = preflight(System.getenv("DUALDEX_WESTERN_GEN1_SEMANTIC_ACCEPTANCE"),
        System.getenv("DUALDEX_WESTERN_GEN1_PROOF_SHA256")) {
        val freezePath = configured("DUALDEX_WESTERN_GEN1_INPUT_FREEZE")
        val freeze = strictJson(readPinned(freezePath, freezeSha256, metadataLimit))
        val manifestPath = configured("DUALDEX_WESTERN_MANIFEST")
        val inventoryPath = configured("DUALDEX_WESTERN_INVENTORY")
        require(manifestPath == Path.of(freeze.string("original35ManifestPath")))
        require(inventoryPath == Path.of(freeze.string("original35InventoryPath")))
        val pins = freeze.getAsJsonObject("pins")
        val manifestBytes = readPinned(manifestPath, freeze.string("manifestSha256"), metadataLimit)
        val inventoryBytes = readPinned(inventoryPath, pins.getAsJsonObject(inventoryPath.toString()).string("sha256"), metadataLimit)
        val selected = WesternGen1BaselineCapture.preflight("1", { manifestBytes }, { inventoryBytes })
        val bundles = mutableListOf<JsonObject>()
        val snapshots = linkedMapOf<String, JsonObject>()
        val frozenControls = freeze.getAsJsonArray("controls")
        require(frozenControls.size() == 10)
        for (element in frozenControls) {
            val row = element.asJsonObject
            val filename = row.string("bundle")
            require(Path.of(filename).fileName.toString() == filename && filename.endsWith(".metadata.json"))
            val bundle = strictJson(readPinned(freezePath.resolveSibling(filename), row.string("bundleSha256"), metadataLimit))
            require(bundle.getAsJsonObject("control") == row.getAsJsonObject("control"))
            require(bundle["requestedIds"] == row["requestedIds"])
            require(bundle.getAsJsonArray("references").size() == row.integer("referenceCount"))
            require(bundle.string("witnessesPath") == row.string("witnessesPath"))
            require(bundle.string("witnessesSha256") == row.string("witnessesSha256"))
            bundles += bundle
            val snapshotPath = Path.of(row.string("witnessesPath")).resolveSibling("catalog-snapshot.json")
            val snapshot = strictJson(readPinned(snapshotPath, pins.getAsJsonObject(snapshotPath.toString()).string("sha256"), metadataLimit))
            require(snapshots.put(row.getAsJsonObject("control").string("sha256"), snapshot) == null)
        }
        val proof = decodePinnedProof(readPinned(configured("DUALDEX_WESTERN_GEN1_PROOF"), proofSha256, maxProofBytes))
        bind(proof, selected, bundles, snapshots).values.toList()
    }

    private fun configured(name: String) = Path.of(requireNotNull(System.getenv(name)?.takeIf { it.isNotBlank() }) { "$name required" })

    private fun readPinned(path: Path, expected: String, limit: Int): ByteArray {
        require(path.fileName.toString().endsWith(".json")) { "only pinned JSON metadata is allowed during preflight" }
        require(Files.size(path) in 1L..limit.toLong())
        return Files.newInputStream(path).use { it.readNBytes(limit + 1) }.also {
            require(it.size in 1..limit && sha256(it) == expected) { "metadata size/hash mismatch: $path" }
        }
    }

    internal fun decodePinnedProof(bytes: ByteArray): JsonObject {
        require(bytes.size in 1..maxProofBytes && sha256(bytes) == proofSha256) { "not the independently reconciled full acquisition" }
        return strictJson(bytes)
    }

    /** Structural seam for fabricated tests. Actual execution must use readConfigured's external pins. */
    internal fun bind(proof: JsonObject, manifest: List<JsonObject>, bundles: List<JsonObject>, snapshots: Map<String, JsonObject>): Map<String, Control> {
        require(proof.string("status") == complete)
        notAccepted(proof)
        require(manifest.size == 10 && bundles.size == 10)
        val expected = linkedMapOf<String, JsonObject>()
        val cells = mutableSetOf<Pair<String, String>>()
        for (control in manifest) {
            val cell = control.string("language") to control.string("family")
            require(cells.add(cell))
            val hash = control.string("sha256")
            require(hash == WesternGen1BaselineCapture.controlHashes[cell])
            require(control.integer("size") == 1048576)
            control.string("file")
            require(expected.put(hash, control) == null)
        }
        require(cells == WesternGen1BaselineCapture.controlHashes.keys && snapshots.keys == expected.keys)
        val original = linkedMapOf<String, JsonObject>()
        for (bundle in bundles) {
            val identity = bundle.getAsJsonObject("control")
            val hash = identity.string("sha256")
            require(identity == expected[hash] && original.put(hash, bundle) == null)
        }
        require(original.keys == expected.keys)
        val result = linkedMapOf<String, Control>()
        val controls = proof.getAsJsonArray("controls")
        require(controls.size() == 10)
        for (element in controls) {
            val row = element.asJsonObject
            val identity = row.getAsJsonObject("control")
            val hash = identity.string("sha256")
            require(identity == expected[hash]) { "proof identity differs from original manifest" }
            val bundle = original.getValue(hash)
            require(row.string("status") == complete && row.boolean("staticProofComplete"))
            notAccepted(row)
            require(row.getAsJsonArray("errors").isEmpty)
            require(row.integer("inputReads") == 1 && row.integer("inputReadAttempts") == 1 && row.string("inputSha256") == hash)
            val counts = row.getAsJsonObject("counts")
            require(counts.integer("nominationPasses") == 1 && counts.integer("nominationScanBytes") == 1048576)
            require(counts.integer("wrapperCandidates") == 1 && counts.integer("hiddenCandidates") == 1)
            val ids = row.getAsJsonArray("requestedIds").map { integer(it) }
            require(ids.size == 62 && ids.toSet().size == 62 && ids.all { it in 1..255 })
            require(row["requestedIds"] == bundle["requestedIds"])
            val labels = row.getAsJsonObject("derivedLabels")
            val fields = row.getAsJsonObject("fields")
            require(labels.keySet() == ids.map { it.toString() }.toSet() && fields.keySet() == labels.keySet())
            val rawNames = ids.associateWith { id ->
                val label = labels.string(id.toString())
                require(label.length <= 128 && label.none { it.isISOControl() || it.code == 0xfffd } && displayName(label).isNotBlank())
                val field = fields.getAsJsonObject(id.toString())
                require(field.keySet() == setOf("status", "label") && field.string("status") == derived && field.string("label") == label)
                label
            }
            require(row["originalReferences"] == bundle["references"] && row["originalNumericPois"] == bundle["pois"])
            require(row["originalAuthorityBundle"] == bundle["observedAuthority"])
            val refs = row.getAsJsonArray("originalReferences").map { it.asJsonObject }
            val pois = row.getAsJsonArray("originalNumericPois").map { it.asJsonObject }
            val memberships = row.getAsJsonArray("references").map { it.asJsonObject }
            val yellow = identity.string("family") == "YELLOW"
            require(refs.size == if (yellow) 161 else 156)
            require(pois.size == refs.size && memberships.size == refs.size)
            require(refs.map { it.string("poiKey") }.toSet().size == refs.size)
            require(pois.map { it.string("key") }.toSet().size == pois.size)
            require(refs.map { it.integer("itemId") }.toSet() == ids.toSet())
            val numeric = pois.associateBy { it.string("key") }
            require(numeric.keys == refs.map { it.string("poiKey") }.toSet())
            require(refs.count { it.string("kind") == "HIDDEN_EVENT" } == if (yellow) 53 else 52)
            refs.forEachIndexed { index, ref ->
                validateReference(ref, ids.toSet(), numeric.getValue(ref.string("poiKey")))
                val membership = memberships[index]
                require(membership.string("poiKey") == ref.string("poiKey") && membership.integer("itemId") == ref.integer("itemId"))
                require(membership.string("status") == "REFERENCE_SCOPED_MEMBERSHIP")
            }
            val baseline = snapshots.getValue(hash)
            require(baseline.keySet() == setOf("languageManifest", "localization", "capabilities", "diagnostics", "balls", "pois"))
            require(result.put(hash, Control(identity.deepCopy(), rawNames, refs.map { it.deepCopy() }, pois.map { it.deepCopy() }, baseline.deepCopy())) == null)
        }
        require(result.keys == expected.keys && result.values.sumOf { it.rawNames.size } == 620 && result.values.sumOf { it.references.size } == 1585)
        return result
    }

    private fun validateReference(ref: JsonObject, ids: Set<Int>, poi: JsonObject) {
        require(ref.keySet() == setOf("poiKey", "itemId", "operandOffset", "kind", "baseAreaId", "sourceBank", "recordRoot",
            "mapHeader", "objectPointerField", "mapBankTable", "mapPointerTable", "hiddenHandler", "coordinateRoot", "coordinateIndex"))
        require(ref.integer("itemId") in ids && ref.integer("baseAreaId") in 0..255 && ref.integer("sourceBank") in 0..255)
        val hidden = when (ref.string("kind")) { "VISIBLE_OBJECT" -> false; "HIDDEN_EVENT" -> true; else -> error("non-Gen-I reference kind") }
        for (field in listOf("operandOffset", "recordRoot")) require(ref.integer(field) in 0 until 1048576)
        for (field in listOf("mapHeader", "objectPointerField", "mapBankTable", "mapPointerTable")) {
            if (hidden) require(ref[field].isJsonNull) else require(ref.integer(field) in 0 until 1048576)
        }
        for (field in listOf("hiddenHandler", "coordinateRoot", "coordinateIndex")) {
            if (!hidden) require(ref[field].isJsonNull) else require(ref.integer(field) in 0 until if (field == "coordinateIndex") 256 else 1048576)
        }
        require(poi.keySet() == setOf("key", "localMapKey", "baseAreaId", "kind", "tileX", "tileY", "item"))
        require(poi.string("key") == ref.string("poiKey") && poi.integer("baseAreaId") == ref.integer("baseAreaId"))
        require(poi.string("key").startsWith(poi.string("localMapKey") + if (hidden) "/hidden/" else "/object/"))
        require(poi.string("kind") == if (hidden) "HIDDEN_ITEM" else "VISIBLE_ITEM")
        require(poi.integer("tileX") in 0..255 && poi.integer("tileY") in 0..255)
        val item = poi.getAsJsonObject("item")
        require(item.keySet() == setOf("itemId", "collectionFlagId") && item.integer("itemId") == ref.integer("itemId"))
        if (hidden) require(item.integer("collectionFlagId") == ref.integer("coordinateIndex")) else require(item["collectionFlagId"].isJsonNull)
    }

    private fun notAccepted(row: JsonObject) {
        require(!row.boolean("semanticAcceptance") && row.string("requiredSemanticCompletion") == "NOT_ACCEPTED")
        require(row.getAsJsonObject("acceptedLabels").size() == 0)
    }
    private fun JsonObject.string(key: String): String = requireNotNull(get(key)).let {
        require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
        it.asString.also { value -> require(value.isNotBlank()) }
    }
    private fun JsonObject.boolean(key: String): Boolean = requireNotNull(get(key)).let {
        require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean); it.asBoolean
    }
    private fun JsonObject.integer(key: String): Int = integer(requireNotNull(get(key)))
    private fun integer(value: JsonElement): Int {
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asString.matches(Regex("0|[1-9][0-9]*")))
        return value.asString.toInt()
    }
    internal fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    internal fun strictJson(bytes: ByteArray): JsonObject {
        require(bytes.size in 1..maxProofBytes)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        return JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            val value = readJson(reader, 0).asJsonObject
            require(reader.peek() == JsonToken.END_DOCUMENT)
            value
        }
    }
    // Strict bounded tree reader also rejects duplicate members, which Gson's tree parser would collapse.
    private fun readJson(reader: JsonReader, depth: Int): JsonElement {
        require(depth <= 64)
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JsonObject().apply {
                reader.beginObject()
                while (reader.hasNext()) { val name = reader.nextName(); require(!has(name)); add(name, readJson(reader, depth + 1)) }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JsonArray().apply {
                reader.beginArray(); while (reader.hasNext()) add(readJson(reader, depth + 1)); reader.endArray()
            }
            JsonToken.STRING -> JsonPrimitive(reader.nextString())
            JsonToken.NUMBER -> JsonPrimitive(reader.nextString().toBigDecimal())
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            JsonToken.NULL -> { reader.nextNull(); JsonNull.INSTANCE }
            else -> error("malformed oracle JSON")
        }
    }
}
