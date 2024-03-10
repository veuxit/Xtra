plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.navigation.safeargs)
}

kotlin {
    jvmToolchain(17)
}

android {
    signingConfigs {
        getByName("debug") {
            keyAlias = "debug"
            keyPassword = "123456"
            storeFile = file("debug-keystore.jks")
            storePassword = "123456"
        }
    }
    namespace = "com.github.andreyasadchy.xtra"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.github.andreyasadchy.xtra"
        minSdk = 16
        targetSdk = 34
        versionCode = 121
        versionName = "2.30.2"
        resourceConfigurations += listOf("ar", "de", "en", "es", "fr", "in", "ja", "pt-rBR", "ru", "tr", "zh-rTW", "zh-rCN")
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            signingConfig = signingConfigs.getByName("debug")
            multiDexEnabled = true
        }
        getByName("release") {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    lint {
        disable += "ContentDescription"
    }
    configurations.all {
        resolutionStrategy.force(listOf(
            "androidx.hilt:hilt-compiler:1.1.0",
            "androidx.hilt:hilt-work:1.1.0",
            "androidx.lifecycle:lifecycle-common-java8:2.7.0-alpha03",
            "androidx.lifecycle:lifecycle-livedata-ktx:2.7.0-alpha03",
            "androidx.lifecycle:lifecycle-process:2.7.0-alpha03",
            "androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0-alpha03",
            "androidx.media3:media3-exoplayer:1.2.1",
            "androidx.media3:media3-exoplayer-hls:1.2.1",
            "androidx.media3:media3-session:1.2.1",
            "androidx.media3:media3-ui:1.2.1",
            "androidx.webkit:webkit:1.9.0-alpha01",
            "com.google.android.material:material:1.11.0",
            "com.squareup.okhttp3:okhttp:3.12.13",
            "com.squareup.okhttp3:logging-interceptor:3.12.13",
            "com.squareup.retrofit2:retrofit:2.6.4",
            "com.squareup.retrofit2:converter-gson:2.6.4",
            "com.google.code.gson:gson:2.9.1",
        ))
    }
}

dependencies {
    implementation("org.conscrypt:conscrypt-android:2.5.2")
    implementation("androidx.multidex:multidex:2.0.1")

    //UI
    implementation(libs.material)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.preference.ktx)
    implementation(libs.swiperefreshlayout)
    implementation(libs.flexbox)
    implementation(libs.draglistview)

    //Architecture components
    implementation(libs.paging.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.common.java8)
    implementation(libs.lifecycle.process)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.core.ktx)
    implementation(libs.fragment.ktx)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.webkit)

    //Misc
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.gson)

    implementation(libs.fetch)
    implementation(libs.fetch.okhttp)
    implementation(libs.okio)
    implementation(libs.open.m3u8)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.checker.qual)

    implementation(libs.glide)
    ksp(libs.glide.ksp)
    implementation(libs.glide.okhttp)
    implementation(libs.glide.webpdecoder)

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.extension.compiler)

    implementation(libs.coroutines)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Delete large build log files from ~/.gradle/daemon/X.X/daemon-XXX.out.log
// Source: https://discuss.gradle.org/t/gradle-daemon-produces-a-lot-of-logs/9905
File("${project.gradle.gradleUserHomeDir.absolutePath}/daemon/${project.gradle.gradleVersion}").listFiles()?.forEach {
    if (it.name.endsWith(".out.log")) {
        // println("Deleting gradle log file: $it") // Optional debug output
        it.delete()
    }
}