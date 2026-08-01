@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.lsplugin.apksign)
    alias(libs.plugins.aboutLibraries)
    id("kotlin-parcelize")
}

val androidCompileSdkVersion: Int by rootProject.extra
val androidCompileNdkVersion: String by rootProject.extra
val androidBuildToolsVersion: String by rootProject.extra
val androidMinSdkVersion: Int by rootProject.extra
val androidTargetSdkVersion: Int by rootProject.extra
val androidSourceCompatibility: JavaVersion by rootProject.extra
val androidTargetCompatibility: JavaVersion by rootProject.extra
val managerVersionCode: Int by rootProject.extra
val managerVersionName: String by rootProject.extra

val managerSignHeader = providers.fileContents(
    layout.projectDirectory.file("../../kernel/manager/manager_sign.h")
).asText.get()

fun readSingleManagerSignMacro(pattern: Regex, macroName: String): String {
    val matches = pattern.findAll(managerSignHeader).toList()
    check(matches.size == 1) {
        "Expected exactly one $macroName definition in kernel/manager/manager_sign.h, found ${matches.size}"
    }
    return matches.single().groupValues[1]
}

fun parseSignatureSize(value: String, sourceName: String): Int {
    val parsed = if (value.startsWith("0x", ignoreCase = true)) {
        value.substring(2).toIntOrNull(16)
    } else {
        value.toIntOrNull()
    }
    return requireNotNull(parsed) { "Invalid signature size for $sourceName: $value" }
}

val officialSakiSuSignatureSize = parseSignatureSize(
    readSingleManagerSignMacro(
        Regex("""(?m)^\s*#define\s+EXPECTED_SIZE_SAKISU\s+(0[xX][0-9A-Fa-f]+|[0-9]+)\s*$"""),
        "EXPECTED_SIZE_SAKISU",
    ),
    "EXPECTED_SIZE_SAKISU",
)
val officialSakiSuSignatureHash = readSingleManagerSignMacro(
    Regex("""(?m)^\s*#define\s+EXPECTED_HASH_SAKISU\s+"([0-9A-Fa-f]{64})"\s*$"""),
    "EXPECTED_HASH_SAKISU",
).lowercase()

apksign {
    storeFileProperty = "KEYSTORE_FILE"
    storePasswordProperty = "KEYSTORE_PASSWORD"
    keyAliasProperty = "KEY_ALIAS"
    keyPasswordProperty = "KEY_PASSWORD"
}

val baseCFlags = listOf(
    "-Wall", "-Qunused-arguments", "-fvisibility=hidden", "-fvisibility-inlines-hidden",
    "-fno-exceptions", "-fno-stack-protector", "-fomit-frame-pointer",
    "-Wno-builtin-macro-redefined", "-Wno-unused-value", "-D__FILE__=__FILE_NAME__"
)
val baseCppFlags = baseCFlags + "-fno-rtti"

val isReleaseTask =
    project.gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }

