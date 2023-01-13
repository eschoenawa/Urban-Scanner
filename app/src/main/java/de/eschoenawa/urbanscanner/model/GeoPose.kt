package de.eschoenawa.urbanscanner.model

data class GeoPose(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val heading: Double = 0.0
) {
    val latitudeRad = Math.toRadians(latitude)
    val longitudeRad = Math.toRadians(longitude)
    val compassHeadingRad = Math.toRadians((heading + 360).mod(360.0))
}
