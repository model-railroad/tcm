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
import java.nio.file.Path
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.pathString

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.kapt)
    alias(libs.plugins.javacpp.platform)
}

android {
    namespace = "com.alflabs.tcm"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.alflabs.tcm"
        minSdk = 23                 // Android 6, aka M.
        targetSdk = 34              // Android 14, aka U.
        versionCode = 1023
        versionName = "0.0.2"

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

        // Flag to enable support for the new language APIs
        // (namely: to have java.time.LocalDateTime available on API < 26.
        // See https://developer.android.com/studio/write/java8-support#library-desugaring
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    packaging {
        jniLibs {
            // Sets AndroidManifest <application extractNativeLibs=true> to allow JNI libs to be
            // compressed and not zip-aligned.
            useLegacyPackaging = true
        }
        resources {
            // Remove all the JavaCV lib/ which are being considered "resources" and being packaged
            // in the final APK. Instead JniLibs are packaged via the "javacpp" configuration below.
            excludes.add("**/lib/**")
            // This line prevents some JavaCV native-image/* files from being included which create errors.
            excludes.add("META-INF/native-image/**/*.json")
        }
    }
}

// Source: https://github.com/bytedeco/sample-projects/blob/master/JavaCV-android-example/app/build.gradle
// and manually converted from Groovy Gradle to kts.
val javacpp: Configuration by configurations.creating

tasks.register<Copy>("javacppExtract") {
    dependsOn(configurations["javacpp"])

    doFirst {
        val names = configurations["javacpp"]
            .map { it.name.replace("^([^-]+).*$".toRegex(), "$1") }
            .distinct()
            .sorted()
        val abis  = configurations["javacpp"]
            .asSequence()
            .map { it.name }
            .filter { it.contains("android") }
            .map { it.replace("^.*(android[^.]+).*$".toRegex(), "$1") }
            .distinct()
            .sorted()
            .toList()
        println("@@ javacppExtract: ${names.size} input files $names x $abis")
    }

    from(configurations["javacpp"].map { zipTree(it) })
    include("lib/**/*.so")
    into("${layout.buildDirectory.get()}/javacpp/")

    doLast {
        var numFiles = 0
        fileTree("${layout.buildDirectory.get()}/javacpp/lib/").visit(object : FileVisitor {
            override fun visitDir(dirDetails: FileVisitDetails) { }
            override fun visitFile(fileDetails: FileVisitDetails) {
                numFiles++
            }
        })
        println("@@ javacppExtract: $numFiles output files in ${layout.buildDirectory.get()}/javacpp/lib/")
    }
}

android {
    val keystorePath = findCustomDebugKeystorePath()
    if (!keystorePath.isNullOrEmpty()) {
        signingConfigs {
            getByName("debug") {
                storeFile = file(keystorePath)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }
    sourceSets["main"].jniLibs.srcDir("${layout.buildDirectory.get()}/javacpp/lib/")
    project.tasks.preBuild.dependsOn("javacppExtract")

    println("@@ TYPE = ${sourceSets["main"].jniLibs::class.simpleName}")
    println("@@ JniLibs Dirs = ${sourceSets["main"].jniLibs.srcDirs}")
}

dependencies {

    // --- JavaCV + FFMPEG + OpenCV dependencies
    // Note: JavaCV-platform does NOT provide any NDK JNI for Android directly.
    // IIRC, they are in the JARs, with "-platform" filtered using the "javacppPlatform" (set
    // in the main build.gradle.kts) and used by the JavaCPP Gradle Plugin, then extracted using
    // that javacppExtract rule above and then imported by the javacpp configuration below.
    // Something like that. It's quite convoluted.
    // Bottom line:
    // 1- See comments in gradle/libs.versions.toml
    // 2- To find what JNI lib/*.so gets added: first build, and then look into
    //   app/build/tmp/.cache/expanded/
    //   app/build/javacpp/lib/{armeabi-v7a,arm64-v8a,x86,x86_64}/

    // This provides the java imports available.
    // Note: the JAR /lib gets packaged in the APK /lib folder as resources, and to avoid that
    // we filter them out above via packaging.jniLibs.resources.excludes. That's because these
    // JAR /lib contains way more than the required .so, they also contain source code and execs.
    implementation(libs.javacv.platform)
    implementation(libs.ffmpeg.platform)

    // This filters the JAR artifacts to unzip the lib/*.so and provide them as JNI libs.
    javacpp(libs.javacv.platform)
    javacpp(libs.ffmpeg.platform)

    // TBD add OpenCV in both rules above once we actually use it.

    // --- All other dependencies

    coreLibraryDesugaring(libs.android.tool.desugar.jdk.libs) // For isCoreLibraryDesugaringEnabled

    implementation(libs.dagger.dagger)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.material)
    implementation(libs.squareup.okhttp3)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    // implementation(libs.kotlinx.coroutines) -- not currently used
    implementation(kotlin("reflect"))

    kapt(libs.dagger.compiler)

    // "Where we're going, we don't need tests" (at least for now)
    // testImplementation(libs.junit)
    // androidTestImplementation(libs.androidx.junit)
    // androidTestImplementation(libs.androidx.espresso.core)
}

fun findCustomDebugKeystorePath(): String? {
    // Finds a custom debug key matching the specific pattern.
    // Adjust for your OS/path as needed.
    // Returns null if not found, in which case the default SDK debug key will be used.
    var keystorePath: String? = null
    arrayOf("HOME", "USERPROFILE").forEach { envKey ->
        val envDir = System.getenv(envKey)
        if (keystorePath == null && envDir != null) {
            try {
                val evnPath = Path.of(envDir)
                evnPath.forEachDirectoryEntry("r*-debug-*.keystore") { path ->
                    if (keystorePath == null) {
                        keystorePath = path.pathString
                        println("@@ Custom Debug Key: $keystorePath")
                    }
                }
            } catch (_: Exception) {
                // Ignore all invalid access, etc
            }
        }
    }
    if (keystorePath == null) {
        println("@@ Custom Debug Key not found, using default.")
    }
    return keystorePath
}
