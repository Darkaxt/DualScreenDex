package com.darkaxt.dualdex.save

import java.security.MessageDigest

object LevelUpRulesetDetectionFingerprint {
    fun create(selectors: List<SaveByteSelector>, detectedRulesetId: String): String? {
        if (detectedRulesetId.isBlank() || selectors.isEmpty()) return null
        if (selectors.map { it.rulesetId }.distinct().size != selectors.size) return null
        if (selectors.none { it.rulesetId == detectedRulesetId }) return null
        if (selectors.any { !it.isValidDescriptor() }) return null

        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateString("dualdex-level-up-selector-v1")
        selectors.sortedBy { it.rulesetId }.forEach { selector ->
            digest.updateString(selector.rulesetId)
            digest.updateInt(selector.saveBlock1ByteOffset)
            digest.updateInt(selector.mask)
            digest.updateInt(selector.expectedValue)
        }
        digest.updateString(detectedRulesetId)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun SaveByteSelector.isValidDescriptor(): Boolean =
        rulesetId.isNotBlank() &&
            saveBlock1ByteOffset >= 0 &&
            mask in 1..0x80 && mask and (mask - 1) == 0 &&
            expectedValue in 0..0xFF && expectedValue and mask == expectedValue

    private fun MessageDigest.updateString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        updateInt(bytes.size)
        update(bytes)
    }

    private fun MessageDigest.updateInt(value: Int) {
        update((value ushr 24).toByte())
        update((value ushr 16).toByte())
        update((value ushr 8).toByte())
        update(value.toByte())
    }
}
