package com.momen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.ExtractorLink
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

@CloudstreamPlugin
class CarateenPlugin: Plugin() {
    override fun load(context: Context) {
        // تسجيل المزود ليتعرف عليه التطبيق
        registerMainAPI(CarateenProvider())
    }
}

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
        val items = res.document.select("article, .post-item").mapNotNull { element ->
            val a = element.selectFirst("a") ?: return@mapNotNull null
            newAnimeSearchResponse(a.attr("title"), a.attr("href"), TvType.Cartoon) {
                this.posterUrl = fixUrl(element.selectFirst("img")?.attr("src") ?: "")
            }
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun load(url: String): LoadResponse {
        val res = app.get(url)
        val doc = res.document
        val title = doc.selectFirst("h1")?.text() ?: ""
        
        val episodes = doc.select(".episodes-list a, .entry-content a[href*='/watch/']").map {
            newEpisode(it.attr("href")) {
                this.name = it.text().trim()
            }
        }

        return if (episodes.isEmpty()) {
             newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = fixUrl(doc.selectFirst("meta[property=og:image]")?.attr("content") ?: "")
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.Cartoon, episodes) {
                this.posterUrl = fixUrl(doc.selectFirst("meta[property=og:image]")?.attr("content") ?: "")
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = app.get(data)
        res.document.select("iframe").forEach { 
            val src = it.attr("src")
            if (src.contains("http")) {
                loadExtractor(fixUrl(src), mainUrl, subtitleCallback, callback)
            }
        }
        return true
    }
}
