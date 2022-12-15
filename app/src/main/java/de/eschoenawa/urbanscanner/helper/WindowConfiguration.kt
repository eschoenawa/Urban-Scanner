package de.eschoenawa.urbanscanner.helper

import android.app.Activity
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

//TODO remove these if not needed

fun Activity.configureWindowForArFullscreen(rootView: View) {
    with(window) {
        addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val windowInsetsController =
            WindowCompat.getInsetsController(this, rootView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(this, false)
    }

}

fun Activity.unconfigureWindowFromArFullscreen(rootView: View) {
    with(window) {
        clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val windowInsetsController =
            WindowCompat.getInsetsController(this, rootView)
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }
}
