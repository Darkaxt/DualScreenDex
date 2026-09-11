package com.darkaxt.dualdex.catalog

import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.security.DigestOutputStream
import java.security.MessageDigest

/**
 * Versioned logical identity of the actual persisted catalog payload, not of SQLite/gzip bytes.
 * Call on the same stable catalog snapshot before writing and on the independently reopened catalog.
 * This is evidence only: it neither validates semantic correctness nor replaces catalog equality.
 *
 * V1 binds ROM identity, storage/parser schemas and every section in the storage section plan.
 * It excludes persistence/source metadata and fields not represented by storage DTOs. Object keys
 * use Unicode scalar order, nulls are explicit, finite decimals use their exact shortest plain
 * decimal form (negative zero becomes zero), and strings are not Unicode-normalized. This is not
 * a general RFC 8785 or Python float-format implementation.
 *
 * Only the four persisted Set fields and two map-derived overlay DTO lists are reordered; all
 * other sequences remain significant. Review this inventory when changing storage models/schema.
 * Work is section-at-a-time: raw storage JSON and canonical sections share the persisted 128 MiB
 * section ceiling, total output allows the persisted 256 MiB catalog plus a bounded 1 MiB envelope,
 * and nesting is capped at 64. The JSON tree has bounded input, not a byte-exact heap budget.
 */
object CatalogLogicalDigest {
    const val version = 1
    const val maximumEnvelopeBytes = 1024 * 1024
    const val maximumBytes = CatalogSchema.maximumCatalogInflatedBytes + maximumEnvelopeBytes
    const val maximumSectionBytes = CatalogSchema.maximumSectionInflatedBytes

    @JvmStatic
    fun sha256(catalog: ParsedCatalog, maximumBytes: Int = CatalogLogicalDigest.maximumBytes): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val discard = object : OutputStream() {
            override fun write(value: Int) = Unit
            override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
        }
        writeCanonical(catalog, DigestOutputStream(discard, digest), maximumBytes)
        return digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }

    /** Leaves [output] open. On failure any partial output must be discarded; no digest is returned. */
    @JvmStatic
    fun writeCanonical(catalog: ParsedCatalog, output: OutputStream, maximumBytes: Int = CatalogLogicalDigest.maximumBytes) {
        require(maximumBytes in 1..CatalogLogicalDigest.maximumBytes) { "invalid logical catalog byte limit" }
        val plan = CatalogSectionPlan.from(catalog.localization)
        val bounded = CatalogInflatedOutputStream(output, "logical catalog", maximumBytes)
        fun literal(value: String) = bounded.write(value.toByteArray(Charsets.UTF_8))
        fun json(value: JsonElement) = CatalogCanonicalJson.write(value, bounded, minOf(maximumBytes, maximumSectionBytes))
        val identity = JsonObject().apply {
            addProperty("crc32", catalog.romCrc32)
            addProperty("family", catalog.family.name)
            addProperty("platform", catalog.platform.name)
            addProperty("sha256", catalog.romSha256)
        }
        // Envelope keys are already in scalar order; sections are streamed rather than accumulated.
        literal("{\"digestVersion\":$version,\"identity\":")
        json(identity)
        literal(",\"parserSchemaVersion\":${CatalogSchema.parserSchemaVersion},\"sections\":{")
        val codec = CatalogSectionCodec()
        plan.sections.sortedWith(CatalogCanonicalJson.scalarOrder).forEachIndexed { index, name ->
            if (index > 0) literal(",")
            json(JsonPrimitive(name))
            literal(":")
            val raw = ByteArrayOutputStream()
            codec.writeJsonSection(catalog, name, raw, maximumSectionBytes)
            val value = JsonParser.parseString(raw.toString(Charsets.UTF_8.name()))
            normalizeUnorderedFields(name, value)
            json(value)
        }
        literal("},\"storageSchemaVersion\":${CatalogSchema.version}}")
    }

    private fun normalizeUnorderedFields(name: String, value: JsonElement) {
        fun sort(owner: JsonObject, key: String, compare: Comparator<JsonElement>) {
            owner.add(key, JsonArray().also { result ->
                owner.getAsJsonArray(key).sortedWith(compare).forEach(result::add)
            })
        }
        val scalars = Comparator<JsonElement> { a, b -> CatalogCanonicalJson.scalarOrder.compare(a.asString, b.asString) }
        fun fields(vararg keys: String) = Comparator<JsonElement> { a, b ->
            keys.asSequence().map { key ->
                CatalogCanonicalJson.scalarOrder.compare(a.asJsonObject[key].asString, b.asJsonObject[key].asString)
            }.firstOrNull { it != 0 } ?: 0
        }
        when (name) {
            "encounters" -> value.asJsonArray.forEach { sort(it.asJsonObject, "windows", scalars) }
            "runtime_metadata" -> sort(value.asJsonObject, "areaBaseIds", scalars)
            "world_maps" -> value.asJsonObject.getAsJsonArray("regions").forEach { region ->
                region.asJsonObject.getAsJsonArray("locations").forEach { sort(it.asJsonObject, "baseAreaIds", scalars) }
            }
            "theme" -> sort(value.asJsonObject, "assetClasses", scalars)
            else -> if (CatalogSectionPlan.parseOverlaySectionName(name) != null) {
                sort(value.asJsonObject, "localizedCapabilities", fields("capability"))
                sort(value.asJsonObject, "worldLocationNames", fields("regionKey", "locationKey"))
            }
        }
    }
}

