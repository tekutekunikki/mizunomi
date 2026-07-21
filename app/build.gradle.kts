plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val testKeystoreFile = providers.environmentVariable("MIZUNOMI_UPLOAD_KEYSTORE_FILE").orNull
val testKeystorePassword = providers.environmentVariable("MIZUNOMI_UPLOAD_KEYSTORE_PASSWORD").orNull
val testKeyAlias = providers.environmentVariable("MIZUNOMI_UPLOAD_KEY_ALIAS").orNull
val testKeyPassword = providers.environmentVariable("MIZUNOMI_UPLOAD_KEY_PASSWORD").orNull
val testSigningValues = listOf(
    testKeystoreFile,
    testKeystorePassword,
    testKeyAlias,
    testKeyPassword,
)
val hasAnyTestSigningValue = testSigningValues.any { !it.isNullOrBlank() }
val hasCompleteTestSigningConfig = testSigningValues.all { !it.isNullOrBlank() }

check(!hasAnyTestSigningValue || hasCompleteTestSigningConfig) {
    "Test APK signing requires all MIZUNOMI_UPLOAD_KEYSTORE_* environment variables."
}

val requestedVersionCode = providers.environmentVariable("MIZUNOMI_VERSION_CODE").orNull
val resolvedVersionCode = requestedVersionCode?.toIntOrNull()?.takeIf { it > 0 } ?: 1

check(requestedVersionCode == null || resolvedVersionCode.toString() == requestedVersionCode) {
    "MIZUNOMI_VERSION_CODE must be a positive integer."
}

android {
    namespace = "com.tekutekunikki.mizunomi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tekutekunikki.mizunomi"
        minSdk = 23
        targetSdk = 35
        versionCode = resolvedVersionCode
        versionName = "1.0"
    }

    signingConfigs {
        if (hasCompleteTestSigningConfig) {
            create("testDistribution") {
                storeFile = file(testKeystoreFile!!)
                storePassword = testKeystorePassword
                keyAlias = testKeyAlias
                keyPassword = testKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (hasCompleteTestSigningConfig) {
                signingConfig = signingConfigs.getByName("testDistribution")
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
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
