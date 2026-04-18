import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath("com.github.recloudstream:gradle:master-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    extensions.configure<CloudstreamExtension> {
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "youssefsouri90-art/carateen-extension")
    }

    extensions.configure<BaseExtension> {
        namespace = "com.momen.carateen"
        compileSdkVersion(35)

        defaultConfig {
            minSdk = 21
            targetSdk = 35
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                freeCompilerArgs.addAll("-Xskip-metadata-version-check")
            }
        }
    }

    // هنا استخدما الصيغة المباشرة التي تمنع خطأ "Unresolved reference implementation"
    dependencies {
        "cloudstream"("com.github.lagradost:cloudstream3:pre-release")
        
        implementation(kotlin("stdlib"))
        implementation("org.jsoup:jsoup:1.18.3")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
        
        // إضافة المكتبة التي سببت الصداع سابقاً بشكل مدمج
        implementation("com.github.lagradost:NiceHttp:0.4.11") 
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
