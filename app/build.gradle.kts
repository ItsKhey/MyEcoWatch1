plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

// The checked-in project builds without Firebase credentials. Production CI or
// a developer workstation supplies app/google-services.json; the plugin is only
// applied when that real configuration file exists.

android {
    namespace = "com.example.myecowatch1"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.myecowatch1"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            if (!file("google-services.json").exists()) {
                throw GradleException(
                    "Release builds require app/google-services.json for Firebase Cloud Messaging."
                )
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Shared data layer and the feature screens hosted by the dashboard.
    implementation(project(":app:core"))
    implementation(project(":app:feature:auth"))
    implementation(project(":app:feature:reports"))
    implementation(project(":app:feature:profile"))
    implementation(project(":app:feature:alerts"))

    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    annotationProcessor(libs.room.compiler)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)

    // Glide
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    implementation(libs.play.services.location)
    implementation(libs.swiperefreshlayout)
    implementation(libs.ucrop)
    implementation(libs.biometric)
    implementation(libs.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-messaging")

    // Email verification and password reset only.
    //
    // The app never signs in with Firebase. It never calls signInWith*, never
    // reads an ID token and never treats a Firebase session as a login, so
    // adding this dependency cannot make Firebase an authentication authority.
    // It is used for exactly one optional call: applyActionCode, which retires a
    // reset code once the backend has already applied the new password.
    implementation("com.google.firebase:firebase-auth")
}
