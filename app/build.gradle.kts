plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "ir.takweb.terminalapp"; compileSdk = 35
 defaultConfig { applicationId = "ir.takweb.terminalapp"; minSdk = 24; targetSdk = 35; versionCode = 4; versionName = "0.2.2" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
