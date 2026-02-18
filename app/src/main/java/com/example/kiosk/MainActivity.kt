package com.example.kiosk

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private lateinit var root: View
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on — prevents lock screen from appearing
        // FLAG_LAYOUT_NO_LIMITS — window extends under system bars, so our content is behind them
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        // Transparent system bars — even if bars briefly flash, they're invisible
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false

        root = View(this)
        setContentView(root)

        startLockTaskIfPermitted()
        enterImmersive()
        setRandomColor()


        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                setRandomColor()
            }
            true
        }

        // Deprecated but actually fires when transient bars appear (unlike OnApplyWindowInsetsListener)
        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0 ||
                visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0) {
                // Bars became visible — re-hide after a tiny delay
                handler.postDelayed({ enterImmersive() }, 50)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-enter lock task and immersive on every resume
        startLockTaskIfPermitted()
        enterImmersive()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Block — do nothing
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Consume all system navigation keys
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SEARCH,
            KeyEvent.KEYCODE_ASSIST,
            KeyEvent.KEYCODE_VOICE_ASSIST -> true
            else -> super.dispatchKeyEvent(event)
        }
    }

    private fun setRandomColor() {
        val color = Color.rgb(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))
        root.setBackgroundColor(color)
    }

    @Suppress("DEPRECATION")
    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // New API
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Old flags as extra layer — IMMERSIVE_STICKY auto-hides bars faster
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }

    private fun startLockTaskIfPermitted() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, KioskDeviceAdminReceiver::class.java)

        if (dpm.isLockTaskPermitted(packageName)) {
            dpm.setStatusBarDisabled(admin, true)
            try {
                startLockTask()
            } catch (_: IllegalArgumentException) { }
        }
    }
}