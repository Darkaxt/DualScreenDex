package com.darkaxt.dualdex.web

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** TEST ONLY. A reviewed reference-scoped oracle, never a production selector or a decoder. */
internal object WesternGen2SemanticOracle {
    const val proofSha256 = "4910fb487cb256cec0f0703f6301fe5167390ff121fb7cb3518acb5afbcb535b"
    const val contractSha256 = "33bccbdac83b58b7c07362ffa8e19c99d82d37a832c8d880eb47ac0ad826c296"
    const val reviewSha256 = "356d64dec41166d2d00e90adf9a79dec967e4c155e5eab11b517164cc45675c3"
    const val maxProofBytes = 10 * 1024 * 1024 // reviewed full proof: 9,729,357 bytes
    private const val proved = "PROVED_REFERENCE_SCOPED_ONLY"
    private val languages = setOf("en", "fr", "de", "it", "es")
    private val families = setOf("GOLD_SILVER", "CRYSTAL")

    data class Control(val names: Map<Int, String>, val references: Map<String, JsonObject>)

    fun requestedIds(family: String): Set<Int> {
        require(family in families)
        val common = setOf(2, 4, 5, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 21, 26, 27, 28, 29,
            31, 32, 33, 36, 38, 39, 40, 41, 43, 44, 49, 51, 52, 53, 54, 62, 63, 64, 65, 91, 106, 107,
            119, 128, 151, 152, 194, 204, 209, 211, 213, 217, 219, 226, 227, 231, 232, 235, 236, 238, 239, 249)
        return if (family == "CRYSTAL") common + setOf(20, 42, 74, 95, 121, 122, 123, 124, 131, 132, 138, 150, 173, 174) else common
    }

    fun readPinned(configured: String?, manifest: List<JsonObject>): Map<String, Control> {
        require(!configured.isNullOrBlank()) { "DUALDEX_WESTERN_ORACLE must point to the reviewed private full proof" }
        val path = Path.of(configured)
        require(Files.isRegularFile(path)) { "missing private oracle" }
        require(Files.size(path) in 1L..maxProofBytes.toLong()) { "oracle exceeds the 10 MiB bound or is empty" }
        val bytes = Files.newInputStream(path).use { it.readNBytes(maxProofBytes + 1) }
        return decodePinned(bytes, manifest)
    }

    fun decodePinned(bytes: ByteArray, manifest: List<JsonObject>): Map<String, Control> {
        require(bytes.size in 1..maxProofBytes) { "oracle exceeds the 10 MiB bound or is empty" }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(hash == proofSha256) { "oracle is not the exact independently reviewed proof" }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        return parseMetadata(text, manifest)
    }

