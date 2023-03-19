package de.eschoenawa.urbanscanner.helper

import de.eschoenawa.urbanscanner.repository.ScanRepository

object DependencyProvider {

    private val scanRepositoryInstance by lazy { ScanRepository() }
    fun getScanRepository(): ScanRepository {
        return scanRepositoryInstance
    }
}
