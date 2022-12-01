package de.eschoenawa.urbanscanner.permissions

import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

fun AppCompatActivity.handlePermissionResult(
    permission: String,
    notGrantedAction: () -> Unit,
    doNotAskAgainAction: () -> Unit,
    finalAction: () -> Unit
) {
    if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
        notGrantedAction()
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            doNotAskAgainAction()
        }
        finalAction()
    }
}