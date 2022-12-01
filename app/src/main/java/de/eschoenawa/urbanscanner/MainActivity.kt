package de.eschoenawa.urbanscanner

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import de.eschoenawa.urbanscanner.databinding.ActivityMainBinding
import de.eschoenawa.urbanscanner.permissions.handlePermissionResult

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 0)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        handlePermissionResult(
            permission = Manifest.permission.ACCESS_FINE_LOCATION,
            notGrantedAction = ::onPermissionNotGranted,
            doNotAskAgainAction = ::onNeverAskAgain,
            finalAction = ::finish
        )
    }

    private fun onPermissionNotGranted() {
        Toast.makeText(this, "This app needs location permission", Toast.LENGTH_LONG).show()
    }

    private fun onNeverAskAgain() {
        startActivity(Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            data = Uri.fromParts("package", packageName, null)
        })
    }
}