    /** Unpinned structural entry point for synthetic tests ONLY; the real gate always calls readPinned. */
    internal fun parseMetadata(text: String, manifest: List<JsonObject>): Map<String, Control> {
        require(text.isNotBlank() && text.length <= maxProofBytes)
        val root = JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            val value = readJson(reader, 0).asJsonObject
            require(reader.peek() == JsonToken.END_DOCUMENT) { "trailing oracle content" }
            value
        }
        require(root.string("status") == proved)
        require(root.string("contractSha256") == contractSha256)
        require(manifest.size == 10)
        val expected = linkedMapOf<String, JsonObject>()
        val pairs = mutableSetOf<Pair<String, String>>()
        for (row in manifest) {
            val language = row.string("language")
            val family = row.string("family")
            require(language in languages && family in families)
            require(pairs.add(language to family)) { "duplicate manifest cell" }
            val hash = row.string("sha256")
            require(hash.matches(Regex("[0-9a-f]{64}")))
            require(expected.put(hash, row) == null) { "duplicate manifest identity" }
            row.string("file")
            require(row.integer("size") > 0)
        }
        require(pairs == languages.flatMap { language -> families.map { language to it } }.toSet())
        val controls = root.getAsJsonArray("controls")
        require(controls.size() == 10)
        val result = linkedMapOf<String, Control>()
        for (element in controls) {
            val row = element.asJsonObject
            val hash = row.string("sha256")
            val original = requireNotNull(expected[hash]) { "oracle identity outside original manifest" }
            require(row.string("language") == original.string("language"))
            val family = row.string("family")
            require(family == original.string("family") && row.string("status") == proved)
            val ids = row.getAsJsonArray("requestedIds").map { integer(it) }
            require(ids.size == ids.toSet().size && ids.toSet() == requestedIds(family)) { "wrong or duplicate requested IDs" }
            val names = linkedMapOf<Int, String>()
            for (item in row.getAsJsonArray("items")) {
                val label = item.asJsonObject
                val id = label.integer("itemId")
                require(id in ids && label.string("status") == "PROVED")
                val name = label.string("label")
                require(name.length <= 128 && name.none { it.isISOControl() || it.code == 0xfffd })
                require(names.put(id, name) == null) { "duplicate proved label" }
            }
            require(names.keys == ids.toSet()) { "incomplete proved labels" }
            val referenceCount = if (family == "CRYSTAL") 263 else 225
            require(row.integer("referenceCount") == referenceCount)
            val references = linkedMapOf<String, JsonObject>()
            for (reference in row.getAsJsonArray("references")) {
                val ref = reference.asJsonObject.getAsJsonObject("reference")
                validateReference(ref, ids.toSet(), original.integer("size"))
                require(references.put(ref.string("poiKey"), ref) == null) { "duplicate original reference" }
            }
            require(references.size == referenceCount)
            require(references.values.map { it.integer("itemId") }.toSet() == ids.toSet()) { "changed original reference item domain" }
            require(result.put(hash, Control(names.toMap(), references.toMap())) == null) { "duplicate oracle control" }
        }
        require(result.keys == expected.keys)
        require(result.values.sumOf { it.names.size } == 690 && result.values.sumOf { it.references.size } == 2440)
        return result
    }

    private fun validateReference(ref: JsonObject, ids: Set<Int>, romSize: Int) {
        val fields = setOf("poiKey", "itemId", "operandOffset", "kind", "baseAreaId", "mapGroupTable", "mapGroupBank",
            "mapHeader", "attributesBank", "attributes", "scriptsBank", "eventsRoot", "eventRow", "pointerField",
            "tileX", "tileY", "quantity", "collectionFlag")
        require(ref.keySet() == fields) { "malformed original reference metadata" }
        ref.string("poiKey")
        require(ref.integer("itemId") in ids)
        val hidden = when (ref.string("kind")) {
            "HIDDEN_EVENT" -> true
            "VISIBLE_OBJECT" -> false
            else -> error("unknown reference kind")
        }
        for (field in listOf("operandOffset", "mapGroupTable", "mapHeader", "attributes", "eventsRoot", "eventRow", "pointerField")) {
            require(ref.integer(field) in 0 until romSize)
        }
        for (field in listOf("mapGroupBank", "attributesBank", "scriptsBank", "tileX", "tileY")) require(ref.integer(field) in 0..255)
        for (field in listOf("baseAreaId", "collectionFlag")) require(ref.integer(field) in 0..65535)
        if (hidden) require(ref["quantity"].isJsonNull) else require(ref.integer("quantity") in 1..255)
    }

    private fun JsonObject.string(name: String): String {
        val value = requireNotNull(get(name)) { "missing $name" }
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "non-string $name" }
        return value.asString.also { require(it.isNotBlank()) { "blank $name" } }
    }

    private fun JsonObject.integer(name: String): Int = integer(requireNotNull(get(name)) { "missing $name" })
    private fun integer(value: JsonElement): Int {
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asString.matches(Regex("0|[1-9][0-9]*")))
        return value.asString.toInt()
    }

    // Gson's tree parser is lenient and collapses duplicate object members. Reject both before extraction.
    private fun readJson(reader: JsonReader, depth: Int): JsonElement {
        require(depth <= 64) { "oracle nesting exceeds bound" }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JsonObject().apply {
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    require(!has(name)) { "duplicate oracle member $name" }
                    add(name, readJson(reader, depth + 1))
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JsonArray().apply {
                reader.beginArray()
                while (reader.hasNext()) add(readJson(reader, depth + 1))
                reader.endArray()
            }
            JsonToken.STRING -> JsonPrimitive(reader.nextString())
            JsonToken.NUMBER -> JsonPrimitive(reader.nextString().toBigDecimal())
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            JsonToken.NULL -> { reader.nextNull(); JsonNull.INSTANCE }
            else -> error("malformed oracle JSON")
        }
    }
}
