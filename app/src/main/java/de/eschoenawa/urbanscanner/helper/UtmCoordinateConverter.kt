package de.eschoenawa.urbanscanner.helper

import android.util.Log
import org.cts.CRSFactory
import org.cts.crs.CoordinateReferenceSystem
import org.cts.crs.GeodeticCRS
import org.cts.op.CoordinateOperation
import org.cts.op.CoordinateOperationFactory
import org.cts.op.transformation.FrenchGeocentricNTF2RGF
import org.cts.op.transformation.GridBasedTransformation
import org.cts.op.transformation.NTv2GridShiftTransformation
import org.cts.registry.EPSGRegistry
import org.cts.util.UTMUtils

class UtmCoordinateConverter(val targetEpsgCode: String) {

    private val crsFactory: CRSFactory = CRSFactory()
    private val sourceCrs: CoordinateReferenceSystem
    private val targetCrs: CoordinateReferenceSystem
    private var coordinateOperationCandidates: Set<CoordinateOperation>
    private val coordinateOperation: CoordinateOperation

    companion object {
        private const val TAG = "UTMConv"
        private const val WGS84_EPSG_CODE = "EPSG:4326"

        fun fromLatLong(latitude: Float, longitude: Float): UtmCoordinateConverter {
            val autoEpsgCode = "EPSG:${UTMUtils.getEPSGCode(latitude, longitude)}"
            return UtmCoordinateConverter(autoEpsgCode)
        }
    }

    init {
        val registryManager = crsFactory.registryManager
        registryManager.addRegistry(EPSGRegistry())

        sourceCrs = crsFactory.getCRS(WGS84_EPSG_CODE)
        targetCrs = crsFactory.getCRS(targetEpsgCode)
        coordinateOperationCandidates = CoordinateOperationFactory.createCoordinateOperations(
            sourceCrs as GeodeticCRS, targetCrs as GeodeticCRS
        )
        val totalOperations = coordinateOperationCandidates.size
        coordinateOperationCandidates = CoordinateOperationFactory.excludeFilter(
            coordinateOperationCandidates,
            FrenchGeocentricNTF2RGF::class.java
        )
        coordinateOperationCandidates = CoordinateOperationFactory.excludeFilter(
            coordinateOperationCandidates,
            NTv2GridShiftTransformation::class.java
        )
        coordinateOperationCandidates = CoordinateOperationFactory.excludeFilter(
            coordinateOperationCandidates,
            GridBasedTransformation::class.java
        )
        coordinateOperation =
            CoordinateOperationFactory.getMostPrecise(coordinateOperationCandidates)
    }

    fun getUtmCoordinates(latitude: Double, longitude: Double): DoubleArray {
        return coordinateOperation.transform(doubleArrayOf(latitude, longitude))
    }
}
