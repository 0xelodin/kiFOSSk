package com.shinydiscoballsdev.kifossk

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.widget.SwitchCompat
import android.app.role.RoleManager
import android.os.Build
import androidx.activity.OnBackPressedCallback
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

class SettingsActivity : AppCompatActivity() {

    private lateinit var editTextUrl: EditText
    private lateinit var switchScreenOn: SwitchCompat
    private lateinit var spinnerOrientation: Spinner
    private lateinit var switchAutoRefresh: SwitchCompat
    private lateinit var spinnerRefreshInterval: Spinner
    private lateinit var textRefreshIntervalLabel: TextView
    private lateinit var btnSave: Button
    private lateinit var btnSetLauncher: Button
    private lateinit var textLauncherStatus: TextView
    private lateinit var switchSchedule: SwitchCompat
    private lateinit var layoutSchedule: LinearLayout
    private lateinit var textScheduleHint: TextView
    private lateinit var spinnerDim: Spinner
    private val scheduleRows = mutableListOf<ScheduleRow>()
    private var scheduleHint: CharSequence = ""

    private companion object {
        const val STAY_AWAKE_WARNING = "⚠️ \"Stay awake while charging\" is on in the developer options. " +
            "It keeps the screen on all the time, so the schedule cannot let it go dark."
    }

