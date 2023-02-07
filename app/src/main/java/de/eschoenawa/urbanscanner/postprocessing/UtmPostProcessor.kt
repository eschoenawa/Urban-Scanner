package de.eschoenawa.urbanscanner.postprocessing

import android.content.Context
import de.eschoenawa.urbanscanner.R
import de.eschoenawa.urbanscanner.helper.UtmCoordinateConverter
import de.eschoenawa.urbanscanner.helper.getGeoPose
import de.eschoenawa.urbanscanner.model.FrameMetaData
import de.eschoenawa.urbanscanner.model.PixelData
import de.eschoenawa.urbanscanner.model.Scan
import de.eschoenawa.urbanscanner.repository.ScanRepository
import io.github.sceneview.math.toVector3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.cts.CRSFactory
import org.cts.crs.CRSException
import org.cts.registry.EPSGRegistry

class UtmPostProcessor : PostProcessor {

    private var shouldUseCustomEpsg = false
    private var customEpsgCode = ""
    private var addMetadata = false


    override fun getName(): Int {
        return R.string.utm_post_processor
    }

    override fun process(
        context: Context,
        scan: Scan,
        scanRepository: ScanRepository
    ): Flow<Progress> = flow {
        var pointsProcessed = 0L
        if (shouldUseCustomEpsg) {
            scan.epsgCode = customEpsgCode
            scanRepository.persistScan(context, scan)
        }
        var utmCoordinateConverter: UtmCoordinateConverter? =
            if (scan.epsgCode.isBlank()) null else UtmCoordinateConverter(scan.epsgCode)
        val framesMetaData = scanRepository.getFramesMetadata(context, scan)
        var index = 0
        scanRepository.processFrameMetadata(
            context,
            scan,
            scanRepository.getUtmCamDataFilePath(context, scan)
        ) {frameMetaDataString ->
            val frameMetaData = FrameMetaData.fromCsvString(frameMetaDataString)
            if (!frameMetaData.isGeoReferenced) throw IllegalArgumentException("Data not georeferenced!")
            frameMetaData.cameraGeoPose!!.let { cameraGeoPose ->
                if (utmCoordinateConverter == null) {
                    utmCoordinateConverter = UtmCoordinateConverter.fromLatLong(
                        cameraGeoPose.latitude.toFloat(),
                        cameraGeoPose.longitude.toFloat()
                    )
                    scan.epsgCode = utmCoordinateConverter!!.targetEpsgCode
                    scanRepository.persistScan(context, scan)
                }
                val camUtmCoordinates = utmCoordinateConverter!!.getUtmCoordinates(
                    cameraGeoPose.latitude,
                    cameraGeoPose.longitude
                )

                //TODO string template?
                emit(
                    Progress(
                        "CameraPoints: ${index++}/${framesMetaData.size}",
                        0
                    )
                )
                // Format is x=Northing, y=Altitude, z=Easting, to show data correctly in CloudCompare. May be modified to comply with other conventions
                // frameId,scanNumber,x,y,z,horizAccuracy,vertAccuracy,headingAccuracy
                return@processFrameMetadata "${frameMetaData.id},${frameMetaData.scanNumber},${camUtmCoordinates[1]},${cameraGeoPose.altitude},${camUtmCoordinates[0]},${frameMetaData.horizontalAccuracy},${frameMetaData.verticalAccuracy},${frameMetaData.headingAccuracy}"
            }
        }
        scanRepository.processRawData(
            context,
            scan,
            scanRepository.getUtmDataFilePath(context, scan)
        ) { pixelDataString ->
            val pixelData = PixelData.fromString(pixelDataString)
            val frameMetaData = framesMetaData[pixelData.frame]
            // TODO alternative approach: convert cam pose to UTM and use UTM for offsets
            val pixelGeoPose =
                pixelData.getGeoPose(frameMetaData.cameraPosition, frameMetaData.cameraGeoPose!!)
            val newCoordinates = utmCoordinateConverter!!.getUtmCoordinates(
                pixelGeoPose.latitude,
                pixelGeoPose.longitude
            )
            val distanceCameraToPoint =
                (pixelData.position - frameMetaData.cameraPosition).toVector3().length()
            pointsProcessed++
            //TODO string template?
            emit(
                Progress(
                    "$pointsProcessed/${scan.pointCount}",
                    ((pointsProcessed.toDouble() / scan.pointCount.toDouble()) * 100).toInt()
                )
            )
            // horizAccuracy,vertAccuracy,headingAccuracy,scanNumber
            val metadata = if (addMetadata) {
                ",${frameMetaData.horizontalAccuracy},${frameMetaData.verticalAccuracy},${frameMetaData.headingAccuracy},${frameMetaData.scanNumber}"
            } else {
                ""
            }
            // Format is x=Northing, y=Altitude, z=Easting, to show data correctly in CloudCompare. May be modified to comply with other conventions
            // x,y,z,r,g,b,confidence,distanceToCamera[,framemetadata]
            return@processRawData "${newCoordinates[1]},${pixelGeoPose.altitude},${newCoordinates[0]},${pixelData.r.toInt()},${pixelData.g.toInt()},${pixelData.b.toInt()},${pixelData.normalizedConfidence},$distanceCameraToPoint$metadata"
        }
    }.flowOn(Dispatchers.IO)

    override fun getConfig(): List<PostProcessingConfig> {
        return listOf(
            UseCustomEpsgConfig,
            CustomEpsgConfig,
            AddFrameMetadataConfig
        )
    }

    override fun configure(configValues: Map<PostProcessingConfig, String>) {
        shouldUseCustomEpsg = configValues[UseCustomEpsgConfig]!!.lowercase().toBoolean()
        customEpsgCode = configValues[CustomEpsgConfig]!!
        addMetadata = configValues[AddFrameMetadataConfig]!!.lowercase().toBoolean()
    }

    object UseCustomEpsgConfig : PostProcessingConfig {
        override val required = false
        override val name = R.string.utm_post_processor_config_use_custom_epsg

        override fun validateValue(value: String): Boolean {
            if (value.isBlank()) return true
            return try {
                value.lowercase().toBooleanStrict()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    object CustomEpsgConfig : PostProcessingConfig {
        private val crsFactory = CRSFactory().apply {
            registryManager.addRegistry(EPSGRegistry())
        }
        override val required = false
        override val name = R.string.utm_post_processor_config_custom_epsg

        override fun validateValue(value: String): Boolean {
            if (value.isBlank()) return true
            return try {
                val crs = crsFactory.getCRS(value)
                crs != null
            } catch (e: CRSException) {
                false
            }
        }
    }

    object AddFrameMetadataConfig : PostProcessingConfig {
        override val required = false
        override val name = R.string.utm_post_processor_config_add_frame_metadata

        override fun validateValue(value: String): Boolean {
            if (value.isBlank()) return true
            return try {
                value.lowercase().toBooleanStrict()
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
