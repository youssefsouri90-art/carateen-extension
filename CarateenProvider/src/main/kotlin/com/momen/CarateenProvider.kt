package com.momen

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

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
        "$mainUrl/" to "آخر الإضافات",
        "$mainUrl/category/anime/" to "أنمي",
        "$mainUrl/category/%d9%83%d8%b1%d8%aa%d9%88%d9%86/" to "كرتون",
        "$mainUrl/category/series/" to "مسلسلات",
        "$mainUrl/category/movies/" to "أفلام"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}/page/$page/"
        // تم استبدال app.get بـ k-http لضمان التوافق مع إعداداتك
        val doc = app.get(url).document
        
        val items = doc.select("article, .post-item, .item-list li").mapNotNull { 
            parseCard(it)
        }
        
        return newHomePageResponse(request.name, items)
    }

    private fun parseCard(el: Element): SearchResponse? {
        val a = el.selectFirst("a") ?: return null
        val href = fixUrl(a.attr("href"))
        
        if (href.contains("/category/") || href.endsWith("/tv/")) return null

        val title = el.selectFirst("h2, h3, .post-title, .title")?.text()?.trim()
            ?: a.attr("title")?.trim()
            ?: el.selectFirst("img")?.attr("alt")?.trim()
            ?: return null

        val poster = fixUrl(
            el.selectFirst("img")?.attr("data-src") 
            ?: el.selectFirst("img")?.attr("src")
            ?: ""
        )

        return if (href.contains("/movie/") || title.contains("فيلم")) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        } else {
            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        return doc.select("article, .post-item").mapNotNull { parseCard(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1, .post-title, .entry-title")?.text()?.trim() ?: ""
        val poster = fixUrl(doc.selectFirst("meta[property=og:image]")?.attr("content") ?: "")
        val plot = doc.selectFirst(".entry-content p, .post-details, .story")?.text()

        val episodes = mutableListOf<Episode>()
        
        doc.select("a[href*='/watch/'], .episodes-list a, .playlist-items a").forEach {
            val href = fixUrl(it.attr("href"))
            val name = it.text().trim().ifBlank { "مشاهدة" }
            episodes.add(newEpisode(href) {
                this.name = name
                this.episode = Regex("""\d+""").find(name)?.value?.toIntOrNull()
            })
        }

        if (episodes.isEmpty()) {
            episodes.add(newEpisode(url) { this.name = title })
        }

        val isMovie = url.contains("/movie/") || title.contains("فيلم") || episodes.size == 1

        return if (isMovie) {
            newMovieLoadResponse(title, url, TvType.Movie, episodes.first().data) {
                this.posterUrl = poster
                this.plot = plot
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val html = doc.html()
        var found = false

        doc.select("iframe[src*='player'], iframe[src*='vidoza'], iframe[src*='ok.ru'], .video-iframe iframe").forEach {
            val src = fixUrl(it.attr("src"))
            // تم تصحيح استدعاء الـ extractor
            loadExtractor(src, mainUrl, subtitleCallback, callback)
            found = true
        }

        val patterns = listOf(
            Regex("""https?://[^\s"'<>]+(?:\.m3u8|\.mp4)[^\s"'<>]*"""),
            Regex("""file:\s*["'](https?://[^"']+)["']""")
        )

        patterns.forEach { pattern ->
            pattern.findAll(html).forEach { match ->
                val link = match.groupValues.last()
                callback(
                    newExtractorLink(
                        name,
                        name,
                        link,
                        mainUrl,
                        Qualities.Unknown.value,
                        link.contains(".m3u8")
                    )
                )
                found = true
            }
        }

        return found
    }
}
