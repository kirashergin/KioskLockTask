package com.example.kiosk

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
class KioskApp : Application() {
    override fun onCreate() {
        super.onCreate()
        tryConfigureDeviceOwnerPolicies()
    }

    private fun tryConfigureDeviceOwnerPolicies() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, KioskDeviceAdminReceiver::class.java)

        if (!dpm.isDeviceOwnerApp(packageName)) return

        // Allowlist our package for Lock Task
        dpm.setLockTaskPackages(admin, arrayOf(packageName))

        // Disable all Lock Task features for a tight kiosk (0 = none enabled)
        dpm.setLockTaskFeatures(admin, 0)

        // Disable status bar (blocks notifications & quick settings)
        dpm.setStatusBarDisabled(admin, true)

        // Disable keyguard (optional)
        dpm.setKeyguardDisabled(admin, true)

        // Make our activity the default HOME/launcher
        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val activity = ComponentName(this, MainActivity::class.java)
        dpm.addPersistentPreferredActivity(admin, filter, activity)

        // Suppress "swipe to exit immersive" education bubble
        try { dpm.setSecureSetting(admin, "immersive_mode_confirmations", "confirmed") } catch (_: Exception) {}
    }
}