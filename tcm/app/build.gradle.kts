import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.javacpp.platform)
}

android {
    namespace = "com.alflabs.tcm"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.alflabs.tcm"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    // This line prevents some JavaCV native-image/* files from being included which create errors.
    packagingOptions {
        exclude("META-INF/native-image/**/*.json")
    }

    // This generates per-abi APKs if both android-arm and android-x86 libs are provided.
//    splits {
//        abi {
//            isEnable = true
//            reset()
//            include("x86", "armeabi")
//            isUniversalApk = false
//        }
//    }

}

// Source: https://github.com/bytedeco/sample-projects/blob/master/JavaCV-android-example/app/build.gradle
// and manually converted from Groovy Gradle to kts.
val javacpp: Configuration by configurations.creating

tasks.register<Copy>("javacppExtract") {
    dependsOn(configurations["javacpp"])

    doFirst {
        println("@@ javacppExtract: inputs = ${inputs.files.map { it.path.toString() }}")
        println("@@ javacppExtract: outputs = ${outputs.files.map { it.path.toString() }}")
        println("@@ javacppExtract: buildDir 1 = $buildDir")
        println("@@ javacppExtract: buildDir 2 = ${layout.buildDirectory.get()}")
    }

    from(configurations["javacpp"].map {
        println("@@ javacppExtract: map = ${it.path}")
        zipTree(it).also { ft ->
            println("@@ javacppExtract:    zipTree output = ${ft.files.map { f -> f.path.toString() }}")
        }
    })
    include("lib/**")
    into("$buildDir/javacpp/")

    doLast {
        val f = configurations["javacpp"].map { it.path.toString() }
        println("@@ javacppExtract: files = $f")
    }
}

android {
    sourceSets["main"].jniLibs.srcDir("$buildDir/javacpp/lib/")
    project.tasks.preBuild.dependsOn("javacppExtract")
}


dependencies {
    // JavaCV - OpenCV stuff
    // Note: JavaCV-platform does NOT provide any NDK JNI for Android directly.
    // IIRC, they are in the JARs, with "-platform" filtered using the "javacppPlatform" (set
    // in the main build.gradlekts) and used by the JavaCPP Gradle Plugin, then extracted using that
    // javacppExtract rule above and then imported using the fileTree(libs) rule.
    // Something like that. It's quite convoluted.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    // This provides the java imports available.
    implementation(libs.javacv.platform)
    implementation(libs.ffmpeg.platform)
    implementation(libs.opencv.platform)
    // This provides the JNI libs needed at runtime.
    // For some reason only one "platform" needs to be specified below.
    // (e.g. trying to hava javacpp of javacv + ffmpeg makes it fail).
    javacpp(libs.opencv.platform)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.preference.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
