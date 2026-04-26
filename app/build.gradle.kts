plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

fun String.toBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val appId = providers.gradleProperty("MOASIS_APPLICATION_ID")
    .orElse("com.example.moasis")
    .get()
val appVersionCode = providers.gradleProperty("MOASIS_VERSION_CODE")
    .orElse("1")
    .get()
    .toInt()
val appVersionName = providers.gradleProperty("MOASIS_VERSION_NAME")
    .orElse("1.0")
    .get()
val aiEnabled = providers.gradleProperty("MOASIS_AI_ENABLED")
    .orElse("false")
    .get()
    .toBoolean()
val melangePersonalKey = providers.gradleProperty("MOASIS_MELANGE_PERSONAL_KEY")
    .orElse("")
    .get()
val melangeModelName = providers.gradleProperty("MOASIS_MELANGE_MODEL_NAME")
    .orElse("")
    .get()
val melangeModelVersion = providers.gradleProperty("MOASIS_MELANGE_MODEL_VERSION")
    .orElse("-1")
    .get()
    .toInt()
val melangeModelMode = providers.gradleProperty("MOASIS_MELANGE_MODEL_MODE")
    .orElse("RUN_AUTO")
    .get()
val melangeTarget = providers.gradleProperty("MOASIS_MELANGE_TARGET")
    .orElse("LLAMA_CPP")
    .get()
val melangeQuantType = providers.gradleProperty("MOASIS_MELANGE_QUANT_TYPE")
    .orElse("GGUF_QUANT_Q4_K_M")
    .get()
val melangeApType = providers.gradleProperty("MOASIS_MELANGE_AP_TYPE")
    .orElse("AUTO")
    .get()
val embeddingEnabled = providers.gradleProperty("MOASIS_EMBEDDING_ENABLED")
    .orElse("false")
    .get()
    .toBoolean()
val embeddingPersonalKey = providers.gradleProperty("MOASIS_EMBEDDING_PERSONAL_KEY")
    .orElse(melangePersonalKey)
    .get()
val embeddingModelName = providers.gradleProperty("MOASIS_EMBEDDING_MODEL_NAME")
    .orElse("")
    .get()
val embeddingModelVersion = providers.gradleProperty("MOASIS_EMBEDDING_MODEL_VERSION")
    .orElse("-1")
    .get()
    .toInt()
val embeddingModelMode = providers.gradleProperty("MOASIS_EMBEDDING_MODEL_MODE")
    .orElse("RUN_AUTO")
    .get()
val visionEnabled = providers.gradleProperty("MOASIS_VISION_ENABLED")
    .orElse("false")
    .get()
    .toBoolean()
val visionOnnxAsset = providers.gradleProperty("MOASIS_VISION_ONNX_ASSET")
    .orElse("yoloe_s.onnx")
    .get()
val uploadStoreFile = providers.gradleProperty("MOASIS_UPLOAD_STORE_FILE").orNull
val uploadStorePassword = providers.gradleProperty("MOASIS_UPLOAD_STORE_PASSWORD").orNull
val uploadKeyAlias = providers.gradleProperty("MOASIS_UPLOAD_KEY_ALIAS").orNull
val uploadKeyPassword = providers.gradleProperty("MOASIS_UPLOAD_KEY_PASSWORD").orNull

android {
    namespace = "com.example.moasis"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = appId
        minSdk = 31
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("boolean", "AI_ENABLED", aiEnabled.toString())
        buildConfigField("String", "MELANGE_PERSONAL_KEY", melangePersonalKey.toBuildConfigString())
        buildConfigField("String", "MELANGE_MODEL_NAME", melangeModelName.toBuildConfigString())
        buildConfigField("int", "MELANGE_MODEL_VERSION", melangeModelVersion.toString())
        buildConfigField("String", "MELANGE_MODEL_MODE", melangeModelMode.toBuildConfigString())
        buildConfigField("String", "MELANGE_TARGET", melangeTarget.toBuildConfigString())
        buildConfigField("String", "MELANGE_QUANT_TYPE", melangeQuantType.toBuildConfigString())
        buildConfigField("String", "MELANGE_AP_TYPE", melangeApType.toBuildConfigString())
        buildConfigField("boolean", "EMBEDDING_ENABLED", embeddingEnabled.toString())
        buildConfigField("String", "EMBEDDING_PERSONAL_KEY", embeddingPersonalKey.toBuildConfigString())
        buildConfigField("String", "EMBEDDING_MODEL_NAME", embeddingModelName.toBuildConfigString())
        buildConfigField("int", "EMBEDDING_MODEL_VERSION", embeddingModelVersion.toString())
        buildConfigField("String", "EMBEDDING_MODEL_MODE", embeddingModelMode.toBuildConfigString())
        buildConfigField("boolean", "VISION_ENABLED", visionEnabled.toString())
        buildConfigField("String", "VISION_ONNX_ASSET", visionOnnxAsset.toBuildConfigString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (
            !uploadStoreFile.isNullOrBlank() &&
            !uploadStorePassword.isNullOrBlank() &&
            !uploadKeyAlias.isNullOrBlank() &&
            !uploadKeyPassword.isNullOrBlank()
        ) {
            create("upload") {
                storeFile = file(uploadStoreFile)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("upload")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    // Keep ONNX model files uncompressed in the APK so they can be
    // memory-mapped directly without an extra decompression copy.
    androidResources {
        noCompress += "onnx"
    }
    testOptions {
        // Allow JVM unit tests to hit android.util.Log etc. without throwing.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.exifinterface)
    // Melange SDK transitively bundles ONNX Runtime (ort_runtime).
    // We use its ai.onnxruntime.* API directly for YOLOE — do NOT add
    // a separate onnxruntime-android dependency (causes native lib conflict).
    implementation("com.zeticai.mlange:mlange:1.6.1")
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
