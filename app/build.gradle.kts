plugins {
    alias(libs.plugins.android.application)
}

// Release signing comes from environment variables (set by CI from repository
// secrets). When no keystore is provided, e.g. on pull request builds, the
// release APK is built unsigned instead of failing.
val releaseKeystorePath: String? = System.getenv("RELEASE_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: ""
val releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "chrometablist"
val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: releaseStorePassword
val hasReleaseKeystore = !releaseKeystorePath.isNullOrBlank() &&
    file(releaseKeystorePath!!).let { it.isFile && it.length() > 0 }

android {
    namespace = "com.operit.chrometablist"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.operit.chrometablist"
        minSdk = 27
        targetSdk = 34
        versionCode = 7
        versionName = "1.0.6"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storeType = "pkcs12"
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources {
            excludes += setOf("kotlin/**", "META-INF/*.kotlin_module")
        }
    }
}

// Local-build workaround: use the linux-aarch64 AAPT2 binary when building
// inside a Proot/Termux environment on ARM64.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.android.tools.build" && requested.name == "aapt2") {
            useTarget("com.android.tools.build:aapt2:${'$'}{requested.version}:linux-aarch64")
        }
    }
}

dependencies {
    compileOnly(libs.xposed.api)
}
