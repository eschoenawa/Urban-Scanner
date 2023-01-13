package de.eschoenawa.urbanscanner.helper

import com.google.ar.core.GeospatialPose
import com.google.ar.core.Pose
import de.eschoenawa.urbanscanner.model.GeoPose
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.cross
import dev.romainguy.kotlin.math.dot
import dev.romainguy.kotlin.math.pow
import io.github.sceneview.ar.arcore.position
import io.github.sceneview.ar.arcore.zDirection
import java.lang.Math.toDegrees
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS = 6_371_000  // meters

fun FloatArray.fromWorldToGeoPose(cameraPose: Pose, cameraGeoPose: GeoPose): GeoPose {
    //TODO verify this works correctly
    if (this.size != 4) throw IllegalArgumentException("Function only works with points in format (x,y,z,1)")
    val pointPosition = Float3(this[0], this[1], this[2])
    val cameraForwardDirection = cameraPose.zDirection
    val cameraPointDirection = pointPosition - cameraPose.position
    val axis = Float3(0f, 1f, 0f)
    val cameraForwardDirectionProjected = cameraForwardDirection - ( axis * dot(cameraForwardDirection, axis))
    val cameraPointDirectionProjected = cameraPointDirection - (axis * dot(cameraPointDirection, axis))
    val signedLeftHandedAngle = atan2(
        dot(cross(cameraPointDirectionProjected, cameraForwardDirectionProjected), axis),
        dot(cameraForwardDirectionProjected, cameraPointDirectionProjected)
    )
    val pointBearing = ((cameraGeoPose.compassHeadingRad + signedLeftHandedAngle) + (2 * PI)).mod(2 * PI)
    val distancePointCamera = cameraPointDirection.magnitude()
    val distanceRadiusFraction = distancePointCamera / EARTH_RADIUS
    val pointLat = asin(sin(cameraGeoPose.latitudeRad) * cos( distanceRadiusFraction) + cos(cameraGeoPose.latitudeRad) * sin(distanceRadiusFraction) * cos(pointBearing))
    val pointLong = cameraGeoPose.longitudeRad + atan2(sin(pointBearing) * sin(distanceRadiusFraction) * cos(cameraGeoPose.latitudeRad), cos(distanceRadiusFraction) - sin(cameraGeoPose.latitude) * sin(pointLat))
    val pointAltitude = cameraGeoPose.altitude + (pointPosition.y - cameraPose.position.y)
    return GeoPose(
        toDegrees(pointLat),
        toDegrees(pointLong),
        pointAltitude
    )
}

fun GeospatialPose.toGeoPose(): GeoPose {
    return GeoPose(
        latitude,
        longitude,
        altitude,
        heading
    )
}

private fun Float3.magnitude(): Float {
    return sqrt(pow(x, 2f) + pow(y, 2f) + pow(z, 2f))
}
