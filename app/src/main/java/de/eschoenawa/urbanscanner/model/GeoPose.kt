package de.eschoenawa.urbanscanner.model

data class GeoPose(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val heading: Double = 0.0
) {
    val latitudeRad = Math.toRadians(latitude)
    val longitudeRad = Math.toRadians(longitude)

    fun toCsvString(): String {
        return "$latitude,$longitude,$altitude,$heading"
    }
}
