plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val supportedAlphaAbis = setOf("arm64-v8a", "x86_64")
val requestedAbi = providers.gradleProperty("thwiply.abi").orNull
require(requestedAbi == null || requestedAbi in supportedAlphaAbis) {
    "thwiply.abi must be one of ${supportedAlphaAbis.sorted()}; received '$requestedAbi'"
}

val requestedVersionCode = providers.gradleProperty("thwiply.versionCode").orNull?.let { value ->
    value.toIntOrNull()?.takeIf { it > 0 }
        ?: error("thwiply.versionCode must be a positive integer; received '$value'")
}
val requestedVersionName = providers.gradleProperty("thwiply.versionName").orNull?.also { value ->
    require(value.isNotBlank()) {
        "thwiply.versionName must not be blank"
    }
}

android {
    namespace = "thwiply.elopenmike.com"
    compileSdk = 36

    defaultConfig {
        applicationId = "thwiply.elopenmike.com"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        requestedVersionCode?.let { versionCode = it }
        requestedVersionName?.let { versionName = it }
        requestedAbi?.let { abi ->
            ndk {
                abiFilters += abi
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("alpha") {
            initWith(getByName("release"))
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs {
            keepDebugSymbols += "**/*.so"
        }
    }
    sourceSets {
        getByName("androidTest").assets.directories.add("schemas")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    // Room's migration-test runtime needs 1.8.1; align the base graph for Android's
    // consistent test-runtime resolution.
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    
    // Network
    implementation(libs.okhttp)
    
    // LLM
    implementation(libs.litertlm)
    
    // UI & Coroutines
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
