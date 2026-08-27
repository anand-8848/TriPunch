package com.anand.punchhole

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.CompoundButton
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.anand.punchhole.databinding.ActivityMainBinding

object PrefsKeys {
    const val NAME = "punch_hole_prefs"
    const val MASTER_ON = "master_on"
    const val HOLE_COUNT = 3
}

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private val defaultSize = intArrayOf(22, 16, 16)
    private val defaultPos = intArrayOf(30, 50, 70)
    private val defaultTop = intArrayOf(18, 18, 18)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PrefsKeys.NAME, MODE_PRIVATE)
        seedDefaultsIfNeeded()

        setupMasterToggle()
        setupHoleControls()
        setupStartButton()
    }

    private fun seedDefaultsIfNeeded() {
        if (!prefs.contains(PrefsKeys.MASTER_ON)) {
            val editor = prefs.edit()
            editor.putBoolean(PrefsKeys.MASTER_ON, false)
            for (i in 0 until PrefsKeys.HOLE_COUNT) {
                editor.putInt("size_$i", defaultSize[i])
                editor.putInt("pos_$i", defaultPos[i])
                editor.putInt("top_$i", defaultTop[i])
                editor.putBoolean("on_$i", true)
            }
            editor.apply()
        }
    }

    private fun setupMasterToggle() {
        binding.masterSwitch.isChecked = prefs.getBoolean(PrefsKeys.MASTER_ON, false)
        binding.masterSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PrefsKeys.MASTER_ON, isChecked).apply()
            if (isChecked) {
                tryStartOverlay()
            } else {
                stopService(Intent(this, OverlayService::class.java))
            }
        }
    }

    private fun setupHoleControls() {
        val seekBarSets = listOf(
            Triple(binding.sizeSeek0, binding.posSeek0, binding.topSeek0),
            Triple(binding.sizeSeek1, binding.posSeek1, binding.topSeek1),
            Triple(binding.sizeSeek2, binding.posSeek2, binding.topSeek2)
        )
        val holeToggles = listOf(binding.holeToggle0, binding.holeToggle1, binding.holeToggle2)
        val labels = listOf(
            Triple(binding.sizeLabel0, binding.posLabel0, binding.topLabel0),
            Triple(binding.sizeLabel1, binding.posLabel1, binding.topLabel1),
            Triple(binding.sizeLabel2, binding.posLabel2, binding.topLabel2)
        )

        for (i in 0 until PrefsKeys.HOLE_COUNT) {
            val (sizeSeek, posSeek, topSeek) = seekBarSets[i]
            val (sizeLabel, posLabel, topLabel) = labels[i]

            sizeSeek.max = 40
            posSeek.max = 100
            topSeek.max = 60

            sizeSeek.progress = prefs.getInt("size_$i", defaultSize[i])
            posSeek.progress = prefs.getInt("pos_$i", defaultPos[i])
            topSeek.progress = prefs.getInt("top_$i", defaultTop[i])
            holeToggles[i].isChecked = prefs.getBoolean("on_$i", true)

            sizeLabel.text = "Size: ${sizeSeek.progress}dp"
            posLabel.text = "Position: ${posSeek.progress}%"
            topLabel.text = "Top gap: ${topSeek.progress}dp"

            sizeSeek.setOnSeekBarChangeListener(simpleSeekListener { v ->
                prefs.edit().putInt("size_$i", v).apply()
                sizeLabel.text = "Size: ${v}dp"
                notifyServiceUpdated()
            })
            posSeek.setOnSeekBarChangeListener(simpleSeekListener { v ->
                prefs.edit().putInt("pos_$i", v).apply()
                posLabel.text = "Position: ${v}%"
                notifyServiceUpdated()
            })
            topSeek.setOnSeekBarChangeListener(simpleSeekListener { v ->
                prefs.edit().putInt("top_$i", v).apply()
                topLabel.text = "Top gap: ${v}dp"
                notifyServiceUpdated()
            })
            holeToggles[i].setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                prefs.edit().putBoolean("on_$i", checked).apply()
                notifyServiceUpdated()
            }
        }
    }

    private fun simpleSeekListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) onChange(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    private fun notifyServiceUpdated() {
        if (prefs.getBoolean(PrefsKeys.MASTER_ON, false)) {
            val intent = Intent(this, OverlayService::class.java)
            intent.action = OverlayService.ACTION_REFRESH
            startService(intent)
        }
    }

    private fun setupStartButton() {
        binding.grantPermissionButton.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                tryStartOverlay()
            }
        }
    }

    private fun tryStartOverlay() {
        if (Settings.canDrawOverlays(this)) {
            val intent = Intent(this, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            binding.masterSwitch.isChecked = false
            prefs.edit().putBoolean(PrefsKeys.MASTER_ON, false).apply()
        }
    }

    override fun onResume() {
        super.onResume()
        if (prefs.getBoolean(PrefsKeys.MASTER_ON, false) && Settings.canDrawOverlays(this)) {
            tryStartOverlay()
        }
    }
}
