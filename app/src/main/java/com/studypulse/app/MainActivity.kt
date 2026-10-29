package com.studypulse.app

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val vm: AppVM by viewModels()
    private var lastPin = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // If the app was killed during a session, Do Not Disturb is restored only when no session is running.
        if (vm.e.activeSession() == null) FocusGuard.restoreDnd(applicationContext, vm.e.store)
        Reminders.scheduleAll(applicationContext)
        val appCtx = applicationContext
        Thread { try { com.google.android.gms.ads.MobileAds.initialize(appCtx) } catch (_: Exception) { } }.start()
        Night.load(applicationContext)
        setContent { App(vm) }
    }

    override fun onResume() {
        super.onResume()
        Reminders.cancelReturn(applicationContext)
        repin()
        vm.bump()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) repin()
    }

    /** While a session is running the app pins itself again if you escaped the pin. */
    private fun repin() {
        if (vm.e.activeSession() == null) return
        val now = System.currentTimeMillis()
        if (now - lastPin < 2500) return
        lastPin = now
        FocusGuard.ensurePinned(this, vm.e.store)
    }

    override fun onStop() {
        super.onStop()
        if (vm.e.activeSession() != null) Reminders.postReturn(applicationContext)
    }

    fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
