plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val apiBaseUrl = providers.fileContents(rootProject.layout.projectDirectory.file(".env"))
    .asText
    .map { readApiBaseUrl(it) }
    .get()

android {
    namespace = "com.example.messenger"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.messenger"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", javaStringLiteral(apiBaseUrl))
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "libsignal_jni*.dylib",
                "signal_jni*.dll",
                "**/libsignal_jni_testing.so",
            )
        }
    }
}

private fun readApiBaseUrl(text: String): String {
    val value = text.lineSequence()
        .map { it.substringBefore("#").trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) null
            else {
                line.substring(0, separator).trim() to
                    line.substring(separator + 1).trim().trim('"', '\'')
            }
        }
        .toMap()["API_BASE_URL"]
        ?: error("API_BASE_URL is missing from mobile/.env. Copy mobile/.env.example to mobile/.env.")
    require(value.startsWith("http://") || value.startsWith("https://")) {
        "API_BASE_URL must start with http:// or https://"
    }
    return if (value.endsWith("/")) value else "$value/"
}

private fun javaStringLiteral(value: String): String {
    val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.libsignal.android)
    implementation(libs.libsignal.client)
    coreLibraryDesugaring (libs.desugar.jdk.libs)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}