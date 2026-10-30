import java.util.Properties
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase is configured by app/google-services.json (provided by CI secrets, never committed).
// Without it the app still builds and runs: reading, Psalms, saved verses and the journal work
// offline, and community features show a friendly "unavailable" state.
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

val config = Properties().apply {
    val f = rootProject.file("config/app.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun cfg(name: String, fallback: String): String =
    (project.findProperty(name) as String?) ?: config.getProperty(name) ?: fallback

// Google's public AdMob test ids, used for debug and QA builds so development never touches live ads.
val testAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerId = "ca-app-pub-3940256099942544/9214589741"

android {
    namespace = "com.hartmannstudios.psalmwell"
    compileSdk = 37

    signingConfigs {
        create("upload") {
            // Release signing happens outside the repository. When these environment variables are
            // present (local release build), Gradle signs with them; otherwise the bundle is unsigned.
            val store = System.getenv("PSALMWELL_KEYSTORE")
            if (store != null) {
                storeFile = file(store)
                storePassword = System.getenv("PSALMWELL_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PSALMWELL_KEY_ALIAS") ?: "upload"
                keyPassword = System.getenv("PSALMWELL_KEY_PASSWORD") ?: System.getenv("PSALMWELL_KEYSTORE_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.hartmannstudios.psalmwell"
        minSdk = 26
        targetSdk = 36
        versionCode = cfg("VERSION_CODE", "1").toInt()
        versionName = cfg("VERSION_NAME", "1.0.0")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "API_BASE_URL", "\"${cfg("API_BASE_URL", "https://psalmwell-api.charles-h-hartmann1.workers.dev")}\"")
        buildConfigField("String", "SITE_URL", "\"${cfg("SITE_URL", "https://psalmwell.web.app")}\"")
        buildConfigField("String", "SUPPORT_EMAIL", "\"${cfg("SUPPORT_EMAIL", "me@charleshartmann.com")}\"")
        buildConfigField("Boolean", "FIREBASE_CONFIGURED", hasFirebaseConfig.toString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = testAdMobAppId
            buildConfigField("String", "BANNER_AD_UNIT_ID", "\"$testBannerId\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["admobAppId"] = cfg("ADMOB_APP_ID", testAdMobAppId)
            buildConfigField("String", "BANNER_AD_UNIT_ID", "\"${cfg("ADMOB_BANNER_ID", testBannerId)}\"")
            if (System.getenv("PSALMWELL_KEYSTORE") != null) signingConfig = signingConfigs.getByName("upload")
        }
        // Same code shrinking as release, debug-signed, Google test ads: used for automated device checks.
        create("qa") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            manifestPlaceholders["admobAppId"] = testAdMobAppId
            buildConfigField("String", "BANNER_AD_UNIT_ID", "\"$testBannerId\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}


// Third-party notices that Google's libraries (ML Kit, Firebase, Play services, Ads) ship inside their
// packages, collected at build time into an app asset that Settings > About shows.
abstract class OpenSourceNoticesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val libraries: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun collect() {
        val byText = linkedMapOf<String, MutableSet<String>>()
        libraries.files.sortedBy { it.name }.forEach { lib ->
            if (!lib.isFile || !(lib.name.endsWith(".aar") || lib.name.endsWith(".jar"))) return@forEach
            val archive = ZipFile(lib)
            try {
                val index = archive.getEntry("third_party_licenses.json")
                val body = archive.getEntry("third_party_licenses.txt")
                if (index != null && body != null) {
                    val bytes: ByteArray = archive.getInputStream(body).readBytes()
                    val json: String = String(archive.getInputStream(index).readBytes(), Charsets.UTF_8)
                    @Suppress("UNCHECKED_CAST")
                    val entries = groovy.json.JsonSlurper().parseText(json) as Map<String, Map<String, Any>>
                    for ((name, pos) in entries) {
                        val start = (pos["start"] as Number).toInt()
                        val length = (pos["length"] as Number).toInt()
                        if (start >= 0 && start + length <= bytes.size) {
                            val text = String(bytes, start, length, Charsets.UTF_8).trim()
                            byText.getOrPut(text) { sortedSetOf() }.add(name)
                        }
                    }
                }
            } finally {
                archive.close()
            }
        }
        val out = outputDir.get().asFile
        out.mkdirs()
        val file = File(out, "open_source_notices.txt")
        file.writeText(
            byText.entries.sortedBy { it.value.first().lowercase() }
                .joinToString("\n\u000C\n") { (text, names) -> names.joinToString(", ") + "\n" + text }
        )
    }
}

val openSourceNotices = tasks.register<OpenSourceNoticesTask>("collectOpenSourceNotices") {
    val runtime = configurations.named("releaseRuntimeClasspath")
    libraries.from(runtime.map { c ->
        c.incoming.artifactView {
            lenient(true)
            attributes { attribute(Attribute.of("artifactType", String::class.java), "aar") }
        }.files
    })
    libraries.from(runtime.map { c ->
        c.incoming.artifactView {
            lenient(true)
            attributes { attribute(Attribute.of("artifactType", String::class.java), "jar") }
        }.files
    })
    outputDir.set(layout.buildDirectory.dir("generated/notices"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(openSourceNotices, OpenSourceNoticesTask::outputDir)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    // On-device translation of the interface and scripture into the reader's language.
    implementation("com.google.mlkit:translate:17.0.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
