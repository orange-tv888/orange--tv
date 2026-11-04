import java.util.Properties

// A one-off tool for TVs whose firmware keeps taking Home back from Lite. It lists every Home
// candidate, says whether `su` works, and can disable a candidate with that. Plain Java on purpose:
// Android 4.4 is the target and nothing here should need desugaring or Kotlin's runtime.
plugins {
    id("com.android.application")
}

android {
    namespace = "app.orange.tv.checkup"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.orange.tv.checkup"
        // Android 4.0, the same floor as Lite.
        minSdk = 14
        // Old target on purpose: the newest behaviours are what break 4.x.
        targetSdk = 19
        versionCode = 1
        versionName = "1.0"
    }

    // The same key as Film and Lite, so it can be installed on a TV that already has them.
    val signingProps = rootProject.file("local/signing.properties")
    signingConfigs {
        if (signingProps.exists()) {
            val p = Properties().apply { signingProps.inputStream().use { load(it) } }
            create("aerial") {
                storeFile = rootProject.file("local/" + p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("aerial") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    lint {
        checkReleaseBuilds = false
        disable += setOf("OldTargetApi", "ExpiredTargetSdkVersion")
    }
}
