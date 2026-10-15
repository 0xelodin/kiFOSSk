package com.shinydiscoballsdev.kifossk

import android.content.Context
import android.content.SharedPreferences

object KioskPrefs {
    private const val PREFS_NAME = "kiosk_prefs"
    private const val DEFAULT_URL = "http://192.168.50.152:3001"

    private var prefsInstance: SharedPreferences? = null

    fun getInstance(context: Context): SharedPreferences {
        if (prefsInstance == null) {
            prefsInstance = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        return prefsInstance!!
    }

    // BUG #1: Returns empty string if user saves "", doesn't validate
    fun getUrl(context: Context): String {
        val prefs = getInstance(context)
        val url = prefs.getString("web_url", null)  // Changed: null instead of DEFAULT_URL
        // FIX: Use UrlValidator to sanitize
        return UrlValidator.sanitizeOrDefault(url, DEFAULT_URL)
    }

    fun setUrl(context: Context, url: String) {
        getInstance(context).edit().putString("web_url", url).commit()
    }

    fun isFirstRun(context: Context): Boolean {
        return getInstance(context).getBoolean("first_run", true)
    }

    fun setFirstRun(context: Context, value: Boolean) {
        getInstance(context).edit().putBoolean("first_run", value).commit()
    }

    fun getOrientation(context: Context): String {
        return getInstance(context).getString("orientation", "landscape") ?: "landscape"
    }

    fun setOrientation(context: Context, orientation: String) {
        getInstance(context).edit().putString("orientation", orientation).commit()
    }

    fun getScreenOn(context: Context): Boolean {
        return getInstance(context).getBoolean("screen_on", true)
    }

    fun setScreenOn(context: Context, value: Boolean) {
        getInstance(context).edit().putBoolean("screen_on", value).apply()
    }

    fun isAutoRefreshEnabled(context: Context): Boolean {
        return getInstance(context).getBoolean("auto_refresh_enabled", false)
    }

    fun getAutoRefreshInterval(context: Context): Int {
        return getInstance(context).getInt("auto_refresh_interval", 30)
    }

    fun setAutoRefresh(context: Context, enabled: Boolean, intervalSeconds: Int) {
        getInstance(context).edit()
            .putBoolean("auto_refresh_enabled", enabled)
            .putInt("auto_refresh_interval", intervalSeconds)
            .apply()
    }

    // ─── SCREEN SCHEDULE (see ScreenSchedule) ───────────────────────────
    fun isScheduleEnabled(context: Context): Boolean {
        return getInstance(context).getBoolean("schedule_enabled", false)
    }

    fun setScheduleEnabled(context: Context, value: Boolean) {
        getInstance(context).edit().putBoolean("schedule_enabled", value).apply()
    }

    /** Entry switch + time text ("HH:MM" or "HH:MM-HH:MM"). */
    fun getScheduleEntry(context: Context, key: String, default: String): Pair<Boolean, String> {
        val prefs = getInstance(context)
        return Pair(prefs.getBoolean("${key}_enabled", false), prefs.getString(key, default) ?: default)
    }

    fun setScheduleEntry(context: Context, key: String, enabled: Boolean, value: String) {
        getInstance(context).edit()
            .putBoolean("${key}_enabled", enabled)
            .putString(key, value)
            .apply()
    }

    fun getDimPercent(context: Context): Int {
        return getInstance(context).getInt("schedule_dim_percent", 20)
    }

    fun setDimPercent(context: Context, value: Int) {
        getInstance(context).edit().putInt("schedule_dim_percent", value).apply()
    }
}