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
        "$mainUrl/" to "آخر الإضافات",
        "$mainUrl/category/%d9%83%d8%b1%d8%aa%d9%88%d9%86/" to "مسلسلات كرتون",
        "$mainUrl/category/anime/" to "أنمي صغار"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}/page/$page/"
        val doc = app.get(url).document
        val items = doc.select("article, .post-item").mapNotNull { element ->
            val a = element.selectFirst("a") ?: return@mapNotNull null
            newAnimeSearchResponse(a.attr("title") ?: element.selectFirst("h2")?.text() ?: "", a.attr("href")) {
                this.posterUrl = fixUrl(element.selectFirst("img")?.attr("src") ?: "")
            }
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1")?.text() ?: ""
        val episodes = doc.select("a[href*='/watch/'], .episodes-list a").map { element ->
            newEpisode(element.attr("href")) { this.name = element.text() }
        }
        return newTvSeriesLoadResponse(title, url, TvType.Cartoon, episodes)
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        app.get(data).document.select("iframe").forEach { 
            loadExtractor(it.attr("src"), mainUrl, subtitleCallback, callback)
        }
        return true
    }
}
