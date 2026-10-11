package staff.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

// Supplies the typed task to consumers of this included build.
class BuildToolsPlugin : Plugin<Project> {
    override fun apply(project: Project) = Unit
}
