package de.eschoenawa.urbanscanner.scanlist

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.eschoenawa.urbanscanner.helper.DependencyProvider
import de.eschoenawa.urbanscanner.model.Scan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScanListViewModel : ViewModel() {
    private val scanRepository = DependencyProvider.getScanRepository()

    private val _scans = MutableLiveData<List<Scan>>()
    val scans: LiveData<List<Scan>>
        get() = _scans

    fun loadScans(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _scans.postValue(scanRepository.getAllScans(context))
        }
    }

    fun deleteScan(context: Context, scan: Scan) {
        scanRepository.deleteScan(context, scan.name)
    }
}
