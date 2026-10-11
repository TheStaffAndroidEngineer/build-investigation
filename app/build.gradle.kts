import staff.buildlogic.GenerateBuildLabel

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    id("staff.build-tools")
}
android {
    namespace = "staff.android.builds"
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    buildToolsVersion = "37.0.0"
    defaultConfig {
        minSdk = 26
        applicationId = "staff.android.builds"
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_26; targetCompatibility = JavaVersion.VERSION_26 }
}
kotlin { jvmToolchain(27); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26) } }
androidComponents {
    onVariants(selector().all()) { variant ->
        val capitalized = variant.name.replaceFirstChar { it.uppercase() }
        val generated = tasks.register<GenerateBuildLabel>(
            "generate${capitalized}BuildLabel"
        ) {
            label.set(providers.gradleProperty("buildLabel").orElse("Local build"))
            outputDirectory.set(layout.buildDirectory.dir(
                "generated/build-label/${variant.name}"
            ))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(
            generated, GenerateBuildLabel::outputDirectory
        )
    }
}
dependencies {
    implementation(libs.hiltAndroid)
    ksp(libs.hiltCompiler)
    implementation(libs.androidxActivityCompose)
    implementation(libs.androidxLifecycleViewModel)
    implementation(libs.androidxLifecycleRuntimeCompose)
    implementation(libs.coroutinesAndroid)
    implementation(platform(libs.composeBom))
    implementation(libs.composeUi)
    implementation(libs.composeFoundation)
    implementation(libs.composeMaterial3)
    implementation(project(":cart-api"))
    implementation(project(":cart-impl"))
    implementation(project(":feature-cart"))
    implementation(project(":feature-saved"))
    debugImplementation(libs.composeTestManifest)
    androidTestImplementation(platform(libs.composeBom))
    androidTestImplementation(libs.composeTestJunit4)
    androidTestImplementation(libs.androidxTestJunit)
    androidTestImplementation(libs.androidxTestRunner)
}
