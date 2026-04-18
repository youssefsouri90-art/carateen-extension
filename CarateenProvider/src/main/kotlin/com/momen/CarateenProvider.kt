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
    
    // تم حصر الأنواع في الكرتون والأنمي فقط بما يناسب الموقع
    override val supportedTypes = setOf(
        TvType.Cartoon,
        TvType.Anime
    )

    // الكتالوج الفعلي لموقع كاراطين (الأقسام الموجودة في القائمة الجانبية للموقع)
    override val mainPage = mainPageOf(
        "$mainUrl/" to "الرئيسية (آخر الحلقات)",
        "$mainUrl/category/%d9%83%d8%b1%d8%aa%d9%88%d9%86/" to "مسلسلات كرتون",
        "$mainUrl/category/anime/" to "أنمي صغار",
        "$mainUrl/category/%d8%b3%d9%8a%d9%86%d9%85%d8%a7-%d9%84%d9%84%d8%a3%d8%b7%d9%81%d8%a7%d9%84/" to "سينما الأطفال",
        "$mainUrl/category/%d8%a8%d8%b1%d8%a7%d9%85%d8%ac-%d8%a7%d9%8ل%d8%a3%d8%b7%d9%81%d8%a7%d9%84/" to "برامج الأطفال"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}/page/$page/"
        val doc = app.get(url).document
        
        // التقاط محتوى الكرتون بناءً على هيكلية الموقع (Grid)
        val items = doc.select("article, .post-item, .item-list li").mapNotNull { element ->
            parseCard(element)
        }
        
        return newHomePageResponse(request.name, items)
    }

    private fun parseCard(el: Element): SearchResponse? {
        val a = el.selectFirst("a") ?: return null
        val href = fixUrl(a.attr("href"))
        
        if (href.contains("/category/") || href.endsWith("/tv/")) return null

        val title = el.selectFirst("h2, h3, .post-title, .title, .entry-title")?.text()?.trim()
            ?: a.attr("title")?.trim()
            ?: el.selectFirst("img")?.attr("alt")?.trim()
            ?: return null

        val poster = fixUrl(
            el.selectFirst("img")?.attr("data-src") 
            ?: el.selectFirst("img")?.attr("src")
            ?: el.selectFirst("img")?.attr("data-lazy-src")
            ?: ""
        )

        // كل محتوى الموقع يتم تصنيفه كـ Cartoon ليعمل داخل قسم الكرتون في التطبيق
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
        val title = doc.selectFirst("h1, .post-title, .entry-title")?.text()?.trim() ?: ""
        val poster = fixUrl(doc.selectFirst("meta[property=og:image]")?.attr("content") ?: "")
        val plot = doc.selectFirst(".entry-content p, .post-details, .story, .plot")?.text()

        val episodes = mutableListOf<Episode>()
        
        // جلب حلقات الكرتون
        doc.select("a[href*='/watch/'], .episodes-list a, .playlist-items a").forEach { element ->
            val href = fixUrl(element.attr("href"))
            val name = element.text().trim().ifBlank { "مشاهدة" }
            episodes.add(newEpisode(href) {
                this.name = name
                this.episode = Regex("""\d+""").find(name)?.value?.toIntOrNull()
            })
        }

        if (episodes.isEmpty()) {
            episodes.add(newEpisode(url) { this.name = title })
        }

        // بما أن الموقع مخصص للكرتون، نستخدم تصنيف TvSeries ليعرض قائمة الحلقات
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
        val doc = app.get(data).document
        val html = doc.html()
        var found = false

        // مشغلات موقع كاراطين المعروفة
        doc.select("iframe[src*='player'], iframe[src*='vidoza'], iframe[src*='ok.ru'], .video-iframe iframe").forEach { element ->
            val src = fixUrl(element.attr("src"))
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
                    ExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = link,
                        referer = mainUrl,
                        quality = Qualities.Unknown.value,
                        isM3u8 = link.contains(".m3u8")
                    )
                )
                found = true
            }
        }

        return found
    }
}
