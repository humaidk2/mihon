package mihon.feature.suwayomi

import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy

class SuwayomiApi(
    private val client: OkHttpClient,
    val baseUrl: String,
    private val apiUrl: String,
) {
    private val json: Json by injectLazy()

    suspend fun getLibrary(): List<ServerManga> = withIOContext {
        val q = "{ mangas(condition: { inLibrary: true }) { nodes { id title thumbnailUrl author artist description status } } }"
        with(json) { graphql(q).parseAs<LibraryResponse>() }.data.mangas.nodes
    }

    suspend fun getMangaDetails(mangaId: Int): ServerManga = withIOContext {
        val q = "{ manga(id: $mangaId) { id title thumbnailUrl author artist description status } }"
        with(json) { graphql(q).parseAs<MangaDetailsResponse>() }.data.manga
    }

    suspend fun getChapters(mangaId: Int): List<ServerChapter> = withIOContext {
        val q = "{ chapters(condition: { mangaId: $mangaId }) { nodes { id name chapterNumber sourceOrder } } }"
        with(json) { graphql(q).parseAs<ChaptersResponse>() }.data.chapters.nodes
    }

    private suspend fun graphql(query: String): okhttp3.Response {
        val body = buildJsonObject { put("query", query) }.toString().toRequestBody(jsonMime)
        return client.newCall(Request.Builder().url(apiUrl).post(body).build()).awaitSuccess()
    }

    fun toSManga(m: ServerManga): SManga = SManga.create().also {
        it.url = m.id.toString()
        it.title = m.title
        it.author = m.author
        it.artist = m.artist
        it.description = m.description
        it.thumbnail_url = m.thumbnailUrl?.let { url -> "$baseUrl$url" }
        it.status = when (m.status) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "LICENSED" -> SManga.LICENSED
            "PUBLISHING_FINISHED" -> SManga.PUBLISHING_FINISHED
            "CANCELLED" -> SManga.CANCELLED
            "ON_HIATUS" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        it.initialized = true
    }

    fun toSChapter(c: ServerChapter): SChapter = SChapter.create().also {
        it.url = c.id.toString()
        it.name = c.name
        it.chapter_number = c.chapterNumber.toFloat()
    }
}

@Serializable
data class ServerManga(
    val id: Int,
    val title: String,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val status: String = "UNKNOWN",
)

@Serializable
data class ServerChapter(
    val id: Int,
    val name: String,
    val chapterNumber: Double = 0.0,
    val sourceOrder: Int = 0,
)

@Serializable private data class LibraryResponse(val data: LibraryData)
@Serializable private data class LibraryData(val mangas: MangaNodes)
@Serializable private data class MangaNodes(val nodes: List<ServerManga>)

@Serializable private data class MangaDetailsResponse(val data: MangaDetailsData)
@Serializable private data class MangaDetailsData(val manga: ServerManga)

@Serializable private data class ChaptersResponse(val data: ChaptersData)
@Serializable private data class ChaptersData(val chapters: ChapterNodes)
@Serializable private data class ChapterNodes(val nodes: List<ServerChapter>)