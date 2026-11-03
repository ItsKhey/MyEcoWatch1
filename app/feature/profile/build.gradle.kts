plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.myecowatch1.feature.profile"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":app:core"))
    // The security screen reuses the forgot-password flow owned by auth.
    implementation(project(":app:feature:auth"))

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity.ktx)
    implementation(libs.recyclerview)
    implementation(libs.swiperefreshlayout)
    implementation(libs.glide)
    implementation(libs.ucrop)
    implementation(libs.biometric)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
}
