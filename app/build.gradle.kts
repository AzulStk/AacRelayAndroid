plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firma: in GitHub Actions il keystore arriva dai secret (vedi README). Senza, si firma con la
// chiave di debug del runner, che cambia a ogni build: l'aggiornamento sopra una versione
// precedente allora fallisce e va disinstallata prima.
val keystoreFile: String? = System.getenv("AAC_KEYSTORE_FILE")

android {
    namespace = "it.azulsistemi.aacrelay"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.azulsistemi.aacrelay"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0.${versionCode}"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("AAC_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("AAC_KEY_ALIAS")
                keyPassword = System.getenv("AAC_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
