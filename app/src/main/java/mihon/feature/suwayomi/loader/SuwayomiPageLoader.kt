package mihon.feature.suwayomi.loader

import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.loader.PageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mihon.feature.suwayomi.SuwayomiSource
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class SuwayomiPageLoader(
    private val chapter: ReaderChapter,
    private val source: SuwayomiSource,
    private val chapterCache: ChapterCache = Injekt.get(),
) : PageLoader() {

    private val json: Json by injectLazy()

    override var isLocal: Boolean = false

    override suspend fun getPages(): List<ReaderPage> {
        val domainChapter = chapter.chapter.toDomainChapter()!!
        val pageUrls = try {
            chapterCache.getPageListFromCache(domainChapter).map { it.imageUrl!! }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            fetchPageUrls(chapter.chapter.url)
        }
        return pageUrls.mapIndexed { index, url ->
            ReaderPage(index, imageUrl = url)
        }
    }

    private suspend fun fetchPageUrls(chapterId: String): List<String> = withIOContext {
        val id = chapterId.toIntOrNull() ?: error("Invalid Suwayomi chapter ID: $chapterId")
        val query = "mutation { fetchChapterPages(input: { chapterId: $id }) { pages } }"
        val payload = buildJsonObject { put("query", query) }
        val response = source.client.newCall(
            Request.Builder()
                .url(source.apiUrl)
                .post(payload.toString().toRequestBody(jsonMime))
                .build(),
        ).awaitSuccess()
        val result = with(json) { response.parseAs<FetchPagesResponse>() }
        result.data.fetchChapterPages.pages.map { "${source.baseUrl}$it" }
    }

    override suspend fun loadPage(page: ReaderPage) = withIOContext {
        val imageUrl = page.imageUrl ?: return@withIOContext
        if (page.status is Page.State.Error) page.status = Page.State.Queue
        if (page.status != Page.State.Queue) return@withIOContext

        try {
            if (chapterCache.isImageInCache(imageUrl)) {
                page.stream = { chapterCache.getImageFile(imageUrl).inputStream() }
                page.status = Page.State.Ready
                return@withIOContext
            }
            page.status = Page.State.DownloadImage
            val response = source.client.newCall(
                Request.Builder().url(imageUrl).build(),
            ).awaitSuccess()
            chapterCache.putImageToCache(imageUrl, response)
            page.stream = { chapterCache.getImageFile(imageUrl).inputStream() }
            page.status = Page.State.Ready
        } catch (e: Throwable) {
            page.status = Page.State.Error(e)
            if (e is CancellationException) throw e
        }
    }

    override fun recycle() {
        super.recycle()
        chapter.pages?.let { pages ->
            launchIO {
                try {
                    val toSave = pages.map { Page(it.index, it.url, it.imageUrl) }
                    chapterCache.putPageListToCache(chapter.chapter.toDomainChapter()!!, toSave)
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                }
            }
        }
    }
}

@Serializable
private data class FetchPagesResponse(val data: FetchPagesData)

@Serializable
private data class FetchPagesData(val fetchChapterPages: FetchPagesPayload)

@Serializable
private data class FetchPagesPayload(val pages: List<String>)