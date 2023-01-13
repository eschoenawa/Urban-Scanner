package de.eschoenawa.urbanscanner.model

data class PrecisePixelData(
    val x: Double,
    val y: Double,
    val z: Double,
    val confidence: UByte,
    val r: UByte,
    val g: UByte,
    val b: UByte
) : PixelData {
    val normalizedConfidence = confidence.toInt() / 255f

    companion object {
        fun fromString(source: String): PrecisePixelData {
            val components = source.split(",")
            //TODO validate back and forth confidence doesn't loose accuracy
            if (components.size != 7) throw IllegalArgumentException("Invalid PixelData!")
            return PrecisePixelData(
                components[0].toDoubleOrNull()
                    ?: throw IllegalArgumentException("Malformed PixelData!"),
                components[1].toDoubleOrNull()
                    ?: throw IllegalArgumentException("Malformed PixelData!"),
                components[2].toDoubleOrNull()
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
    }

    override val stringRepresentation =
        "$x,$y,$z,$normalizedConfidence,${r.toInt()},${g.toInt()},${b.toInt()}"
}
