package de.eschoenawa.urbanscanner.model

data class RawPixelData(
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
) : PixelData {
    val isGeoReferenced = latitude != null && longitude != null && altitude != null
    val normalizedConfidence = confidence.toInt() / 255f

    companion object {
        fun fromString(source: String): RawPixelData {
            val components = source.split(",")
            //TODO validate back and forth confidence doesn't loose accuracy
            return when (components.size) {
                10 -> {
                    RawPixelData(
                        components[0].toFloatOrNull()
                            ?: throw IllegalArgumentException("Malformed PixelData!"),
                        components[1].toFloatOrNull()
                            ?: throw IllegalArgumentException("Malformed PixelData!"),
                        components[2].toFloatOrNull()
                            ?: throw IllegalArgumentException("Malformed PixelData!"),
                        components[3].toFloatOrNull()?.times(255f)?.toInt()?.toUByte()
                            ?: throw IllegalArgumentException("Malformed PixelData!"),
                        components[4].toUByteOrNull()
                            ?: throw IllegalArgumentException("Malformed PixelData!"),
                        components[5].toUByteOrNull()
                            ?: throw IllegalArgumentException("Malformed PixelData!"),
                        components[6].toUByteOrNull()
                            ?: throw IllegalArgumentException("Malformed PixelData!"),
                        components[7].toDoubleOrNull()
                            ?: throw IllegalArgumentException("Malformed PixelData!"),
                        components[8].toDoubleOrNull()
                            ?: throw IllegalArgumentException("Malformed PixelData!"),
                        components[9].toDoubleOrNull()
                            ?: throw IllegalArgumentException("Malformed PixelData!"),
                    )
                }
                7 -> {
                    RawPixelData(
                        components[0].toFloatOrNull()
                            ?: throw IllegalArgumentException("Malformed PixelData!"),
                        components[1].toFloatOrNull()
                            ?: throw IllegalArgumentException("Malformed PixelData!"),
                        components[2].toFloatOrNull()
                            ?: throw IllegalArgumentException("Malformed PixelData!"),
                        components[3].toFloatOrNull()?.times(255f)?.toInt()?.toUByte()
                            ?: throw IllegalArgumentException("Malformed PixelData!"),
                        components[4].toUByteOrNull()
                            ?: throw IllegalArgumentException("Malformed PixelData!"),
                        components[5].toUByteOrNull()
                            ?: throw IllegalArgumentException("Malformed PixelData!"),
                        components[6].toUByteOrNull()
                            ?: throw IllegalArgumentException("Malformed PixelData!")
                    )
                }
                else -> {
                    throw IllegalArgumentException("Invalid PixelData!")
                }
            }
        }
    }

    override val stringRepresentation = if (isGeoReferenced) {
        "$x,$y,$z,$normalizedConfidence,${r.toInt()},${g.toInt()},${b.toInt()},$latitude,$longitude,$altitude"
    } else {
        "$x,$y,$z,$normalizedConfidence,${r.toInt()},${g.toInt()},${b.toInt()}"
    }
}
