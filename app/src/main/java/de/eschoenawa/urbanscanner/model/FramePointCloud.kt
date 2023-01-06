package de.eschoenawa.urbanscanner.model

import de.eschoenawa.urbanscanner.helper.TimingHelper

class FramePointCloud(
    val data: Array<PixelData?>
) {
    var pointCount = 0

    fun generateFileString(): String {
        return buildString {
            data.forEach { nullableData ->
                nullableData?.let { pixelData ->
                    TimingHelper.startTimer("build")
                    val line = if (pixelData.isGeoReferenced) {
                        "${pixelData.x},${pixelData.y},${pixelData.z},${pixelData.normalizedConfidence},${pixelData.r.toInt()},${pixelData.g.toInt()},${pixelData.b.toInt()},${pixelData.latitude},${pixelData.longitude},${pixelData.altitude}"
                    } else {
                        "${pixelData.x},${pixelData.y},${pixelData.z},${pixelData.normalizedConfidence},${pixelData.r.toInt()},${pixelData.g.toInt()},${pixelData.b.toInt()}"
                    }
                    TimingHelper.endTimer("build")
                    TimingHelper.startTimer("append")
                    appendLine(line)
                    TimingHelper.endTimer("append")
                }
            }
        }
    }
}
