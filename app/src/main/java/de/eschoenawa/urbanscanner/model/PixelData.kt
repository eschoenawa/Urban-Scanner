package de.eschoenawa.urbanscanner.model

import dev.romainguy.kotlin.math.Float3

data class PixelData(
    val x: Float,
    val y: Float,
    val z: Float,
    val confidence: UByte,
    val r: UByte,
    val g: UByte,
    val b: UByte,
    val frame: Int
) {
    val normalizedConfidence = confidence.toInt() / 255f
    val position = Float3(x, y, z)

    companion object {
        fun fromString(source: String): PixelData {
            val components = source.split(",")
            return when (components.size) {
                8 -> {
                    PixelData(
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
                        components[7].toIntOrNull()
                            ?: throw IllegalArgumentException("Malformed PixelData!")
                    )
                }
                else -> {
                    throw IllegalArgumentException("Invalid PixelData!")
                }
            }
        }
    }

    val stringRepresentation =
        "$x,$y,$z,$normalizedConfidence,${r.toInt()},${g.toInt()},${b.toInt()},$frame"
}
