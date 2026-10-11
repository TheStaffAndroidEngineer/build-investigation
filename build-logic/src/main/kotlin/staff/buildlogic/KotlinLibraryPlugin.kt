package staff.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class KotlinLibraryPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        pluginManager.apply("java-library")
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure(KotlinJvmProjectExtension::class.java) { extension ->
            extension.jvmToolchain(27)
            extension.compilerOptions.jvmTarget.set(JvmTarget.JVM_26)
        }
        extensions.configure(JavaPluginExtension::class.java) { extension ->
            extension.sourceCompatibility = JavaVersion.VERSION_26
            extension.targetCompatibility = JavaVersion.VERSION_26
        }
        tasks.withType(JavaCompile::class.java).configureEach { task ->
            task.options.release.set(26)
        }
        tasks.withType(Test::class.java).configureEach { task ->
            task.useJUnitPlatform()
        }
    }
}
