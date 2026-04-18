package com.momen

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.Qualities

// استخدام استيراد صريح جداً للمكتبات التي تفشل
import com.lagradost.cloudstream3.MainActivity.Companion.app

class CarateenProvider : MainAPI() {
    override var mainUrl = "https://carateen.tv"
    override var name = "Carateen"
    override val hasMainPage = true
    override var lang = "ar"
    
    override val supportedTypes = setOf(
        TvType.Cartoon,
        TvType.Anime
    )

    // الكتالوج المخصص لمحتوى كاراطين الحقيقي
    override val mainPage = mainPageOf(
        "$mainUrl/" to "الرئيسية",
        "$mainUrl/category/%d9%83%d8%b1%d8%aa%d9%88%d9%86/" to "مسلسلات كرتون",
        "$mainUrl/category/anime/" to "أنمي صغار",
        "$mainUrl/category/%d8%b3%d9%8a%d9%86%d9%85%d8%a7-%d9%84%d9%84%d8%a3%d8%b7%d9%81%d8%a7%d9%84/" to "سينما الأطفال"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}/page/$page/"
        val response = app.get(url) // الطلب بشكل منفصل
        val doc = response.document
        
        val items = doc.select("article, .post-item").mapNotNull { element: Element ->
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
        val response = app.get("$mainUrl/?s=$query")
        return response.document.select("article, .post-item").mapNotNull { element: Element -> 
            parseCard(element) 
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val doc = response.document
        val title = doc.selectFirst("h1, .post-title")?.text()?.trim() ?: ""
        val poster = fixUrl(doc.selectFirst("meta[property=og:image]")?.attr("content") ?: "")
        
        val episodes = mutableListOf<Episode>()
        doc.select("a[href*='/watch/'], .episodes-list a").forEach { element: Element ->
            val eHref = fixUrl(element.attr("href"))
            val eName = element.text().trim()
            episodes.add(newEpisode(eHref) {
                this.name = eName
            })
        }

        if (episodes.isEmpty()) episodes.add(newEpisode(url) { this.name = title })

        return newTvSeriesLoadResponse(title, url, TvType.Cartoon, episodes) {
            this.posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(data)
        val doc = response.document
        var found = false

        doc.select("iframe").forEach { element: Element ->
            val src = fixUrl(element.attr("src"))
            if (src.contains("http")) {
                loadExtractor(src, mainUrl, subtitleCallback, callback)
                found = true
            }
        }

        return found
    }
}
