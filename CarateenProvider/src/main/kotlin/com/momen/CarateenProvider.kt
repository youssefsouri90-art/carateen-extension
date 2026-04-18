
package com.momen

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class CarateenProvider : MainAPI() {
    override var mainUrl = "https://carateen.tv"
    override var name = "Carateen"
    override val hasMainPage = true
    override var lang = "ar"
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Anime,
        TvType.Movie,
        TvType.Cartoon
    )

    override val mainPage = mainPageOf(
        "$mainUrl" to "الرئيسية",
        "$mainUrl/category/anime/" to "أنمي",
        "$mainUrl/category/series/" to "مسلسلات",
        "$mainUrl/category/movies/" to "أفلام"
    )

    private fun fix(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return fixUrl(url)
    }

    private fun parseCard(el: Element): SearchResponse? {
        val a = when {
            el.tagName() == "a" && el.hasAttr("href") -> el
            else -> el.selectFirst("a[href]")
        } ?: return null

        val href = fix(a.attr("href")) ?: return null
        if ("/watch/" in href) return null

        val title = a.selectFirst("img[alt]")?.attr("alt")?.trim()?.takeIf { it.isNotBlank() }
            ?: a.selectFirst("h1,h2,h3,h4,.title,.entry-title")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: return null

        val poster = fix(
            a.selectFirst("img[data-src]")?.attr("data-src")
                ?: a.selectFirst("img[src]")?.attr("src")
        )

        val type = when {
            href.contains("/movie/") || href.contains("/movies/") -> TvType.Movie
            href.contains("/anime/") -> TvType.Anime
            else -> TvType.TvSeries
        }

        return newSearchResponse(title, href, type) {
            this.posterUrl = poster
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data).document
        val results = doc.select("a:has(img)")
            .mapNotNull { parseCard(it) }
            .distinctBy { it.url }
        return newHomePageResponse(request.name, results)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=${query.urlEncoded()}").document
        return doc.select("a:has(img)")
            .mapNotNull { parseCard(it) }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fix(
            doc.selectFirst("meta[property=og:image]")?.attr("content")
                ?: doc.selectFirst("img[data-src]")?.attr("data-src")
                ?: doc.selectFirst("img[src]")?.attr("src")
        )
        val plot = doc.selectFirst("meta[name=description]")?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst(".story,.plot,.description,.entry-content p")?.text()

        val tags = doc.select("a[rel=tag], .genres a, .tagcloud a")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }

        val watchLinks = linkedMapOf<String, Episode>()
        doc.select("a[href*=/watch/]").forEach { a ->
            val href = fix(a.attr("href")) ?: return@forEach
            if (!href.contains("/watch/")) return@forEach
            val text = a.text().trim()
            val epNum = Regex("(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
            watchLinks[href] = EpisodeData(href).toEpisode(epNum)
        }

        val isMovie = url.contains("/movie/") || tags.any { it.contains("فيلم") } || watchLinks.isEmpty()

        return if (!isMovie && watchLinks.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.Anime, watchLinks.values.toList()) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
            }
        } else {
            val watchUrl = watchLinks.keys.firstOrNull() ?: url
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
        val doc = app.get(data, referer = mainUrl).document
        val html = doc.html()

        val direct = doc.select("video source[src], source[src], iframe[src], a[href$=.m3u8], a[href$=.mp4]")
            .mapNotNull { fix(it.attr("src").ifBlank { it.attr("href") }) }

        val patterns = listOf(
            Regex("https?://[^\\\"'\\s>]+\\.m3u8[^\\\"'\\s<]*"),
            Regex("https?://[^\\\"'\\s>]+master\\.m3u8[^\\\"'\\s<]*"),
            Regex("https?://[^\\\"'\\s>]+\\.mp4[^\\\"'\\s<]*")
        )

        val found = (direct + patterns.flatMap { rx -> rx.findAll(html).map { it.value }.toList() })
            .distinct()
            .filter { it.startsWith("http") }

        found.forEach { link ->
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

        return found.isNotEmpty()
    }
}
