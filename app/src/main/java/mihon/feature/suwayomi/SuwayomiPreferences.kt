package mihon.feature.suwayomi

import android.content.Context

class SuwayomiPreferences(private val context: Context) {

    private val prefs get() = context.getSharedPreferences("suwayomi", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_URL, "") ?: ""
        set(value) { prefs.edit().putString(KEY_URL, value).apply() }

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) { prefs.edit().putString(KEY_USERNAME, value).apply() }

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) { prefs.edit().putString(KEY_PASSWORD, value).apply() }

    companion object {
        private const val KEY_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
    }
}