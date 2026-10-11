plugins { id("com.android.application") }

android {
    namespace = "com.reptrack.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.reptrack.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "RoutineTrack-v1.0.0-${variant.name}.apk"
        }
    }
}

dependencies {
    implementation("androidx.core:core:1.15.0")
    implementation("com.google.android.material:material:1.12.0")
}
