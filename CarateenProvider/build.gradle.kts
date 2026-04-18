import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.0")
        classpath("com.github.recloudstream:gradle:master-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.20")
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
        compileSdkVersion(34)

        defaultConfig {
            minSdk = 21
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        tasks.withType<KotlinJvmCompile>().configureEach {
            kotlinOptions {
                jvmTarget = "1.8"
                freeCompilerArgs = freeCompilerArgs + "-Xskip-metadata-version-check"
            }
        }
    }

    dependencies {
        val cloudstream by configurations.creating
        add(cloudstream.name, "com.github.lagradost:cloudstream3:master-SNAPSHOT")
        
        add("implementation", "org.jsoup:jsoup:1.17.2")
        add("implementation", "com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
        // ملاحظة: لا نحتاج NiceHttp هنا لأنها تأتي مدمجة مع Cloudstream SNAPSHOT
    }
}
