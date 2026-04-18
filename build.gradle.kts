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

        tasks.withType<KotlinJvmCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                freeCompilerArgs.add("-Xskip-metadata-version-check")
            }
        }
    }

    dependencies {
        // تعريف يدوي للـ Configurations لتجنب أي تعارض
        val cloudstream by configurations.getting
        
        // الطريقة الصحيحة والمضمونة في Kotlin DSL داخل subprojects
        add(cloudstream.name, "com.github.lagradost:cloudstream3:master-SNAPSHOT")
        add("implementation", "org.jsoup:jsoup:1.18.3")
        add("implementation", "com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
        // NiceHttp مدمجة في السناب شوت، لا تضفها لكي لا تفشل العملية
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
