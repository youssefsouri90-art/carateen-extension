dependencies {
        val cloudstream by configurations
        val implementation by configurations

        // استيراد النسخة التي تحتوي بالتأكيد على NiceHttp مدمجة أو كاعتمادية واضحة
        cloudstream("com.github.lagradost:cloudstream3:master-SNAPSHOT")
        
        // استيراد NiceHttp بشكل منفصل ولكن برابط JitPack الصحيح (حالة الأحرف مهمة)
        implementation("com.github.lagradost:NiceHttp:main-SNAPSHOT")
        
        implementation(kotlin("stdlib"))
        implementation("org.jsoup:jsoup:1.18.3")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    }
