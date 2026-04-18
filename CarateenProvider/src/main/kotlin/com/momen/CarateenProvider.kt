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

    // تعريف الأقسام الرئيسية للموقع
    override val mainPage = mainPageOf(
        "$mainUrl/" to "آخر الحلقات المضافة",
        "$mainUrl/category/%d9%83%d8%b1%d8%aa%d9%88%d9%86/" to "مسلسلات كرتون",
        "$mainUrl/category/anime/" to "أنمي صغار",
        "$mainUrl/category/%d8%b3%d9%8a%d9%86%d9%85%d8%a7-%d9%84%d9%84%d8%a3%d8%b7%d9%81%d8%a7%d9%84/" to "أفلام كرتون"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}/page/$page/"
        val res = app.get(url)
        val doc = res.document
        
        val items = doc.select("article, .post-item, .item").mapNotNull { element: Element ->
            val a = element.selectFirst("a") ?: return@mapNotNull null
            val title = a.attr("title").ifBlank { element.selectFirst("h2, h3")?.text() ?: "بدون عنوان" }
            val link = a.attr("href")
            
            newAnimeSearchResponse(title, link, TvType.Cartoon) {
                this.posterUrl = fixUrl(element.selectFirst("img")?.attr("src") ?: "")
            }
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val res = app.get("$mainUrl/?s=$query")
        return res.document.select("article, .post-item").mapNotNull { element: Element ->
            val a = element.selectFirst("a") ?: return@mapNotNull null
            newAnimeSearchResponse(a.attr("title") ?: "", a.attr("href"), TvType.Cartoon) {
                this.posterUrl = fixUrl(element.selectFirst("img")?.attr("src") ?: "")
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val res = app.get(url)
        val doc = res.document
        val title = doc.selectFirst("h1, .entry-title")?.text() ?: ""
        val poster = fixUrl(doc.selectFirst("meta[property=og:image]")?.attr("content") ?: "")
        
        // جلب قائمة الحلقات
        val episodes = doc.select("a[href*='/watch/'], .episodes-list a").map { element: Element ->
            newEpisode(element.attr("href")) {
                this.name = element.text().trim()
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.Cartoon, if(episodes.isEmpty()) listOf(newEpisode(url){ name = title }) else episodes) {
            this.posterUrl = poster
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
        
        // البحث عن الروابط داخل الـ Iframes
        val iframes = doc.select("iframe")
        
        // هام: استخدام for loop بدلاً من forEach لتجنب أخطاء الـ Suspension functions
        for (iframe in iframes) {
            val src = iframe.attr("src")
            if (src.isNotBlank() && src.startsWith("http")) {
                loadExtractor(fixUrl(src), mainUrl, subtitleCallback, callback)
            }
        }
        
        // البحث عن روابط مباشرة في الأزرار إذا وجدت
        doc.select("a[href*='vidspeeds'], a[href*='ok.ru']").forEach { 
            val link = it.attr("href")
            loadExtractor(link, mainUrl, subtitleCallback, callback)
        }

        return true
    }
}
