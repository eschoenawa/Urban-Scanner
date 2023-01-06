package de.eschoenawa.urbanscanner.model

data class PixelData(
    val x: Float,
    val y: Float,
    val z: Float,
    val confidence: UByte,
    val r: UByte,
    val g: UByte,
    val b: UByte,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null
) {
    val isGeoReferenced = latitude != null && longitude != null && altitude != null
    val normalizedConfidence = confidence.toInt() / 255f
}
