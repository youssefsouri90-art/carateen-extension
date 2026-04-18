@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")
package com.momen

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

class CarateenProvider : MainAPI() {
    override var mainUrl = "https://carateen.tv"
    override var name = "Carateen"
    override val hasMainPage = true
    override var lang = "ar"

    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Anime,
        TvType.Movie
    )

    override val mainPage = mainPageOf(
        "$mainUrl" to "الرئيسية",
        "$mainUrl/category/anime/" to "أنمي",
        "$mainUrl/category/series/" to "مسلسلات",
        "$mainUrl/category/movies/" to "أفلام"
    )

    private fun getDoc(url: String): Document {
        return Jsoup.connect(url)
            .userAgent("Mozilla/5.0")
            .header("Referer", mainUrl)
            .timeout(15000)
            .get()
    }

    private fun safeFix(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return fixUrl(url)
    }

    // تم تعديل الدالة لإزالة شرط /watch/ وتحسين استخراج العناوين
    private fun parseCard(el: Element): SearchResponse? {
        val a: Element = if (el.tagName() == "a" && el.hasAttr("href")) {
            el
        } else {
            el.selectFirst("a[href]") ?: return null
        }

        val href = safeFix(a.attr("href")) ?: return null
        
        // تم حذف السطر الذي كان يسبب اختفاء المحتوى (if href.contains("/watch/"))

        val title =
            a.selectFirst("img[alt]")?.attr("alt")?.trim()?.takeIf { it.isNotBlank() }
                ?: a.selectFirst("h1,h2,h3,h4,.title,.entry-title")?.text()?.trim()?.takeIf { it.isNotBlank() }
                ?: a.attr("title").trim().takeIf { it.isNotBlank() }
                ?: return null

        val poster = safeFix(
            a.selectFirst("img[data-src]")?.attr("data-src")
                ?: a.selectFirst("img[src]")?.attr("src")
        )

        val isMovieCard =
            href.contains("/movie/") ||
            href.contains("/movies/") ||
            title.contains("فيلم")

        return if (isMovieCard) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        } else {
            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = getDoc(request.data)

        // تم توسيع المحددات (Selectors) لضمان التقاط جميع البطاقات
        val results = doc.select("article a:has(img), .post a:has(img), .item a:has(img), a:has(img)")
            .mapNotNull { element -> parseCard(element) }
            .distinctBy { response -> response.url }

        return newHomePageResponse(request.name, results)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = URLEncoder.encode(query, "UTF-8")
        val doc = getDoc("$mainUrl/?s=$q")

        // تم تحسين محدد البحث أيضاً
        return doc.select("article a:has(img), .post a:has(img), .item a:has(img), a:has(img)")
            .mapNotNull { element -> parseCard(element) }
            .distinctBy { response -> response.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = getDoc(url)

        val title = doc.selectFirst("h1")?.text()?.trim() ?: return null

        val poster = safeFix(
            doc.selectFirst("meta[property=og:image]")?.attr("content")
                ?: doc.selectFirst("img[data-src]")?.attr("data-src")
                ?: doc.selectFirst("img[src]")?.attr("src")
        )

        val plot = doc.selectFirst("meta[name=description]")?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst(".story,.plot,.description,.entry-content p")?.text()

        val tags = doc.select("a[rel=tag], .genres a, .tagcloud a")
            .map { tag -> tag.text().trim() }
            .filter { it.isNotBlank() }

        val episodes = mutableListOf<Episode>()

        doc.select("a[href*=/watch/]").forEach { a ->
            val href = safeFix(a.attr("href")) ?: return@forEach
            val text = a.text().trim()
            val epNum = Regex("(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()

            episodes.add(
                newEpisode(href) {
                    this.name = if (text.isNotBlank()) text else null
                    this.episode = epNum
                }
            )
        }

        val isMovie = url.contains("/movie/") || tags.any { it.contains("فيلم") } || episodes.isEmpty()

        return if (!isMovie) {
            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
            }
        } else {
            val watchUrl = episodes.firstOrNull()?.data ?: url
            newMovieLoadResponse(title, url, TvType.Movie, watchUrl) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = getDoc(data)
        val html = doc.html()

        val directLinks = mutableListOf<String>()

        doc.select("video source[src], source[src], iframe[src], a[href$=.m3u8], a[href$=.mp4]")
            .forEach { el ->
                val raw = el.attr("src").ifBlank { el.attr("href") }
                val fixed = safeFix(raw)
                if (!fixed.isNullOrBlank() && fixed.startsWith("http")) {
                    directLinks.add(fixed)
                }
            }

        val regexes = listOf(
            Regex("https?://[^\\\"'\\s>]+\\.m3u8[^\\\"'\\s<]*"),
            Regex("https?://[^\\\"'\\s>]+master\\.m3u8[^\\\"'\\s<]*"),
            Regex("https?://[^\\\"'\\s>]+\\.mp4[^\\\"'\\s<]*")
        )

        regexes.forEach { rx ->
            rx.findAll(html).forEach { match ->
                directLinks.add(match.value)
            }
        }

        val links = directLinks.distinct()

        links.forEach { link ->
            callback(
                ExtractorLink(
                    source = name,
                    name = if (link.contains(".m3u8")) "$name HLS" else name,
                    url = link,
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    isM3u8 = link.contains(".m3u8")
                )
            )
        }

        return links.isNotEmpty()
    }
}
