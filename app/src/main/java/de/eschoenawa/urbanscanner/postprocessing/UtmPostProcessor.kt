package de.eschoenawa.urbanscanner.postprocessing

import android.content.Context
import de.eschoenawa.urbanscanner.R
import de.eschoenawa.urbanscanner.helper.UtmCoordinateConverter
import de.eschoenawa.urbanscanner.helper.getGeoPose
import de.eschoenawa.urbanscanner.model.PixelData
import de.eschoenawa.urbanscanner.model.Scan
import de.eschoenawa.urbanscanner.repository.ScanRepository
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
        scanRepository.processRawData(
            context,
            scan,
            scanRepository.getUtmDataFilePath(context, scan)
        ) { pixelDataString ->
            val pixelData = PixelData.fromString(pixelDataString)
            val frameMetaData = framesMetaData[pixelData.frame]
            if (!frameMetaData.isGeoReferenced) throw IllegalArgumentException("Data not georeferenced!")
            // TODO alternative approach: convert cam pose to UTM and use UTM for offsets
            val pixelGeoPose =
                pixelData.getGeoPose(frameMetaData.cameraPosition, frameMetaData.cameraGeoPose!!)
            if (utmCoordinateConverter == null) {
                utmCoordinateConverter = UtmCoordinateConverter.fromLatLong(
                    pixelGeoPose.latitude.toFloat(),
                    pixelGeoPose.longitude.toFloat()
                )
                scan.epsgCode = utmCoordinateConverter!!.targetEpsgCode
                scanRepository.persistScan(context, scan)
            }
            val newCoordinates = utmCoordinateConverter!!.getUtmCoordinates(
                pixelGeoPose.latitude,
                pixelGeoPose.longitude
            )
            pointsProcessed++
            //TODO string template?
            emit(
                Progress(
                    "$pointsProcessed/${scan.pointCount}",
                    ((pointsProcessed.toDouble() / scan.pointCount.toDouble()) * 100).toInt()
                )
            )
            // Format is x=Northing, y=Altitude, z=Easting, to show data correctly in CloudCompare. May be modified to comply with other conventions
            return@processRawData "${newCoordinates[1]},${pixelGeoPose.altitude},${newCoordinates[0]},${pixelData.normalizedConfidence},${pixelData.r.toInt()},${pixelData.g.toInt()},${pixelData.b.toInt()}"
        }
    }.flowOn(Dispatchers.IO)

    override fun getConfig(): List<PostProcessingConfig> {
        return listOf(
            UseCustomEpsgConfig,
            CustomEpsgConfig
        )
    }

    override fun configure(configValues: Map<PostProcessingConfig, String>) {
        shouldUseCustomEpsg = configValues[UseCustomEpsgConfig]!!.toBoolean()
        customEpsgCode = configValues[CustomEpsgConfig]!!
    }

    object UseCustomEpsgConfig : PostProcessingConfig {
        override val required = false
        override val name = R.string.utm_post_processor_config_use_custom_epsg

        override fun validateValue(value: String): Boolean {
            if (value.isBlank()) return true
            if (value.lowercase() == "true" || value.lowercase() == "false") return true
            return false
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
}
