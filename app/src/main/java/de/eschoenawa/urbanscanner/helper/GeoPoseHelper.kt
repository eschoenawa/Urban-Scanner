package de.eschoenawa.urbanscanner.helper

import com.google.ar.core.GeospatialPose
import com.google.ar.core.Pose
import de.eschoenawa.urbanscanner.model.GeoPose
import de.eschoenawa.urbanscanner.model.PixelData
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.cross
import dev.romainguy.kotlin.math.dot
import io.github.sceneview.ar.arcore.position
import io.github.sceneview.ar.arcore.yDirection
import io.github.sceneview.ar.arcore.zDirection
import java.lang.Math.toDegrees
import kotlin.math.atan2
import kotlin.math.cos

private const val EARTH_RADIUS = 6_371_000  // meters

fun PixelData.getGeoPose(cameraPosition: Float3, cameraGeoPose: GeoPose): GeoPose {
    return cameraGeoPose.getGeoPoseWithoutHeadingOfLocalPosition(cameraPosition, position)
}

fun GeoPose.getGeoPoseWithoutHeadingOfLocalPosition(geoPoseLocalPosition: Float3, targetLocalPosition: Float3): GeoPose {
    // Points are given in EUS coordinate system (East-Up-South)
    val offset = targetLocalPosition - geoPoseLocalPosition
    //TODO -z here? (1)
    val latOffsetRadians = -offset.z / EARTH_RADIUS
    val longOffsetRadians = offset.x / (EARTH_RADIUS * cos(this.latitudeRad))
    val resultLatDeg = toDegrees(this.latitudeRad + latOffsetRadians)
    val resultLongDeg = toDegrees(this.longitudeRad + longOffsetRadians)
    val pointAltitude = this.altitude + (targetLocalPosition.y - geoPoseLocalPosition.y)
    return GeoPose(
        resultLatDeg,
        resultLongDeg,
        pointAltitude
    )
}

fun GeoPose.getGeoPoseWithHeadingOfLocalPose(geoPoseLocalPose: Pose, targetLocalPose: Pose): GeoPose {
    val withoutHeading = getGeoPoseWithoutHeadingOfLocalPosition(geoPoseLocalPose.position, targetLocalPose.position)
    //TODO -z here? (2)
    val northAxis = -geoPoseLocalPose.zDirection
    val heading = toDegrees(atan2(dot(cross(targetLocalPose.zDirection, northAxis), geoPoseLocalPose.yDirection), dot(northAxis, targetLocalPose.zDirection)).toDouble())
    return GeoPose(
        withoutHeading.latitude,
        withoutHeading.longitude,
        withoutHeading.altitude,
        heading
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

fun GeospatialPose.toGeoPoseWithoutHeading(): GeoPose {
    return GeoPose(
        latitude,
        longitude,
        altitude
    )
}
