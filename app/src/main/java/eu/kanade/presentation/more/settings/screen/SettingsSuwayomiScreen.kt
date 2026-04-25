package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.feature.suwayomi.SuwayomiPreferences
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsSuwayomiScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_suwayomi

    @Composable
    override fun getPreferences(): List<Preference> {
        val prefs = remember { Injekt.get<SuwayomiPreferences>() }
        return listOf(
            Preference.PreferenceItem.CustomPreference(title = "Server") {
                ServerConfigContent(prefs)
            },
        )
    }

    @Composable
    private fun ServerConfigContent(prefs: SuwayomiPreferences) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var url by remember { mutableStateOf(prefs.serverUrl) }
        var username by remember { mutableStateOf(prefs.username) }
        var password by remember { mutableStateOf(prefs.password) }
        var showPassword by remember { mutableStateOf(false) }
        var testResult by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Server URL") },
                placeholder = { Text("http://192.168.1.100:8085") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        prefs.serverUrl = url.trimEnd('/')
                        prefs.username = username
                        prefs.password = password
                        context.toast("Saved")
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = {
                        testResult = "Testing…"
                        scope.launch {
                            testResult = testConnection(url.trimEnd('/'), username, password)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Test")
                }
            }

            if (testResult.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = testResult,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (testResult.startsWith("OK")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }

    private suspend fun testConnection(baseUrl: String, username: String, password: String): String =
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder().build()
                // Simple Login: POST form credentials to /login.html to get session cookie
                if (username.isNotEmpty()) {
                    val loginBody = FormBody.Builder()
                        .add("user", username)
                        .add("pass", password)
                        .build()
                    client.newCall(
                        Request.Builder()
                            .url("$baseUrl/login.html")
                            .post(loginBody)
                            .build(),
                    ).execute().close()
                }
                val body = """{"query":"{ aboutServer { version } }"}"""
                val response = client.newCall(
                    Request.Builder()
                        .url("$baseUrl/api/graphql")
                        .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build(),
                ).execute()
                if (response.isSuccessful) {
                    "OK ${response.code} — connection works"
                } else {
                    "Error ${response.code}: ${response.message}"
                }
            } catch (e: Exception) {
                "Failed: ${e.message}"
            }
        }
}