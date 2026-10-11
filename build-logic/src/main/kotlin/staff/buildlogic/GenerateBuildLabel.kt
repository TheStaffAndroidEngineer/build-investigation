package staff.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateBuildLabel : DefaultTask() {
    @get:Input
    abstract val label: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val value = label.get()
        require(value.isNotBlank())
        val directory = outputDirectory.get().asFile
        directory.mkdirs()
        directory.resolve("build-info.txt").writeText(value + "\n")
    }
}