    /** One schedule entry: on/off switch, time (wake) or start + end (periods). */
    private class ScheduleRow(val key: String, val toggle: SwitchCompat, val start: Button, val end: Button?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lockscreen (API 27+) — lock-screen wake only.
        // NOTE: FLAG_KEEP_SCREEN_ON deliberately NOT added here (Issue #3):
        // it ignored the screen_on preference. Timeout is governed by
        // applyKeepScreenOnFlag(), which respects the switch below.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        setContentView(R.layout.activity_settings)

        // Initialize views
        editTextUrl = findViewById(R.id.editTextUrl)
        editTextUrl.post {
            editTextUrl.clearFocus()
        }
        switchScreenOn = findViewById(R.id.switchScreenOn)
        spinnerOrientation = findViewById(R.id.spinnerOrientation)
        switchAutoRefresh = findViewById(R.id.switchAutoRefresh)
        spinnerRefreshInterval = findViewById(R.id.spinnerRefreshInterval)
        textRefreshIntervalLabel = findViewById(R.id.textRefreshIntervalLabel)
        btnSave = findViewById(R.id.buttonSave)
        btnSetLauncher = findViewById(R.id.buttonSetLauncher)
        textLauncherStatus = findViewById(R.id.textLauncherStatus)

        // Load existing preferences
        editTextUrl.setText(KioskPrefs.getUrl(this))
        switchScreenOn.isChecked = KioskPrefs.getScreenOn(this)

        // Issue #3: apply initial flag state, and follow the switch live
        applyKeepScreenOnFlag()
        switchScreenOn.setOnCheckedChangeListener { _, _ ->
            applyKeepScreenOnFlag()
        }

        // Screen schedule: rows built from ScreenSchedule's entries
        switchSchedule = findViewById(R.id.switchSchedule)
        layoutSchedule = findViewById(R.id.layoutSchedule)
        textScheduleHint = findViewById(R.id.textScheduleHint)
        scheduleHint = textScheduleHint.text
        buildScheduleRows()
        switchSchedule.isChecked = KioskPrefs.isScheduleEnabled(this)
        updateScheduleState()
        switchSchedule.setOnCheckedChangeListener { _, _ ->
            updateScheduleState()
        }

        // Orientation dropdown
        ArrayAdapter.createFromResource(
            this,
            R.array.orientation_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerOrientation.adapter = adapter

            // Sync spinner to saved orientation
            val savedOrientation = KioskPrefs.getOrientation(this)
            val orientationMap = mapOf("landscape" to 0, "portrait" to 1, "auto" to 2)
            spinnerOrientation.setSelection(orientationMap[savedOrientation] ?: 0)
        }

        // Auto-refresh toggle and interval
        switchAutoRefresh.isChecked = KioskPrefs.isAutoRefreshEnabled(this)
        val currentInterval = KioskPrefs.getAutoRefreshInterval(this)
        val intervalValues = intArrayOf(10, 30, 60, 300, 900)
        val intervalIndex = intervalValues.indexOf(currentInterval)
        spinnerRefreshInterval.setSelection(if (intervalIndex >= 0) intervalIndex else 1)
        updateRefreshIntervalSpinnerState()

        switchAutoRefresh.setOnCheckedChangeListener { _, _ ->
            updateRefreshIntervalSpinnerState()
        }

        // Check launcher status on load
        updateLauncherStatus()

        // Set as Launcher button handler
        btnSetLauncher.setOnClickListener {
            setAsLauncher()
        }

        // Save button handler
        btnSave.setOnClickListener {
            saveAndReturn()
        }

        // Handle back press — save settings and return to kiosk
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveAndReturn()
            }
        })
    }

    // Issue #3: FLAG_KEEP_SCREEN_ON driven by the switch, not forced on
    private fun applyKeepScreenOnFlag() {
        if (switchScreenOn.isChecked) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ─── SCREEN SCHEDULE ─────────────────────────────────────────────
    private fun buildScheduleRows() {
        val days = listOf(
            "Weekdays (Mon–Fri)" to ScreenSchedule.Day.WEEKDAY,
            "Weekend (Sat–Sun)" to ScreenSchedule.Day.WEEKEND,
        )
        for ((title, day) in days) {
            addScheduleHeader(title)
            addScheduleRow(ScreenSchedule.wakeKey(day), "Wake at")
            addScheduleRow(ScreenSchedule.keepOnKey(day, 1), "Keep on")
            addScheduleRow(ScreenSchedule.keepOnKey(day, 2), "Keep on")
        }

        addScheduleHeader("Dimming (every day)")
        addScheduleRow(ScreenSchedule.DIM_KEY, "Dim")

        spinnerDim = Spinner(this)
        ArrayAdapter.createFromResource(this, R.array.dim_levels, android.R.layout.simple_spinner_item).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerDim.adapter = adapter
        }
        val dimIndex = resources.getIntArray(R.array.dim_level_values).indexOf(KioskPrefs.getDimPercent(this))
        spinnerDim.setSelection(if (dimIndex >= 0) dimIndex else 2)
        layoutSchedule.addView(spinnerDim)
    }

    private fun addScheduleHeader(text: String) {
        layoutSchedule.addView(TextView(this).apply {
            this.text = text
            textSize = 14f
            secondaryTextColor()?.let { setTextColor(it) }
            setPadding(0, dp(16), 0, dp(4))
        })
    }

    private fun addScheduleRow(key: String, label: String) {
        val (enabled, value) = KioskPrefs.getScheduleEntry(this, key, ScreenSchedule.DEFAULTS.getValue(key))
        val times = value.split("-")

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val toggle = SwitchCompat(this).apply {
            text = label
            isChecked = enabled
        }
        row.addView(toggle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val start = timeButton(times[0])
        row.addView(start)
        var end: Button? = null
        if (times.size == 2) {
            row.addView(TextView(this).apply {
                text = "–"
                setPadding(dp(8), 0, dp(8), 0)
            })
            end = timeButton(times[1]).also { row.addView(it) }
        }

        layoutSchedule.addView(row)
        scheduleRows.add(ScheduleRow(key, toggle, start, end))
    }

    // Button showing HH:MM; tap opens a 24-hour time picker
    private fun timeButton(time: String): Button = Button(this).apply {
        text = time
        setOnClickListener {
            val minutes = ScreenSchedule.parseTime(text.toString()) ?: 0
            TimePickerDialog(this@SettingsActivity, { _, hour, minute ->
                text = ScreenSchedule.formatTime(hour * 60 + minute)
            }, minutes / 60, minutes % 60, true).show()
        }
    }

    // Schedule on: rows visible, the plain "Keep Screen On" switch has no effect.
    // Android's "Stay awake while charging" keeps the screen on whatever the
    // app does, so the schedule could never let it go dark: warn about it.
    private fun updateScheduleState() {
        val visibility = if (switchSchedule.isChecked) View.VISIBLE else View.GONE
        layoutSchedule.visibility = visibility
        textScheduleHint.visibility = visibility
        switchScreenOn.isEnabled = !switchSchedule.isChecked

        val stayAwake = Settings.Global.getInt(contentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0) != 0
        textScheduleHint.text = if (stayAwake) "$scheduleHint\n\n$STAY_AWAKE_WARNING" else scheduleHint
    }

    private fun saveSchedule() {
        KioskPrefs.setScheduleEnabled(this, switchSchedule.isChecked)
        for (row in scheduleRows) {
            val value = if (row.end == null) row.start.text.toString() else "${row.start.text}-${row.end.text}"
            KioskPrefs.setScheduleEntry(this, row.key, row.toggle.isChecked, value)
        }
        KioskPrefs.setDimPercent(this, resources.getIntArray(R.array.dim_level_values)[spinnerDim.selectedItemPosition])
        ScreenWakeReceiver.schedule(this)
    }

    private fun secondaryTextColor(): ColorStateList? {
        val attrs = obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary))
        try {
            return attrs.getColorStateList(0)
        } finally {
            attrs.recycle()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun updateRefreshIntervalSpinnerState() {
        val isEnabled = switchAutoRefresh.isChecked
        spinnerRefreshInterval.isEnabled = isEnabled
        textRefreshIntervalLabel.isEnabled = isEnabled
    }

    override fun onResume() {
        super.onResume()
        applyKeepScreenOnFlag()
        // The developer option may have changed while away
        updateScheduleState()
        // Refresh launcher status when returning from system settings
        updateLauncherStatus()
    }

    /**
     * Open system default home settings page
     */
    private fun setAsLauncher() {
        // Save current settings before leaving (defensive)
        KioskPrefs.setUrl(this, editTextUrl.text.toString())
        KioskPrefs.setScreenOn(this, switchScreenOn.isChecked)
        val intervalValues = intArrayOf(10, 30, 60, 300, 900)
        KioskPrefs.setAutoRefresh(this, switchAutoRefresh.isChecked, intervalValues[spinnerRefreshInterval.selectedItemPosition])
        saveSchedule()
        requestBatteryOptimizationExemption()

        Toast.makeText(this, "Settings saved! Opening launcher selection...", Toast.LENGTH_SHORT).show()

        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
        startActivity(intent)
    }

    /**
     * Check if kiFOSSk is the default home launcher and update UI
     */
    private fun isDefaultLauncher(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            return roleManager?.isRoleHeld(RoleManager.ROLE_HOME) ?: false
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resInfo = packageManager.resolveActivity(homeIntent, 0)
        return resInfo?.activityInfo?.packageName == packageName
    }

    private fun updateLauncherStatus() {
        val isDefault = isDefaultLauncher()

        if (isDefault) {
            textLauncherStatus.text = "✅ Launcher: Active (Boots on startup)"
            textLauncherStatus.setTextColor(getColor(android.R.color.holo_green_light))
            btnSetLauncher.text = "Switch to Different Launcher"
        } else {
            textLauncherStatus.text = "⚠️ Launcher: Not Set (Won't boot to foreground)"
            textLauncherStatus.setTextColor(getColor(android.R.color.holo_red_light))
            btnSetLauncher.text = "Set as Home Launcher"
        }
    }

    /**
     * Request battery optimization exemption for background execution.
     * Shows system dialog: "Allow kiFOSSk to always run in background?"
     */
    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    override fun onPause() {
        super.onPause()
        // Save preferences when activity loses focus (defensive backup)
        KioskPrefs.setUrl(this, editTextUrl.text.toString())
        KioskPrefs.setScreenOn(this, switchScreenOn.isChecked)
        val orientationMap = mapOf(0 to "landscape", 1 to "portrait", 2 to "auto")
        KioskPrefs.setOrientation(this, orientationMap[spinnerOrientation.selectedItemPosition]!!)
        val intervalValues = intArrayOf(10, 30, 60, 300, 900)
        KioskPrefs.setAutoRefresh(this, switchAutoRefresh.isChecked, intervalValues[spinnerRefreshInterval.selectedItemPosition])
        saveSchedule()
    }

    private fun saveAndReturn() {
        KioskPrefs.setUrl(this, editTextUrl.text.toString())
        KioskPrefs.setScreenOn(this, switchScreenOn.isChecked)

        val orientationMap = mapOf(0 to "landscape", 1 to "portrait", 2 to "auto")
        KioskPrefs.setOrientation(this, orientationMap[spinnerOrientation.selectedItemPosition]!!)

        val intervalValues = intArrayOf(10, 30, 60, 300, 900)
        KioskPrefs.setAutoRefresh(this, switchAutoRefresh.isChecked, intervalValues[spinnerRefreshInterval.selectedItemPosition])

        saveSchedule()

        // FIX Bug #4: Clear first_run only after URL is validated and saved
        KioskPrefs.setFirstRun(this, false)

        Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()

        // Return to MainActivity
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}