package de.eschoenawa.urbanscanner.postprocessing

import android.content.Context
import de.eschoenawa.urbanscanner.model.FrameMetaData
import de.eschoenawa.urbanscanner.model.Scan
import de.eschoenawa.urbanscanner.repository.ScanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class KmlCameraPathPostProcessor : PostProcessor {

    private companion object {
        private const val LINE_WIDTH = 3
        private val KML_HEADER_A = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
                <Document>
                    <name>""".trimIndent()
        private val KML_HEADER_B = """</name>
            <Style id="style0">
                <LineStyle>
                    <color>ffff0000</color>
                    <width>$LINE_WIDTH</width>
                </LineStyle>
            </Style>
            <Style id="style1">
                <LineStyle>
                    <color>ff00ff00</color>
                    <width>$LINE_WIDTH</width>
                </LineStyle>
            </Style>
            <Style id="style2">
                <LineStyle>
                    <color>ff0000ff</color>
                    <width>$LINE_WIDTH</width>
                </LineStyle>
            </Style>
            <Style id="style3">
                <LineStyle>
                    <color>ffffff00</color>
                    <width>$LINE_WIDTH</width>
                </LineStyle>
            </Style>
            <Style id="style4">
                <LineStyle>
                    <color>ff00ffff</color>
                    <width>$LINE_WIDTH</width>
                </LineStyle>
            </Style>
            <Style id="style5">
                <LineStyle>
                    <color>ffff00ff</color>
                    <width>$LINE_WIDTH</width>
                </LineStyle>
            </Style>
        """.trimIndent()
        private val SCAN_HEADER_A = """
            <Placemark>
                <name>""".trimIndent()
        private val SCAN_HEADER_B = """</name>
                <styleUrl>""".trimIndent()
        private val SCAN_HEADER_C = """</styleUrl>
                <LineString>
                    <coordinates>
        """.trimIndent()
        private val SCAN_FOOTER = """
                    </coordinates>
                </LineString>
            </Placemark>
        """.trimIndent()
        private val KML_FOOTER = """
                </Document>
            </kml>
        """.trimIndent()
    }

    override fun process(
        context: Context,
        scan: Scan,
        scanRepository: ScanRepository
    ) = flow {
        val kmlFileHeader = buildString {
            append(KML_HEADER_A)
            append(scan.name)
            append(KML_HEADER_B)
            append(SCAN_HEADER_A)
            append("Scan 0")
            append(SCAN_HEADER_B)
            append("#style0")
            append(SCAN_HEADER_C)
        }
        val kmlFilePath = scanRepository.getKmlCamDataFilePath(context, scan)
        scanRepository.persistStringToFile(kmlFilePath, kmlFileHeader, false)
        val framesMetaData = scanRepository.getFramesMetadata(context, scan)
        var currentScanId = 0
        var index = 0
        scanRepository.processFrameMetadata(
            context,
            scan,
            kmlFilePath,
            true
        ) { frameMetaDataString ->
            val frameMetaData = FrameMetaData.fromCsvString(frameMetaDataString)
            if (!frameMetaData.isGeoReferenced) throw IllegalArgumentException("Data not georeferenced!")
            frameMetaData.cameraGeoPose!!.let { cameraGeoPose ->
                val newData = buildString {
                    if (currentScanId < frameMetaData.scanNumber) {
                        currentScanId = frameMetaData.scanNumber
                        append(SCAN_FOOTER)
                        append(SCAN_HEADER_A)
                        append("Scan $currentScanId")
                        append(SCAN_HEADER_B)
                        append("#style${currentScanId.mod(6)}")
                        append(SCAN_HEADER_C)
                    }
                    appendLine("${cameraGeoPose.longitude},${cameraGeoPose.latitude},${cameraGeoPose.altitude}")
                }
                emit(
                    Progress(
                        "Scan $currentScanId/${scan.currentScanNumber - 1}; Frame ${index++}/${framesMetaData.size}",
                        ((index.toDouble() / framesMetaData.size.toDouble()) * 100).toInt()
                    )
                )
                // Format is x=Northing, y=Altitude, z=Easting, to show data correctly in CloudCompare. May be modified to comply with other conventions
                // frameId,scanNumber,x,y,z,horizAccuracy,vertAccuracy,headingAccuracy
                return@processFrameMetadata newData
            }
        }
        val kmlFileFooter = buildString {
            append(SCAN_FOOTER)
            append(KML_FOOTER)
        }
        scanRepository.persistStringToFile(kmlFilePath, kmlFileFooter, true)
    }.flowOn(Dispatchers.IO)

    override fun getConfig(): List<PostProcessingConfig> {
        return emptyList()
    }

    override fun configure(configValues: Map<PostProcessingConfig, String>) {
        /*noop*/
    }
}
