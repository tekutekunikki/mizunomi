plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// versionCode は -PVERSION_CODE=... で上書きできる。
// GitHub Actions では github.run_number を渡し、更新ごとに増やせるようにする。
// 値が渡されない場合（ローカルビルドなど）はデフォルト 1 を使う。
val appVersionCode = (project.findProperty("VERSION_CODE") as String?)?.toIntOrNull() ?: 1

// テスト配布用の固定署名情報を環境変数から読む。
// Secrets が未設定の環境では null になり、既存の debug 署名がそのまま使われる。
val uploadKeystorePath = System.getenv("MIZUNOMI_UPLOAD_KEYSTORE_PATH")
val uploadKeystorePassword = System.getenv("MIZUNOMI_UPLOAD_KEYSTORE_PASSWORD")
val uploadKeyAlias = System.getenv("MIZUNOMI_UPLOAD_KEY_ALIAS")
val uploadKeyPassword = System.getenv("MIZUNOMI_UPLOAD_KEY_PASSWORD")
val hasUploadSigning = !uploadKeystorePath.isNullOrBlank() &&
    file(uploadKeystorePath).exists() &&
    !uploadKeystorePassword.isNullOrBlank() &&
    !uploadKeyAlias.isNullOrBlank() &&
    !uploadKeyPassword.isNullOrBlank()

android {
    namespace = "com.tekutekunikki.mizunomi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tekutekunikki.mizunomi"
        minSdk = 23
        targetSdk = 35
        versionCode = appVersionCode
        versionName = "1.0"
    }

    signingConfigs {
        // テスト配布用の固定署名。環境変数が揃っている場合のみ作成する。
        if (hasUploadSigning) {
            create("upload") {
                storeFile = file(uploadKeystorePath!!)
                storePassword = uploadKeystorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // 固定署名が利用可能なときだけ debug ビルドに適用する。
            // 未設定の環境では Android 標準の debug keystore がそのまま使われる。
            if (hasUploadSigning) {
                signingConfig = signingConfigs.getByName("upload")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-runtime:2.6.1")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
