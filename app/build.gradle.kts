plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.facultyattendance"
    compileSdk = 37            // required by androidx.core 1.19.x; install "Android 17 (API 37)" in the SDK Manager

    defaultConfig {
        applicationId = "com.example.facultyattendance"
        minSdk = 26
        targetSdk = 36         // behaviour changes of API 37 have not been tested, so we do not opt in yet
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8 shrinking is left OFF because a release build could not be tested in the authoring
            // environment. Turn it on, then test the release APK, before distributing.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // JDK 21 runs Gradle; the Java language level is 17 (supported by AGP 9.1 and D8 desugaring).
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Room schema export (used for migration tests). Plain javac argument, so it works with any AGP DSL.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Aroom.schemaLocation=${projectDir}/schemas")
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.material)

    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.mlkit.face.detection)
    implementation(libs.litert)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
}
