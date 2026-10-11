// Read-only graph inspection, deliberately separate from timed/cache runs.
// Invoke with --no-configuration-cache and --dry-run.
gradle.taskGraph.whenReady {
    val selected = allTasks.toSet()
    val rows = allTasks.sortedBy { it.path }.map { task ->
        val dependencies = task.taskDependencies.getDependencies(task)
            .filter { it in selected }.map { it.path }.sorted()
        task.path + "\t" + dependencies.joinToString(",")
    }
    val destination = System.getProperty("chapter18.graphFile")
        ?: error("Pass -Dchapter18.graphFile=/absolute/output.tsv")
    java.io.File(destination).writeText(rows.joinToString("\n") + "\n")
}