android {
    namespace = "com.sakisu.sakisu"

    buildTypes {
        debug {
            externalNativeBuild {
                cmake {
                    arguments += listOf("-DCMAKE_CXX_FLAGS_DEBUG=-Og", "-DCMAKE_C_FLAGS_DEBUG=-Og")
                }
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            externalNativeBuild {
                cmake {
                    arguments += "-DDEBUG_SYMBOLS_PATH=${layout.buildDirectory.get().asFile.absolutePath}/symbols"
                    arguments += "-DCMAKE_BUILD_TYPE=Release"

                    val releaseFlags = listOf(
                        "-flto", "-ffunction-sections", "-fdata-sections", "-Wl,--gc-sections",
                        "-fno-unwind-tables", "-fno-asynchronous-unwind-tables", "-Wl,--exclude-libs,ALL"
                    )
                    val configFlags = listOf("-Oz", "-DNDEBUG").joinToString(" ")

                    cppFlags += releaseFlags
                    cFlags += releaseFlags

                    arguments += listOf(
                        "-DCMAKE_CXX_FLAGS_RELEASE=$configFlags",
                        "-DCMAKE_C_FLAGS_RELEASE=$configFlags",
                        "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--gc-sections -Wl,--exclude-libs,ALL -Wl,--icf=all -s -Wl,--hash-style=sysv -Wl,-z,norelro"
                    )
                }
            }
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
        compose = true
        prefab = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            // https://stackoverflow.com/a/58956288
            // It will break Layout Inspector, but it's unused for release build.
            excludes += "META-INF/*.version"
            // https://github.com/Kotlin/kotlinx.coroutines?tab=readme-ov-file#avoiding-including-the-debug-infrastructure-in-the-resulting-apk
            excludes += "DebugProbesKt.bin"
            // https://issueantenna.com/repo/kotlin/kotlinx.coroutines/issues/3158
            excludes += "kotlin-tooling-metadata.json"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    androidResources {
        generateLocaleConfig = true
    }

    compileSdk = androidCompileSdkVersion
    ndkVersion = androidCompileNdkVersion
    buildToolsVersion = androidBuildToolsVersion

    defaultConfig {
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion
        versionCode = managerVersionCode
        versionName = managerVersionName

        val isPrBuildValue = providers.gradleProperty("IS_PR_BUILD").orNull
        val isPrBuild = isPrBuildValue?.toBooleanStrictOrNull() ?: false
        check(isPrBuildValue == null || isPrBuildValue.toBooleanStrictOrNull() != null) {
            "IS_PR_BUILD must be either true or false"
        }
        buildConfigField("boolean", "IS_PR_BUILD", isPrBuild.toString())

        val expectedPrBuildSize = providers.environmentVariable("KSU_EXPECTED_PR_BUILD_SIZE")
            .orNull
            .orEmpty()
        val expectedPrBuildHash = providers.environmentVariable("KSU_EXPECTED_PR_BUILD_HASH")
            .orNull
            .orEmpty()
            .lowercase()
        check(expectedPrBuildSize.isBlank() == expectedPrBuildHash.isBlank()) {
            "KSU_EXPECTED_PR_BUILD_SIZE and KSU_EXPECTED_PR_BUILD_HASH must be provided together"
        }
        if (isPrBuild) {
            check(expectedPrBuildSize.matches(Regex("""0[xX][0-9A-Fa-f]+|[0-9]+"""))) {
                "IS_PR_BUILD requires a valid KSU_EXPECTED_PR_BUILD_SIZE"
            }
            check(parseSignatureSize(expectedPrBuildSize, "KSU_EXPECTED_PR_BUILD_SIZE") > 0) {
                "KSU_EXPECTED_PR_BUILD_SIZE must be positive and fit in an Int"
            }
            check(expectedPrBuildHash.matches(Regex("""[0-9a-f]{64}"""))) {
                "IS_PR_BUILD requires a valid KSU_EXPECTED_PR_BUILD_HASH"
            }
        } else {
            check(expectedPrBuildSize.isBlank() && expectedPrBuildHash.isBlank()) {
                "PR signature metadata must be empty unless IS_PR_BUILD=true"
            }
        }
        buildConfigField(
            "int",
            "OFFICIAL_SAKISU_SIGNATURE_SIZE",
            officialSakiSuSignatureSize.toString(),
        )
        buildConfigField(
            "String",
            "OFFICIAL_SAKISU_SIGNATURE_HASH",
            "\"$officialSakiSuSignatureHash\"",
        )
        buildConfigField("String", "EXPECTED_PR_BUILD_SIZE", "\"$expectedPrBuildSize\"")
        buildConfigField("String", "EXPECTED_PR_BUILD_HASH", "\"$expectedPrBuildHash\"")

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=none"
                cFlags += baseCFlags + "-std=c2x"
                cppFlags += baseCppFlags + "-std=c++2b"
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64", "armeabi-v7a")
        }
    }

    splits {
        abi {
            isEnable = isReleaseTask
            reset()
            include("arm64-v8a", "x86_64", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = androidSourceCompatibility
        targetCompatibility = androidTargetCompatibility
    }
}

base {
    archivesName.set(
        "SakiSU_${managerVersionName}_${managerVersionCode}"
    )
}

configurations.all {
    exclude(group = "androidx.navigationevent", module = "navigationevent-compose")
}

aboutLibraries {
    library {
        // Enable the duplication mode, allows to merge, or link dependencies which relate
        duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
        // Configure the duplication rule, to match "duplicates" with
        duplicationRule = com.mikepenz.aboutlibraries.plugin.DuplicateRule.SIMPLE
    }
}

dependencies {
    implementation(libs.gson)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.foundation)

    implementation(libs.androidx.compose.runtime.tracing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.navigation)
    implementation(libs.androidx.navigationevent) {
        exclude(group = "androidx.navigation", module = "navigationevent-compose")
    }

    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.m3)

    implementation(libs.com.github.topjohnwu.libsu.core)
    implementation(libs.com.github.topjohnwu.libsu.service)
    implementation(libs.com.github.topjohnwu.libsu.io)

    implementation(libs.material.kolor)
    implementation(libs.monet.compat)
    implementation(libs.material.components)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.capsule)

    implementation(libs.dev.rikka.rikkax.parcelablelist)

    implementation(libs.io.coil.kt.coil.compose)
    implementation(libs.ucrop)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.me.zhanghai.android.appiconloader.coil)

    implementation(libs.markdown)
    implementation(libs.androidx.webkit)

    implementation(libs.lsposed.cxx)

    implementation(libs.com.github.topjohnwu.libsu.core)

    implementation(libs.accompanist.drawablepainter)

}
