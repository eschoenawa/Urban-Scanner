package de.eschoenawa.urbanscanner.repository

import android.content.Context
import de.eschoenawa.urbanscanner.helper.TimingHelper
import de.eschoenawa.urbanscanner.model.FrameMetaData
import de.eschoenawa.urbanscanner.model.FramePointCloud
import de.eschoenawa.urbanscanner.model.Scan
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Paths

class ScanRepository {
    private companion object {
        private const val RAW_DATA_FILE = "raw.xyz"
        private const val FRAME_DATA_FILE = "frames.csv"
        private const val UTM_DATA_FILE = "utm.xyz"
        private const val UTM_CAM_DATA_FILE = "frames_utm.csv"
        private const val META_DATA_FILE = "meta.json"
    }

    fun getScan(context: Context, name: String): Scan {
        return loadFromName(context, name)
    }

    fun getAllScans(context: Context): List<Scan> {
        val scansPath = context.getExternalFilesDir(null)?.absolutePath
            ?: throw IllegalStateException("Can't open files!")
        val scansFolder = File(scansPath)
        val scans = emptyList<Scan>().toMutableList()
        scansFolder.listFiles()?.forEach { file ->
            if (file.isScanDirectory()) {
                scans.add(loadFromName(context, file.name))
            }
        }
        return scans.toList()
    }

    fun persistScan(context: Context, scan: Scan) {
        val pathToFile = "${context.getExternalFilesDir(null)?.absolutePath}/${scan.name}/"
        Files.createDirectories(Paths.get(pathToFile))
        val fullFilename = "$pathToFile$META_DATA_FILE"
        FileWriter(fullFilename).use { fw ->
            fw.write(scan.toJson())
        }
    }

    fun deleteScan(context: Context, name: String) {
        val pathToScan = "${context.getExternalFilesDir(null)?.absolutePath}/$name/"
        deleteRecursive(File(pathToScan))
    }

    fun doesScanHaveRawData(context: Context, scan: Scan): Boolean {
        val path = getRawDataFilePath(context, scan)
        val file = File(path)
        return file.exists() && file.isFile
    }

    fun persistRawData(context: Context, scan: Scan, framePointCloud: FramePointCloud) {
        val pointDataFilename = getRawDataFilePath(context, scan)
        val frameDataFilename = getFrameDataFilePath(context, scan)
        val pointsString = TimingHelper.withTimer("preparePersist") {
            framePointCloud.generateFileString()
        }
        val metaDataString = framePointCloud.generateMetaDataFileString()
        TimingHelper.withTimer("persist") {
            FileWriter(pointDataFilename, true).use { fw ->
                fw.write(pointsString)
            }
            FileWriter(frameDataFilename, true).use { fw ->
                fw.write(metaDataString)
            }
        }
    }

    /**
     * This function allows processing the raw points sequentially and putting the new points in a
     * target file.
     */
    suspend fun processRawData(
        context: Context,
        scan: Scan,
        targetFilepath: String,
        process: suspend (String) -> String
    ) {
        val rawFilename = getRawDataFilePath(context, scan)
        FileWriter(targetFilepath).use { fw ->
            File(rawFilename).useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank()) {
                        fw.write(process(line))
                        fw.write("\n")
                    }
                }
            }
        }
    }

    suspend fun processFrameMetadata(
        context: Context,
        scan: Scan,
        targetFilepath: String,
        process: suspend (String) -> String
    ) {
        val frameMetaDataFilename = getFrameDataFilePath(context, scan)
        FileWriter(targetFilepath).use { fw ->
            File(frameMetaDataFilename).useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank()) {
                        fw.write(process(line))
                        fw.write("\n")
                    }
                }
            }
        }
    }

    fun getUtmDataFilePath(context: Context, scan: Scan): String {
        return "${context.getExternalFilesDir(null)?.absolutePath}/${scan.name}/$UTM_DATA_FILE"
    }

    fun getUtmCamDataFilePath(context: Context, scan: Scan): String {
        return "${context.getExternalFilesDir(null)?.absolutePath}/${scan.name}/$UTM_CAM_DATA_FILE"
    }

    fun getFramesMetadata(context: Context, scan: Scan): List<FrameMetaData> {
        val metaDataFileName = getFrameDataFilePath(context, scan)
        val result = mutableListOf<FrameMetaData>()
        File(metaDataFileName).useLines { lines ->
            lines.forEach { line ->
                if (line.isNotBlank()) {
                    val frameMetaData = FrameMetaData.fromCsvString(line)
                    result.add(frameMetaData)
                }
            }
        }
        return result.sortedBy { it.id }
    }

    private fun loadFromName(context: Context, name: String): Scan {
        val fullFilename =
            "${context.getExternalFilesDir(null)?.absolutePath}/$name/$META_DATA_FILE"
        val file = File(fullFilename)
        if (!file.exists() || !file.isFile) {
            throw IllegalArgumentException("Given name has no meta file!")
        }
        val json: String
        FileReader(fullFilename).use { fr ->
            json = fr.readText()
        }
        return Scan.fromJson(json)
    }

    private fun File.isScanDirectory(): Boolean {
        return exists() && isDirectory && listFiles()?.find { it.name == META_DATA_FILE } != null
    }

    private fun deleteRecursive(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { subFile ->
                deleteRecursive(subFile)
            }
        }
        file.delete()
    }

    private fun getRawDataFilePath(context: Context, scan: Scan): String {
        return "${context.getExternalFilesDir(null)?.absolutePath}/${scan.name}/$RAW_DATA_FILE"
    }

    private fun getFrameDataFilePath(context: Context, scan: Scan): String {
        return "${context.getExternalFilesDir(null)?.absolutePath}/${scan.name}/$FRAME_DATA_FILE"
    }
}
