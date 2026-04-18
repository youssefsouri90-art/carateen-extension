package com.momen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.app

@CloudstreamPlugin
class CarateenPlugin: Plugin() {
    override fun load(context: Context) {
        // تسجيل المزود (Provider) عند تحميل الإضافة
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

    // ... باقي الدوال (getMainPage, load, loadLinks) كما هي في الكود السابق
}