/** Internal wire-format implementation; independent vectors pin its byte contract. */
internal object CatalogCanonicalJson {
    val scalarOrder = Comparator<String> { a, b ->
        var i = 0
        var j = 0
        var result = 0
        while (i < a.length && j < b.length && result == 0) {
            val left = a.codePointAt(i)
            val right = b.codePointAt(j)
            result = left.compareTo(right)
            i += Character.charCount(left)
            j += Character.charCount(right)
        }
        if (result != 0) result else (a.length - i).compareTo(b.length - j)
    }

    @JvmStatic
    fun encode(value: JsonElement, maximumBytes: Int): ByteArray = ByteArrayOutputStream().also {
        write(value, it, maximumBytes)
    }.toByteArray()

    fun write(value: JsonElement, output: OutputStream, maximumBytes: Int) {
        require(maximumBytes in 1..CatalogLogicalDigest.maximumBytes) { "invalid canonical JSON byte limit" }
        val writer = OutputStreamWriter(CatalogInflatedOutputStream(output, "canonical JSON", maximumBytes), Charsets.UTF_8)
        fun visit(element: JsonElement, depth: Int) {
            require(depth <= 64) { "canonical JSON nesting limit exceeded" }
            when {
                element.isJsonNull -> writer.write("null")
                element.isJsonObject -> {
                    writer.write("{")
                    element.asJsonObject.entrySet().sortedWith { a, b -> scalarOrder.compare(a.key, b.key) }
                        .forEachIndexed { index, entry ->
                            if (index > 0) writer.write(",")
                            quote(entry.key, writer)
                            writer.write(":")
                            visit(entry.value, depth + 1)
                        }
                    writer.write("}")
                }
                element.isJsonArray -> {
                    writer.write("[")
                    element.asJsonArray.forEachIndexed { index, item ->
                        if (index > 0) writer.write(",")
                        visit(item, depth + 1)
                    }
                    writer.write("]")
                }
                else -> {
                    val primitive = element.asJsonPrimitive
                    when {
                        primitive.isString -> quote(primitive.asString, writer)
                        primitive.isBoolean -> writer.write(primitive.asBoolean.toString())
                        else -> writer.write(number(primitive, maximumBytes))
                    }
                }
            }
        }
        visit(value, 0)
        writer.flush()
    }

    private fun number(value: JsonPrimitive, maximumBytes: Int): String {
        val decimal = try { value.asString.toBigDecimal().stripTrailingZeros() }
        catch (invalid: NumberFormatException) { throw IllegalArgumentException("canonical JSON requires finite decimal numbers", invalid) }
        if (decimal.signum() == 0) return "0"
        val precision = decimal.precision().toLong()
        val scale = decimal.scale().toLong()
        val length = (if (decimal.signum() < 0) 1 else 0) + when {
            scale <= 0 -> precision - scale
            precision > scale -> precision + 1
            else -> 2 + scale
        }
        require(length <= maximumBytes) { "canonical JSON number exceeds byte limit" }
        return decimal.toPlainString()
    }

    private fun quote(value: String, writer: Writer) {
        writer.write("\"")
        var start = 0
        var index = 0
        while (index < value.length) {
            val character = value[index]
            val escape = when (character) {
                '"' -> "\\\""
                '\\' -> "\\\\"
                '\b' -> "\\b"
                12.toChar() -> "\\f"
                '\n' -> "\\n"
                '\r' -> "\\r"
                '\t' -> "\\t"
                else -> if (character.code < 32) "\\u" + character.code.toString(16).padStart(4, '0') else null
            }
            if (escape != null) {
                writer.write(value, start, index - start)
                writer.write(escape)
                start = index + 1
            } else if (character.isHighSurrogate()) {
                require(index + 1 < value.length && value[index + 1].isLowSurrogate()) { "unpaired JSON surrogate" }
                index++
            } else {
                require(!character.isLowSurrogate()) { "unpaired JSON surrogate" }
            }
            index++
        }
        writer.write(value, start, value.length - start)
        writer.write("\"")
    }
}
