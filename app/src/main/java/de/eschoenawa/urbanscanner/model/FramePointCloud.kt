package de.eschoenawa.urbanscanner.model

import de.eschoenawa.urbanscanner.helper.TimingHelper

class FramePointCloud(
    val data: Array<RawPixelData?>
) {
    var pointCount = 0

    fun generateFileString(): String {
        return buildString {
            data.forEach { nullableData ->
                nullableData?.let { pixelData ->
                    TimingHelper.startTimer("build")
                    val line = pixelData.stringRepresentation
                    TimingHelper.endTimer("build")
                    TimingHelper.startTimer("append")
                    appendLine(line)
                    TimingHelper.endTimer("append")
                }
            }
        }
    }
}
