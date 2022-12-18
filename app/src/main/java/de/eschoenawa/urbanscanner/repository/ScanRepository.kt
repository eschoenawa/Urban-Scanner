package de.eschoenawa.urbanscanner.repository

import android.content.Context
import de.eschoenawa.urbanscanner.model.Scan
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Paths

class ScanRepository {
    private companion object {
        private const val RAW_DATA_FILE = "raw.xyz"
        private const val META_DATA_FILE = "meta.json"
    }

    fun getScan(context: Context, name: String): Scan {
        return loadFromName(context, name)
    }

    fun getAllScans(context: Context): List<Scan> {
        val scansPath = context.getExternalFilesDir(null)?.absolutePath ?: throw IllegalStateException("Can't open files!")
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

    fun getRawDataFilePath(context: Context, scan: Scan): String {
        return "${context.getExternalFilesDir(null)?.absolutePath}/${scan.name}/$RAW_DATA_FILE"
    }

    private fun loadFromName(context: Context, name: String): Scan {
        val fullFilename = "${context.getExternalFilesDir(null)?.absolutePath}/$name/$META_DATA_FILE"
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
}