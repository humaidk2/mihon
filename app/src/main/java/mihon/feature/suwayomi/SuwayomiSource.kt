package mihon.feature.suwayomi

import eu.kanade.tachiyomi.source.Source
import okhttp3.Credentials
import okhttp3.OkHttpClient

class SuwayomiSource(prefs: SuwayomiPreferences) : Source {

    override val id: Long = ID
    override val name: String = "Suwayomi"
    override val lang: String = "all"

    val baseUrl: String = prefs.serverUrl.trimEnd('/')
    val apiUrl: String = "$baseUrl/api/graphql"

    val client: OkHttpClient = OkHttpClient.Builder()
        .apply {
            val u = prefs.username
            val p = prefs.password
            if (u.isNotEmpty()) {
                addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("Authorization", Credentials.basic(u, p))
                            .build(),
                    )
                }
            }
        }
        .build()

    companion object {
        const val ID = 9_000_000_000_000_000L
    }
}