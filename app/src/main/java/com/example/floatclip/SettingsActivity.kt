package com.example.floatclip

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private val dbHelper by lazy { ClipboardDatabaseHelper(this) }
    private val gson = Gson()

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val reader = InputStreamReader(contentResolver.openInputStream(uri))
                val itemType = object : TypeToken<List<ClipboardItem>>() {}.type
                val items: List<ClipboardItem> = gson.fromJson(reader, itemType)
                var imported = 0
                for (item in items) {
                    if (dbHelper.insertClipboardItem(item.text, item.timestamp, item.isPinned, item.hash)) {
                        imported++
                    }
                }
                Snackbar.make(findViewById(android.R.id.content), "Imported $imported items", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(findViewById(android.R.id.content), "Import failed: ${e.localizedMessage}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Material fade/axis transition
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.requestFeature(android.view.Window.FEATURE_ACTIVITY_TRANSITIONS)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    // Use MaterialFadeThrough for Android 10+
                    val fade = com.google.android.material.transition.platform.MaterialFadeThrough()
                    window.enterTransition = fade
                    window.exitTransition = fade
                } else {
                    val fade = android.transition.Fade()
                    window.enterTransition = fade
                    window.exitTransition = fade
                }
            } catch (_: Exception) {}
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = getSharedPreferences("settings", MODE_PRIVATE)

        // Widget size
        val group = findViewById<RadioGroup>(R.id.widget_size_group)
        val sizeSmall = findViewById<RadioButton>(R.id.size_small)
        val sizeMedium = findViewById<RadioButton>(R.id.size_medium)
        val sizeLarge = findViewById<RadioButton>(R.id.size_large)
        val savedSize = prefs.getString("widget_size", "medium")
        when(savedSize) {
            "small" -> sizeSmall.isChecked = true
            "large" -> sizeLarge.isChecked = true
            else -> sizeMedium.isChecked = true
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            val value = when(checkedId) {
                R.id.size_small -> "small"
                R.id.size_large -> "large"
                else -> "medium"
            }
            prefs.edit().putString("widget_size", value).apply()
        }

        // Position memory
        val posSwitch = findViewById<Switch>(R.id.switch_position_memory)
        posSwitch.isChecked = prefs.getBoolean("position_memory", true)
        posSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("position_memory", isChecked).apply()
        }

        // Auto-delete
        val autoDeleteSeek = findViewById<SeekBar>(R.id.seek_autodelete)
        val autoDeleteLabel = findViewById<TextView>(R.id.text_autodelete)
        val autoDeleteValue = prefs.getInt("autodelete", 0)
        autoDeleteSeek.progress = autoDeleteValue
        autoDeleteLabel.text = if(autoDeleteValue == 0) "Auto-delete: Off" else "Auto-delete: after $autoDeleteValue min"
        autoDeleteSeek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                autoDeleteLabel.text = if(progress == 0) "Auto-delete: Off" else "Auto-delete: after $progress min"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("autodelete", seekBar?.progress ?: 0).apply()
            }
        })

        // Dark mode
        val darkSwitch = findViewById<Switch>(R.id.switch_darkmode)
        val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        darkSwitch.isChecked = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        darkSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        // Export button
        findViewById<android.view.View>(R.id.btn_export).setOnClickListener {
            try {
                val items = dbHelper.getAllItems()
                val json = gson.toJson(items)
                val file = File(cacheDir, "clipboard_export_${System.currentTimeMillis()}.json")
                OutputStreamWriter(file.outputStream()).use { it.write(json) }
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
                intent.type = "application/json"
                intent.putExtra(android.content.Intent.EXTRA_STREAM, uri)
                intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(android.content.Intent.createChooser(intent, "Export Clipboard Data"))
            } catch (e: Exception) {
                Snackbar.make(findViewById(android.R.id.content), "Export failed: ${e.localizedMessage}", Snackbar.LENGTH_LONG).show()
            }
        }

        // Import button
        findViewById<android.view.View>(R.id.btn_import).setOnClickListener {
            try {
                importLauncher.launch("application/json")
            } catch (e: Exception) {
                Snackbar.make(findViewById(android.R.id.content), "Import failed: ${e.localizedMessage}", Snackbar.LENGTH_LONG).show()
            }
        }
    }
}
