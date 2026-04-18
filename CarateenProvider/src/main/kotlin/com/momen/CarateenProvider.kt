package com.momen

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.nodes.Element

// الحل الجذري لمشكلة Unresolved reference 'app'
import com.lagradost.cloudstream3.app

class CarateenProvider : MainAPI() {
    override var mainUrl = "https://carateen.tv"
    override var name = "Carateen"
    override val hasMainPage = true
    override var lang = "ar"
    
    override val supportedTypes = setOf(
        TvType.Cartoon,
        TvType.Anime
    )

    override val mainPage = mainPageOf(
        "$mainUrl/" to "آخر الإضافات",
        "$mainUrl/category/%d9%83%d8%b1%d8%aa%d9%88%d9%86/" to "مسلسلات كرتون",
        "$mainUrl/category/anime/" to "أنمي صغار",
        "$mainUrl/category/%d8%b3%d9%8a%d9%86%d9%85%d8%a7-%d9%84%d9%84%d8%a3%d8%b7%d9%81%d8%a7%d9%84/" to "سينما الأطفال",
        "$mainUrl/category/%d8%a8%d8%b1%d8%a7%d9%85%d8%ac-%d8%a7%d9%8ل%d8%a3%d8%b7%d9%81%d8%a7%d9%84/" to "برامج الأطفال"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}/page/$page/"
        val doc = app.get(url).document
        
        val items = doc.select("article, .post-item").mapNotNull { element ->
            parseCard(element)
        }
        
        return newHomePageResponse(request.name, items)
    }

    private fun parseCard(el: Element): SearchResponse? {
        val a = el.selectFirst("a") ?: return null
        val href = fixUrl(a.attr("href"))
        
        if (href.contains("/category/") || href.endsWith("/tv/")) return null

        val title = el.selectFirst("h2, h3, .post-title")?.text()?.trim()
            ?: a.attr("title")?.trim()
            ?: el.selectFirst("img")?.attr("alt")?.trim()
            ?: return null

        val poster = fixUrl(
            el.selectFirst("img")?.attr("data-src") 
            ?: el.selectFirst("img")?.attr("src")
            ?: ""
        )

        return newAnimeSearchResponse(title, href, TvType.Cartoon) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=$query").document
        return doc.select("article, .post-item").mapNotNull { element -> 
            parseCard(element) 
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1, .post-title")?.text()?.trim() ?: ""
        val poster = fixUrl(doc.selectFirst("meta[property=og:image]")?.attr("content") ?: "")
        val plot = doc.selectFirst(".entry-content p, .post-details, .story")?.text()

        val episodes = mutableListOf<Episode>()
        
        doc.select("a[href*='/watch/'], .episodes-list a, .playlist-items a").forEach { element ->
            val eHref = fixUrl(element.attr("href"))
            val name = element.text().trim().ifBlank { "مشاهدة" }
            episodes.add(newEpisode(eHref) {
                this.name = name
            })
        }

        if (episodes.isEmpty()) {
            episodes.add(newEpisode(url) { this.name = title })
        }

        return newTvSeriesLoadResponse(title, url, TvType.Cartoon, episodes) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = app.get(data)
        val doc = res.document
        val html = res.text
        var found = false

        doc.select("iframe").forEach { element ->
            val src = fixUrl(element.attr("src"))
            if (src.contains("http")) {
                loadExtractor(src, mainUrl, subtitleCallback, callback)
                found = true
            }
        }

        val patterns = listOf(
            Regex("""https?://[^\s"'<>]+(?:\.m3u8|\.mp4)[^\s"'<>]*"""),
            Regex("""file:\s*["'](https?://[^"']+)["']""")
        )

        patterns.forEach { pattern ->
            pattern.findAll(html).forEach { match ->
                val link = match.groupValues.last()
                callback(
                    ExtractorLink(
                        this.name,
                        this.name,
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
