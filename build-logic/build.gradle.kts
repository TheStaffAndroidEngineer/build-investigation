plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
}
kotlin {
    jvmToolchain(27)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26)
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_26
    targetCompatibility = JavaVersion.VERSION_26
}
tasks.withType<JavaCompile>().configureEach { options.release.set(26) }
dependencies { implementation(libs.kotlinGradlePlugin) }
gradlePlugin {
    plugins {
        register("kotlinLibrary") {
            id = "staff.kotlin-library"
            implementationClass = "staff.buildlogic.KotlinLibraryPlugin"
        }
        register("buildTools") {
            id = "staff.build-tools"
            implementationClass = "staff.buildlogic.BuildToolsPlugin"
        }
    }
}
