package de.eschoenawa.urbanscanner.model

import de.eschoenawa.urbanscanner.helper.TimingHelper

class FramePointCloud(
    private val frameMetaData: FrameMetaData,
    val data: Array<PixelData?>
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

    fun generateMetaDataFileString(): String {
        return "${frameMetaData.getCsvString()}\n"
    }
}
