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
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy

class SuwayomiApi(
    private val client: OkHttpClient,
    private val source: SuwayomiSource,
) {
    private val json: Json by injectLazy()

    val baseUrl: String get() = source.baseUrl
    private val apiUrl: String get() = source.apiUrl

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

    suspend fun login(username: String, password: String) = withIOContext {
        if (username.isEmpty()) return@withIOContext
        val body = FormBody.Builder()
            .add("user", username)
            .add("pass", password)
            .build()
        client.newCall(
            Request.Builder()
                .url("$baseUrl/login.html")
                .post(body)
                .build(),
        ).execute().close()
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

    suspend fun getExtensions(): List<ServerExtension> = withIOContext {
        val q = "{ extensions { nodes { pkgName name lang versionName isInstalled hasUpdate isObsolete isNsfw iconUrl } } }"
        with(json) { graphql(q).parseAs<ExtListResponse>() }.data.extensions.nodes
    }

    suspend fun refreshExtensions(): List<ServerExtension> = withIOContext {
        val q = "mutation { fetchExtensions(input: {}) { extensions { pkgName name lang versionName isInstalled hasUpdate isObsolete isNsfw iconUrl } } }"
        with(json) { graphql(q).parseAs<FetchExtResponse>() }.data.fetchExtensions.extensions
    }

    suspend fun installExtension(pkgName: String) = mutateExtension(pkgName, "install: true")
    suspend fun uninstallExtension(pkgName: String) = mutateExtension(pkgName, "uninstall: true")
    suspend fun updateExtension(pkgName: String) = mutateExtension(pkgName, "update: true")

    private suspend fun mutateExtension(pkgName: String, patch: String): ServerExtension? = withIOContext {
        // pkgName is always a Java package name (e.g. eu.kanade.tachiyomi.extension.en.mangadex); safe to interpolate
        val q = """mutation { updateExtension(input: { id: "$pkgName", patch: { $patch } }) { extension { pkgName name lang versionName isInstalled hasUpdate isObsolete isNsfw iconUrl } } }"""
        with(json) { graphql(q).parseAs<UpdateExtResponse>() }.data.updateExtension.extension
    }

    fun extensionIconUrl(ext: ServerExtension): String = "$baseUrl${ext.iconUrl}"

    suspend fun getSources(): List<ServerSource> = withIOContext {
        val q = "{ sources { nodes { id name lang iconUrl supportsLatest isNsfw displayName } } }"
        with(json) { graphql(q).parseAs<SourcesResponse>() }.data.sources.nodes
    }

    // sourceId is a LongString scalar — must be quoted in the GQL literal
    suspend fun fetchSourceManga(
        sourceId: String,
        type: String,
        page: Int,
        query: String? = null,
    ): BrowseResult = withIOContext {
        val queryPart = if (query != null) """, query: "${query.replace("\"", "\\\"")}" """ else ""
        val q = """mutation { fetchSourceManga(input: { source: "$sourceId", type: $type, page: $page$queryPart }) { mangas { id title thumbnailUrl inLibrary } hasNextPage } }"""
        val p = with(json) { graphql(q).parseAs<FetchSourceMangaResp>() }.data.fetchSourceManga
        BrowseResult(p.mangas, p.hasNextPage)
    }

    suspend fun addToLibrary(mangaId: Int): Boolean = withIOContext {
        val q = """mutation { updateManga(input: { id: $mangaId, patch: { inLibrary: true } }) { manga { id inLibrary } } }"""
        with(json) { graphql(q).parseAs<UpdateMangaLibResp>() }.data.updateManga.manga?.inLibrary == true
    }

    fun browseToSManga(m: BrowseManga): SManga = SManga.create().also {
        it.url = m.id.toString()
        it.title = m.title
        it.thumbnail_url = m.thumbnailUrl?.let { url -> "$baseUrl$url" }
        it.initialized = false
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

@Serializable
data class ServerExtension(
    val pkgName: String,
    val name: String,
    val lang: String,
    val versionName: String,
    val isInstalled: Boolean,
    val hasUpdate: Boolean,
    val isObsolete: Boolean,
    val isNsfw: Boolean,
    val iconUrl: String,
)

@Serializable private data class ExtListResponse(val data: ExtListData)
@Serializable private data class ExtListData(val extensions: ExtNodes)
@Serializable private data class ExtNodes(val nodes: List<ServerExtension>)

@Serializable private data class FetchExtResponse(val data: FetchExtData)
@Serializable private data class FetchExtData(val fetchExtensions: FetchExtPayload)
@Serializable private data class FetchExtPayload(val extensions: List<ServerExtension>)

@Serializable private data class UpdateExtResponse(val data: UpdateExtData)
@Serializable private data class UpdateExtData(val updateExtension: UpdateExtPayload)
@Serializable private data class UpdateExtPayload(val extension: ServerExtension?)

@Serializable
data class ServerSource(
    val id: String, // LongString scalar — serialized as String
    val name: String,
    val lang: String,
    val iconUrl: String,
    val supportsLatest: Boolean,
    val isNsfw: Boolean,
    val displayName: String,
)

@Serializable
data class BrowseManga(
    val id: Int,
    val title: String,
    val thumbnailUrl: String? = null,
    val inLibrary: Boolean = false,
)

data class BrowseResult(
    val mangas: List<BrowseManga>,
    val hasNextPage: Boolean,
)

@Serializable private data class SourcesResponse(val data: SourcesData)
@Serializable private data class SourcesData(val sources: SourceNodes)
@Serializable private data class SourceNodes(val nodes: List<ServerSource>)

@Serializable private data class FetchSourceMangaResp(val data: FetchSourceMangaData)
@Serializable private data class FetchSourceMangaData(val fetchSourceManga: FetchSourceMangaPayload)
@Serializable private data class FetchSourceMangaPayload(val mangas: List<BrowseManga>, val hasNextPage: Boolean)

@Serializable private data class UpdateMangaLibResp(val data: UpdateMangaLibData)
@Serializable private data class UpdateMangaLibData(val updateManga: UpdateMangaLibPayload)
@Serializable private data class UpdateMangaLibPayload(val manga: UpdatedMangaLib?)
@Serializable private data class UpdatedMangaLib(val id: Int, val inLibrary: Boolean)