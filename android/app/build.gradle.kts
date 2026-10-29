plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The on-device model is copied from the repo's models/gguf (built by
// scripts/get-model.ps1, not committed) into generated assets at build time.
val llmModel = "smollm2-135m-instruct-q4_k_m.gguf"
val llmAssetsDir = layout.buildDirectory.dir("generated/llmAssets")
val copyLlmModel by tasks.registering(Copy::class) {
    val source = rootProject.file("../models/gguf/$llmModel")
    doFirst { check(source.exists()) { "missing $source: run scripts/get-model.ps1 first" } }
    from(source)
    into(llmAssetsDir)
}

android {
    namespace = "com.airnd.dda"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.airnd.dda"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "0.2-m2"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                // llama.cpp built as Debug is far too slow to judge; always optimize.
                arguments += "-DCMAKE_BUILD_TYPE=Release"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    sourceSets["main"].assets.srcDir(llmAssetsDir)

    androidResources {
        // Stored, not deflated: the model is copied out once and read as a file.
        noCompress += "gguf"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    buildFeatures {
        compose = true
    }
}

tasks.named("preBuild") { dependsOn(copyLlmModel) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
