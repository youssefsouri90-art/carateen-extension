package com.momen

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class CarateenProvider : MainAPI() {
    override var mainUrl = "https://carateen.tv"
    override var name = "Carateen"
    override val hasMainPage = true
    override var lang = "ar"
    override val supportedTypes = setOf(TvType.Cartoon)

    override val mainPage = mainPageOf(
        "$mainUrl/" to "آخر الحلقات",
        "$mainUrl/category/%d9%83%d8%b1%d8%aa%d9%88%d9%86/" to "مسلسلات كرتون",
        "$mainUrl/category/anime/" to "أنمي صغار"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}/page/$page/"
        val res = app.get(url)
        val items = res.document.select("article, .post-item").mapNotNull { element ->
            val a = element.selectFirst("a") ?: return@mapNotNull null
            val title = a.attr("title").ifBlank { element.selectFirst("h2, h3")?.text() ?: "" }
            val poster = element.selectFirst("img")?.attr("src") ?: ""
            
            newAnimeSearchResponse(title, a.attr("href"), TvType.Cartoon) {
                this.posterUrl = fixUrl(poster)
            }
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val res = app.get("$mainUrl/?s=$query")
        return res.document.select("article, .post-item").mapNotNull { element ->
            val a = element.selectFirst("a") ?: return@mapNotNull null
            newAnimeSearchResponse(a.attr("title") ?: "", a.attr("href"), TvType.Cartoon) {
                this.posterUrl = fixUrl(element.selectFirst("img")?.attr("src") ?: "")
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: ""
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
        
        val episodes = doc.select(".episodes-list a, .entry-content a[href*='/watch/']").map { element ->
            newEpisode(element.attr("href")) {
                this.name = element.text().trim()
            }
        }

        return if (episodes.isEmpty()) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = fixUrl(poster)
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.Cartoon, episodes) {
                this.posterUrl = fixUrl(poster)
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
        val iframes = doc.select("iframe")
        
        for (iframe in iframes) {
            val src = iframe.attr("src")
            if (src.contains("http")) {
                loadExtractor(fixUrl(src), mainUrl, subtitleCallback, callback)
            }
        }
        return true
    }
}
