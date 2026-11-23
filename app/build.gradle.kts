import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.novelforge.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.novelforge.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 66
        versionName = "0.4.60"
    }

    // 签名密码不写死在仓库里（仓库是公开的，硬编码等于公开密钥）。
    // 本地：keystore/keystore.properties（.gitignore 已挡）
    // CI  ：GitHub Secrets → 环境变量
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore/keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun secret(key: String, env: String): String? =
        (keystoreProps.getProperty(key) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

    signingConfigs {
        create("fixed") {
            val ksPath = rootProject.file("keystore/wenforge.p12")
            // keystore 文件也不进仓库；本地缺了就退回 debug 签名，照样能装
            if (ksPath.exists()) {
                storeFile = ksPath
                storePassword = secret("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = secret("keyAlias", "KEYSTORE_ALIAS") ?: "wenforge"
                keyPassword = secret("keyPassword", "KEY_PASSWORD")
                storeType = "PKCS12"
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            // 固定签名，保证每次构建签名一致，可直接覆盖更新
            signingConfig = signingConfigs.getByName("fixed")
            isMinifyEnabled = false
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
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // 网络：OpenAI 兼容接口（第三方 API、Magpie 等网关通用）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // 序列化 + 协程
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
