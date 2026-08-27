package com.anand.punchhole

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

class OverlayService : Service() {

    companion object {
        const val ACTION_REFRESH = "com.anand.punchhole.REFRESH"
        const val CHANNEL_ID = "punch_hole_channel"
        const val NOTIF_ID = 1
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: FrameLayout? = null
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(PrefsKeys.NAME, MODE_PRIVATE)
        startForegroundWithNotification()
        addOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH) {
            redrawHoles()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Punch-hole overlay",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tri-Hole overlay active")
            .setContentText("Tap to open settings")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIF_ID, notification)
    }

    private fun addOverlay() {
        val container = FrameLayout(this)
        overlayView = container

        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        windowManager.addView(container, params)
        redrawHoles()
    }

    private fun redrawHoles() {
        val container = overlayView ?: return
        container.removeAllViews()

        if (!prefs.getBoolean(PrefsKeys.MASTER_ON, false)) return

        val metrics: DisplayMetrics = resources.displayMetrics
        val screenWidthPx = metrics.widthPixels
        val density = metrics.density

        for (i in 0 until PrefsKeys.HOLE_COUNT) {
            val isOn = prefs.getBoolean("on_$i", true)
            if (!isOn) continue

            val sizeDp = prefs.getInt("size_$i", 18)
            val posPercent = prefs.getInt("pos_$i", 50)
            val topDp = prefs.getInt("top_$i", 18)

            val sizePx = (sizeDp * density).toInt()
            val topPx = (topDp * density).toInt()
            val leftPx = ((posPercent / 100f) * screenWidthPx - sizePx / 2f).toInt()

            val hole = View(this)
            hole.setBackgroundResource(R.drawable.hole_shape)

            val lp = FrameLayout.LayoutParams(sizePx, sizePx)
            lp.leftMargin = leftPx
            lp.topMargin = topPx
            hole.layoutParams = lp

            container.addView(hole)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
            }
        }
    }
}
