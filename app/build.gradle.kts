 plugins {
     alias(libs.plugins.android.application)
 }

 android {
     namespace = "com.operit.chrometablist"
     compileSdk = 34

     defaultConfig {
         applicationId = "com.operit.chrometablist"
         minSdk = 27
         targetSdk = 34
         versionCode = 1
         versionName = "1.0.0"
     }

     buildTypes {
         debug {
             isMinifyEnabled = false
         }
         release {
             isMinifyEnabled = false
         }
     }
     compileOptions {
         sourceCompatibility = JavaVersion.VERSION_11
         targetCompatibility = JavaVersion.VERSION_11
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
