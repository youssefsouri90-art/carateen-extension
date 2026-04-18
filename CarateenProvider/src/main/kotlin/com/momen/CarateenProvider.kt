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
        val items = res.document.select("article, .post-item").mapNotNull { 
            it.toSearchResult() 
        }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = this.selectFirst("a") ?: return null
        val title = a.attr("title").ifBlank { this.selectFirst("h2, h3")?.text() ?: "" }
        val href = fixUrl(a.attr("href"))
        val posterUrl = fixUrl(this.selectFirst("img")?.attr("src") ?: "")

        return newAnimeSearchResponse(title, href, TvType.Cartoon) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val res = app.get("$mainUrl/?s=$query")
        return res.document.select("article, .post-item").mapNotNull { 
            it.toSearchResult() 
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: ""
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        
        val episodes = doc.select(".episodes-list a, .entry-content a[href*='/watch/']").map {
            newEpisode(it.attr("href")) {
                this.name = it.text().trim()
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.Cartoon, episodes.ifEmpty { listOf(newEpisode(url) { name = title }) }) {
            this.posterUrl = fixUrl(poster ?: "")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        doc.select("iframe").forEach { 
            val src = it.attr("src")
            if (src.contains("http")) {
                loadExtractor(fixUrl(src), mainUrl, subtitleCallback, callback)
            }
        }
        return true
    }
}
