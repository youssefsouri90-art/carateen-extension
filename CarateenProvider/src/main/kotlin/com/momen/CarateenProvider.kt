package com.momen

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.nodes.Element

class CarateenProvider : MainAPI() {
    override var mainUrl = "https://carateen.tv"
    override var name = "Carateen"
    override val hasMainPage = true
    override var lang = "ar"
    override val supportedTypes = setOf(TvType.Cartoon, TvType.Anime)

    // الأقسام الرئيسية للموقع
    override val mainPage = mainPageOf(
        "$mainUrl/" to "آخر الحلقات",
        "$mainUrl/category/%d9%83%d8%b1%d8%aa%d9%88%d9%86/" to "مسلسلات كرتون",
        "$mainUrl/category/anime/" to "أنمي صغار",
        "$mainUrl/category/%d8%b3%d9%8a%d9%86%d9%85%d8%a7-%d9%84%d9%84%d8%a3%d8%b7%d9%81%d8%a7%d9%84/" to "سينما الأطفال"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}/page/$page/"
        val document = app.get(url).document
        
        // استخراج العناصر (البوستات)
        val items = document.select("article, .post-item").mapNotNull { element ->
            val titleElement = element.selectFirst("h2, h3, .entry-title")
            val linkElement = element.selectFirst("a") ?: return@mapNotNull null
            
            val title = titleElement?.text() ?: linkElement.attr("title") ?: "بدون عنوان"
            val link = linkElement.attr("href")
            val poster = element.selectFirst("img")?.attr("src") ?: ""

            newAnimeSearchResponse(title, link, TvType.Cartoon) {
                this.posterUrl = fixUrl(poster)
            }
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article, .post-item").mapNotNull { element ->
            val a = element.selectFirst("a") ?: return@mapNotNull null
            newAnimeSearchResponse(a.attr("title") ?: "", a.attr("href"), TvType.Cartoon) {
                this.posterUrl = fixUrl(element.selectFirst("img")?.attr("src") ?: "")
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text()?.trim() ?: ""
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")

        // جلب الحلقات (إذا كان مسلسلاً)
        val episodes = document.select(".episodes-list a, .entry-content a[href*='/watch/']").map { element ->
            newEpisode(element.attr("href")) {
                this.name = element.text().trim()
            }
        }

        return if (episodes.isEmpty()) {
            // إذا لم توجد حلقات، نعتبره فيلماً أو صفحة مشاهدة مباشرة
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = fixUrl(poster ?: "")
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.Cartoon, episodes) {
                this.posterUrl = fixUrl(poster ?: "")
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // البحث عن جميع الـ Iframes (سيرفرات المشاهدة)
        document.select("iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotBlank()) {
                // استخدام الـ Extractor التلقائي لجلب الروابط
                loadExtractor(fixUrl(src), mainUrl, subtitleCallback, callback)
            }
        }

        return true
    }
}
