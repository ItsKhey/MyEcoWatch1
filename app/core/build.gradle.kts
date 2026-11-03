plugins {
    alias(libs.plugins.android.library)
}

/**
 * Where the app looks for the EcoWatch API.
 *
 * The target is compiled into the app rather than hardcoded in Java, so the
 * same source tree builds against a local server or a hosted deployment without
 * a code edit. It lives in :app:core because ServerConfig is what reads it.
 *
 * The default is the Android emulator's alias for the host machine's loopback,
 * which is exactly what `npm start` in the backend project serves. A physical
 * phone cannot use 10.0.2.2, so point a device build at this machine's address
 * on the same WiFi instead. The address is resolved in this order, first match
 * wins:
 *
 *   1. the -PecowatchApiBaseUrl flag
 *   2. the ECOWATCH_API_BASE_URL environment variable
 *   3. the emulator default above
 *
 *   gradlew installDebug -PecowatchApiBaseUrl=http://192.168.1.20:5000/api/
 */
val ecowatchApiBaseUrl: String =
    (project.findProperty("ecowatchApiBaseUrl") as String?)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: System.getenv("ECOWATCH_API_BASE_URL")?.trim()?.takeIf { it.isNotEmpty() }
        ?: "http://10.0.2.2:5000/api/"

/** Retrofit requires a base URL with a trailing slash, so normalise it once here. */
val ecowatchApiBaseUrlNormalized: String =
    if (ecowatchApiBaseUrl.endsWith("/")) ecowatchApiBaseUrl else "$ecowatchApiBaseUrl/"

/**
 * The same server without the `/api` path, used to expand the relative image
 * paths the database stores (`/uploads/...`) into something Glide can load.
 */
val ecowatchServerOrigin: String = run {
    val withoutTrailingSlash = ecowatchApiBaseUrlNormalized.trimEnd('/')
    val withoutApiPath =
        if (withoutTrailingSlash.endsWith("/api")) withoutTrailingSlash.removeSuffix("/api")
        else withoutTrailingSlash
    "$withoutApiPath/"
}

/**
 * How a new resident account proves it owns the email address.
 *
 *   OTP        (default) the backend emails a 4-digit code, the resident types
 *              it in, and the account is created. No Firebase involvement at
 *              any point, which is what a local-server setup needs.
 *   EMAIL_LINK the backend stages a Firebase identity and the Firebase SDK
 *              emails a verification link. Kept working as a fallback.
 *
 * The Firebase path is selected per build rather than deleted, so switching back
 * is a build flag and not a code change:
 *
 *   gradlew installDebug -PecowatchRegistrationMethod=EMAIL_LINK
 */
val ecowatchRegistrationMethod: String =
    (project.findProperty("ecowatchRegistrationMethod") as String?)
        ?.trim()
        ?.uppercase()
        ?.takeIf { it.isNotEmpty() }
        ?: "OTP"

if (ecowatchRegistrationMethod !in setOf("OTP", "EMAIL_LINK")) {
    throw GradleException(
        "ecowatchRegistrationMethod must be OTP or EMAIL_LINK, got \"$ecowatchRegistrationMethod\"."
    )
}

android {
    namespace = "com.example.myecowatch1.core"
    compileSdk {
        version = release(37)
    }

    // ServerConfig reads these constants instead of hardcoding a target.
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 24

        buildConfigField("String", "API_BASE_URL", "\"$ecowatchApiBaseUrlNormalized\"")
        buildConfigField("String", "SERVER_ORIGIN", "\"$ecowatchServerOrigin\"")
        buildConfigField("String", "REGISTRATION_METHOD", "\"$ecowatchRegistrationMethod\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)

    // Room
    // AppDatabase extends RoomDatabase, so the type has to be visible to any
    // module that calls a DAO accessor on it.
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    annotationProcessor(libs.room.compiler)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Background token sync
    implementation(libs.work.runtime)

    // FCM delivery and the optional Firebase email action
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-auth")
}
