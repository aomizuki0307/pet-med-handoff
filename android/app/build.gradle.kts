import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}

android {
    namespace = "io.github.aomizuki0307.petmed"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.aomizuki0307.petmed"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    flavorDimensions += "backend"
    productFlavors {
        create("mock") {
            dimension = "backend"
            applicationIdSuffix = ".mock"
            versionNameSuffix = "-mock"
        }
        create("prod") {
            dimension = "backend"
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    // Firebase は prod フレーバーのみ（mock は認証情報なしでビルド・起動可能）
    "prodImplementation"(platform(libs.firebase.bom))
    "prodImplementation"(libs.firebase.auth)
    "prodImplementation"(libs.firebase.firestore)
    "prodImplementation"(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.compose.ui.tooling)
}

// google-services.json がある場合のみプラグイン適用（無くても assemble が通る）
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")

    // The root config belongs to the production application id. Google
    // Services otherwise tries to process every flavor and rejects the mock
    // suffix even though that flavor has no Firebase dependency.
    tasks.configureEach {
        if (name.startsWith("processMock") && name.endsWith("GoogleServices")) {
            enabled = false
        }
    }
}
