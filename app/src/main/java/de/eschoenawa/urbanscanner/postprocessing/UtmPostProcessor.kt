package de.eschoenawa.urbanscanner.postprocessing

import android.content.Context
import android.util.Log
import de.eschoenawa.urbanscanner.R
import de.eschoenawa.urbanscanner.helper.UtmCoordinateConverter
import de.eschoenawa.urbanscanner.model.PrecisePixelData
import de.eschoenawa.urbanscanner.model.RawPixelData
import de.eschoenawa.urbanscanner.model.Scan
import de.eschoenawa.urbanscanner.repository.ScanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class UtmPostProcessor : PostProcessor {

    override fun getName(): Int {
        return R.string.utm_post_processor
    }

    override fun process(
        context: Context,
        scan: Scan,
        scanRepository: ScanRepository
    ): Flow<Progress> = flow {
        var pointsProcessed = 0L
        var utmCoordinateConverter: UtmCoordinateConverter? =
            if (scan.epsgCode.isBlank()) null else null//TODO UtmCoordinateConverter(scan.epsgCode)
        scanRepository.processRawData(
            context,
            scan,
            scanRepository.getUtmDataFilePath(context, scan)
        ) { pixelData ->
            if (utmCoordinateConverter == null) {
                utmCoordinateConverter = UtmCoordinateConverter.fromLatLong(
                    pixelData.latitude!!.toFloat(),
                    pixelData.longitude!!.toFloat()
                )
                scan.epsgCode = utmCoordinateConverter!!.targetEpsgCode
                scanRepository.persistScan(context, scan)
            }
            val newCoordinates = utmCoordinateConverter!!.getUtmCoordinates(
                pixelData.latitude!!,
                pixelData.longitude!!
            )
            Log.d("O_O", "New coordinates (${newCoordinates[0]}|${newCoordinates[1]})")
            pointsProcessed++
            //TODO string template?
            emit(
                Progress(
                    "$pointsProcessed/${scan.pointCount}",
                    ((pointsProcessed.toDouble() / scan.pointCount.toDouble()) * 100).toInt()
                )
            )
            return@processRawData PrecisePixelData(
                newCoordinates[0],
                pixelData.altitude!!,
                newCoordinates[1],
                pixelData.confidence,
                pixelData.r,
                pixelData.g,
                pixelData.b
            )
        }
    }.flowOn(Dispatchers.IO)

    override fun getConfig(): List<PostProcessingConfig> {
        //TODO add config?
        return emptyList()
    }

    override fun configure(configValues: Map<PostProcessingConfig, String>) {
        /*noop*/
    }
}
