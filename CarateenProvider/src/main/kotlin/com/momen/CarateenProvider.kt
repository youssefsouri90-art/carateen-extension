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
        "$mainUrl/category/%d9%83%d8%b1%d8%aa%d9%88%d9%86/" to "مسلسلات كرتون"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}/page/$page/"
        val res = app.get(url)
        val items = res.document.select("article, .post-item").mapNotNull { element: Element ->
            val a = element.selectFirst("a") ?: return@mapNotNull null
            newAnimeSearchResponse(a.attr("title") ?: "No Title", a.attr("href")) {
                this.posterUrl = fixUrl(element.selectFirst("img")?.attr("src") ?: "")
            }
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun load(url: String): LoadResponse {
        val res = app.get(url)
        val doc = res.document
        val title = doc.selectFirst("h1")?.text() ?: ""
        
        val episodes = doc.select("a[href*='/watch/'], .episodes-list a").map { element: Element ->
            newEpisode(element.attr("href")) { this.name = element.text().trim() }
        }
        
        return newTvSeriesLoadResponse(title, url, TvType.Cartoon, if(episodes.isEmpty()) listOf(newEpisode(url){ name = title }) else episodes) {
            this.posterUrl = fixUrl(doc.selectFirst("meta[property=og:image]")?.attr("content") ?: "")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = app.get(data)
        val iframes = res.document.select("iframe")
        
        // تم استبدال forEach بـ for لإصلاح خطأ Suspension functions
        for (element in iframes) {
            val src = element.attr("src")
            if (src.contains("http")) {
                loadExtractor(fixUrl(src), mainUrl, subtitleCallback, callback)
            }
        }
        return true
    }
}
