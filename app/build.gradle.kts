import java.time.Duration
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

fun resolveGeminiApiKey(project: Project): String {
    (project.findProperty("GEMINI_API_KEY") as? String)?.takeIf { it.isNotBlank() }?.let { return it }
    System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }?.let { return it }

    val envFile = project.rootProject.file(".env")
    if (envFile.exists()) {
        for (rawLine in envFile.readLines()) {
            val line = rawLine.trim()
            if (line.startsWith("GEMINI_API_KEY=")) {
                val value = line.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
                if (value.isNotBlank()) return value
            }
        }
    }

    val localPropFile = project.rootProject.file("local.properties")
    if (localPropFile.exists()) {
        val props = Properties()
        localPropFile.inputStream().use { props.load(it) }
        val propKey = props.getProperty("GEMINI_API_KEY") ?: props.getProperty("gemini.api.key")
        if (!propKey.isNullOrBlank()) return propKey.trim()
    }

    return ""
}

android {
    namespace = "com.laterbox.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.laterbox.app"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 22
        versionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.0.22"

        val embeddedKey = resolveGeminiApiKey(project)
        buildConfigField("String", "EMBEDDED_GEMINI_API_KEY", "\"$embeddedKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (!keystorePath.isNullOrEmpty() && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/DEPENDENCIES",
                "/META-INF/*.kotlin_module"
            )
        }
    }

    lint {
        disable += listOf("ProtectedPermissions", "StartActivityAndCollapseDeprecated")
        abortOnError = true
    }
}

dependencies {
    // AndroidX & Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.navigation.compose)

    // Data & Local Persistence
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    // Background & Networking
    implementation(libs.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.coil.compose)

    // Media3 Playback
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)

    // youtubedl-android
    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)

    // Firebase & Generative AI Logic (Google AI Studio backend)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)

    // Unit & Local Testing
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.work.testing)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    // Debugging Tools
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Android Instrumentation Testing
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

tasks.withType<Test> {
    timeout.set(Duration.ofMinutes(10))

    testLogging {
        events("started", "passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }

    maxHeapSize = "2048m"

    val cacerts = file("/home/antigravity/cacerts")
    if (cacerts.exists()) {
        systemProperty("javax.net.ssl.trustStore", cacerts.absolutePath)
        systemProperty("javax.net.ssl.trustStorePassword", "changeit")
    }
    val httpProxy = System.getenv("http_proxy") ?: System.getenv("HTTP_PROXY")
    if (!httpProxy.isNullOrEmpty()) {
        val port = httpProxy.substringAfterLast(":")
        systemProperty("http.proxyHost", "127.0.0.1")
        systemProperty("http.proxyPort", port)
        systemProperty("https.proxyHost", "127.0.0.1")
        systemProperty("https.proxyPort", port)
    }
}

gradle.taskGraph.whenReady {
    val hasReleaseTask = allTasks.any { task ->
        task.path == ":app:packageRelease" ||
            task.path == ":app:assembleRelease" ||
            task.path == ":app:bundleRelease"
    }
    if (hasReleaseTask) {
        val keystorePath = System.getenv("KEYSTORE_PATH")
        val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
        val keyAlias = System.getenv("KEY_ALIAS")
        val keyPassword = System.getenv("KEY_PASSWORD")
        if (keystorePath.isNullOrBlank() ||
            !file(keystorePath).exists() ||
            keystorePassword.isNullOrBlank() ||
            keyAlias.isNullOrBlank() ||
            keyPassword.isNullOrBlank()
        ) {
            throw GradleException(
                "Release build requires valid KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD. " +
                    "Refusing to fall back to debug signing."
            )
        }
    }
}

