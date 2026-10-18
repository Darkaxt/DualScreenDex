package com.enrpau.dualscreendex.parser.parse

internal class LocalMapRasterBudget(
    private val maxPixels: Long,
    private val maxEncodedBytes: Long,
) {
    var pixels: Long = 0
        private set
    var encodedBytes: Long = 0
        private set

    fun reserve(pixelCount: Int, assetBytes: Long) {
        require(pixelCount > 0 && assetBytes >= 0)
        require(pixelCount.toLong() <= maxPixels - pixels) {
            "raster-pixels budget: retained $pixels plus $pixelCount exceeds $maxPixels"
        }
        require(assetBytes <= maxEncodedBytes - encodedBytes) {
            "encoded-assets budget: retained $encodedBytes plus $assetBytes exceeds $maxEncodedBytes"
        }
        // A rejected asset consumes neither budget and cannot discard previously retained maps.
        pixels += pixelCount
        encodedBytes += assetBytes
    }
}
