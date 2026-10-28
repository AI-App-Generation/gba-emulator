import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.retropocket.gba.emulator"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.retropocket.gba.emulator"
        minSdk = 24
        targetSdk = 37
        versionCode = 4
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Core libretro chỉ build sẵn cho arm64-v8a (phủ mọi máy Android thật đời 2018+).
        // Giữ 1 ABI để APK không phình gấp 4 lần vì các bản .so trùng chức năng.
        ndk { abiFilters += listOf("arm64-v8a") }

        val dateText = SimpleDateFormat("MM.dd.yyyy").format(Date())
        val taskNames = gradle.startParameter.taskNames.joinToString(" ").lowercase()

        val variant = when {
            "release" in taskNames -> "release"
            "debug" in taskNames -> "debug"
            else -> "unknown"
        }

        val formattedName = "${rootProject.name}_${variant}_v${versionName}_v${versionCode}_$dateText"

        base.archivesName.set(formattedName)
    }

    val appIdDebug = "ca-app-pub-3940256099942544~3347511713"
    val appIdRelease = "ca-app-pub-3850749456614322~9320485037"

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            // Legacy: UMP / manifest com.google.android.gms.ads.APPLICATION_ID
            manifestPlaceholders["app_id"] = appIdDebug
            // Next-Gen: BuildConfig.ADMOB_APP_ID for GlobalConfig.appId
            buildConfigField("String", "ADMOB_APP_ID", "\"$appIdDebug\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["app_id"] = appIdRelease
            buildConfigField("String", "ADMOB_APP_ID", "\"$appIdRelease\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // BẮT BUỘC cho emulator: LibretroDroid nạp core bằng dlopen theo đường dẫn file thật.
    // Nếu để mặc định (nén .so trong APK, không giải nén) thì file không tồn tại trên ổ đĩa
    // -> nạp core luôn thất bại. Cặp cấu hình này ép hệ thống giải nén .so ra nativeLibraryDir.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// Mediation adapters still declare legacy play-services-ads*; Next-Gen (ads-mobile-sdk) replaces them.
configurations.configureEach {
    exclude(group = "com.google.android.gms", module = "play-services-ads")
    exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
    exclude(group = "com.google.android.gms", module = "play-services-ads-api")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // Bộ icon core mặc định đã bị rút gọn ở các bản Compose mới -> dùng bộ extended cho đủ icon
    // (folder, archive, gamepad, download, license...) mà app cần.
    implementation("androidx.compose.material:material-icons-extended")

    implementation(libs.google.material)
    implementation(libs.androidx.navigation.compose.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("io.coil-kt:coil-compose:2.6.0")

    // --- Emulator core stack ---
    implementation(libs.libretrodroid)      // GLRetroView: bọc libretro core, render GL + audio + input
    implementation(libs.radialgamepad)      // gamepad ảo dạng vòng tròn, cùng tác giả với LibretroDroid

    // --- Room: kho game cục bộ ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation("com.airbnb.android:lottie-compose:6.6.2")



    implementation("androidx.multidex:multidex:2.0.1")
    // Required by Adjust to read gps_adid after play-services-ads global exclude
    implementation("com.google.android.gms:play-services-ads-identifier:18.3.0")
    // GMA Next-Gen SDK — replaces play-services-ads (excluded globally above)
    implementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.2.1")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation("com.bbl:module-ads:2.5.2")
    // module-ads đã kéo theo billing client lúc chạy, nhưng không expose ra classpath biên dịch.
    // compileOnly để đọc được ProductDetails.PricingPhase (dò số ngày dùng thử thật của gói) mà
    // không thêm một bản billing thứ hai vào app.
    compileOnly("com.android.billingclient:billing:9.0.0")
    implementation("com.adjust.sdk:adjust-android:5.5.1")
    implementation("com.applovin:applovin-sdk:13.6.3")
    implementation("com.pangle.global:pag-sdk:8.1.0.5") { exclude(group = "com.pangle.global", module = "tiktok-business-android-sdk-comp") }
    implementation("com.google.ads.mediation:pangle:8.1.0.5.0") { exclude(group = "com.pangle.global", module = "tiktok-business-android-sdk-comp") }
    implementation("com.google.ads.mediation:vungle:7.7.4.2")
    implementation("com.google.ads.mediation:applovin:13.6.3.0")
    implementation("com.google.ads.mediation:ironsource:9.3.0.1")
    implementation("com.google.ads.mediation:facebook:6.21.0.4")
    implementation("com.facebook.android:facebook-android-sdk:18.3.0")
    implementation("com.facebook.android:audience-network-sdk:6.22.0")
    implementation("com.google.ads.mediation:mintegral:17.1.61.1")
    implementation("com.unity3d.ads:unity-ads:4.19.0")
    implementation("com.google.ads.mediation:unity:4.19.0.0")
    implementation("com.github.tiktok:tiktok-business-android-sdk:1.5.0")
    implementation("com.appsflyer:af-android-sdk:6.17.6")
    implementation("com.appsflyer:adrevenue:6.9.1")
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.config)
}
