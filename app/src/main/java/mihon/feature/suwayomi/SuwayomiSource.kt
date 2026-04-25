package mihon.feature.suwayomi

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
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

    internal val api: SuwayomiApi by lazy { SuwayomiApi(client, baseUrl, apiUrl) }

    override suspend fun getMangaDetails(manga: SManga): SManga {
        val mangaId = manga.url.toIntOrNull() ?: return manga
        return api.toSManga(api.getMangaDetails(mangaId))
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        val mangaId = manga.url.toIntOrNull() ?: return emptyList()
        return api.getChapters(mangaId).map { api.toSChapter(it) }
    }

    companion object {
        const val ID = 9_000_000_000_000_000L
    }
}