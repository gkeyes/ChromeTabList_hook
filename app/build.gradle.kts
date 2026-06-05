 plugins {
     alias(libs.plugins.android.application)
 }

 android {
     namespace = "com.operit.chrometablist"
     compileSdk = 34
     val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
     val releaseStorePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: ""
     val releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "chrometablist"
     val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: releaseStorePassword

     defaultConfig {
         applicationId = "com.operit.chrometablist"
         minSdk = 27
         targetSdk = 34
         versionCode = 5
         versionName = "1.0.4"
     }

     signingConfigs {
         create("release") {
             if (!releaseKeystorePath.isNullOrBlank()) {
                 storeFile = file(releaseKeystorePath)
             }
             storeType = "pkcs12"
             storePassword = releaseStorePassword
             keyAlias = releaseKeyAlias
             keyPassword = releaseKeyPassword
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
             signingConfig = signingConfigs.getByName("release")
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

 // Force use of ARM64 binaries for AAPT2 in Proot environment
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
