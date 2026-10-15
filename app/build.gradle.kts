import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
android {
    namespace = "it.nfctimetracker"
    compileSdk = 35
    defaultConfig {
        applicationId = "it.nfctimetracker"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
        resValue("string", "app_name", "NFC Time Tracker $versionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.withType<Test>().configureEach {
    // Isolate Robolectric/Compose application and dispatcher state between test classes.
    forkEvery = 1
    val testHome = layout.buildDirectory.dir("test-home").get().asFile
    systemProperty("user.home", testHome.absolutePath)
    doFirst { testHome.mkdirs() }
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    System.getProperty("javax.net.ssl.trustStore")?.let { systemProperty("javax.net.ssl.trustStore", it) }
    // The JVM does not automatically honor HTTPS_PROXY in cloud environments.
    System.getenv("HTTPS_PROXY")?.takeIf { it.isNotBlank() }?.let { proxy ->
        val uri = URI(proxy)
        if (uri.host != null) {
            systemProperty("https.proxyHost", uri.host)
            systemProperty("https.proxyPort", if (uri.port > 0) uri.port else 80)
        }
    }
}
