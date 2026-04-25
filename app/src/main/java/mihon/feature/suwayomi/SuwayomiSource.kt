package mihon.feature.suwayomi

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.lang.withIOContext

class SuwayomiSource(internal val prefs: SuwayomiPreferences) : Source {

    override val id: Long = ID
    override val name: String = "Suwayomi"
    override val lang: String = "all"

    val baseUrl: String get() = prefs.serverUrl.trimEnd('/')
    val apiUrl: String get() = "$baseUrl/api/graphql"

    private val cookieJar = object : CookieJar {
        private val store = mutableListOf<Cookie>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = synchronized(store) {
            cookies.forEach { new -> store.removeAll { old -> old.name == new.name && old.domain == new.domain } }
            store.addAll(cookies)
            Unit
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(store) { store.toList() }
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .build()

    internal val api: SuwayomiApi by lazy { SuwayomiApi(client, this) }

    suspend fun login() = withIOContext {
        api.login(prefs.username, prefs.password)
    }

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