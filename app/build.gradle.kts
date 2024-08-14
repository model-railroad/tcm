/*
 * Project: TCM
 * Copyright (C) 2024 alf.labs gmail com,
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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

    buildFeatures {
        buildConfig = true
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
    packaging {
        jniLibs  .excludes.add("META-INF/native-image/**/*.json")
        resources.excludes.add("META-INF/native-image/**/*.json")
    }

    // This generates per-abi APKs if both android-arm and android-x86 libs are provided.
    // splits {
    //     abi {
    //         isEnable = true
    //         reset()
    //         include("x86", "armeabi")
    //         isUniversalApk = false
    //     }
    // }

}

// Source: https://github.com/bytedeco/sample-projects/blob/master/JavaCV-android-example/app/build.gradle
// and manually converted from Groovy Gradle to kts.
val javacpp: Configuration by configurations.creating

tasks.register<Copy>("javacppExtract") {
    dependsOn(configurations["javacpp"])

    doFirst {
        println("@@ javacppExtract: input ${inputs.files.files.size} files")
        // DEBUG -- this prints the files parsed by this rule when looking for lib/** below
        // println("@@ javacppExtract:\n -${inputs.files.files.joinToString("\n- ") { f -> f.path.toString() }}")
    }

    from(configurations["javacpp"].map { zipTree(it) })
    include("lib/**")
    into("${layout.buildDirectory.get()}/javacpp/")

    doLast {
         val f = configurations["javacpp"].map { it.path.toString() }
         println("@@ javacppExtract: output ${f.size} files")
    }
}

android {
    sourceSets["main"].jniLibs.srcDir("${layout.buildDirectory.get()}/javacpp/lib/")
    project.tasks.preBuild.dependsOn("javacppExtract")
}


dependencies {
    // JavaCV - OpenCV stuff
    // Note: JavaCV-platform does NOT provide any NDK JNI for Android directly.
    // IIRC, they are in the JARs, with "-platform" filtered using the "javacppPlatform" (set
    // in the main build.gradle.kts) and used by the JavaCPP Gradle Plugin, then extracted using
    // that javacppExtract rule above and then imported using the fileTree(libs) rule.
    // Something like that. It's quite convoluted.
    // Bottom line:
    // 1- See comments in gradle/libs.versions.toml
    // 2- To find what JNI jar/so gets added, build, and then look into
    //   app/build/tmp/.cache/expanded/
    //   app/build/javacpp/lib/{armeabi-v7a,arm64-v8a,x86,x86_64}/
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
