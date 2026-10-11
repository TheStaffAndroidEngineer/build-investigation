package staff.android.builds.transfer

data class Job(
    val id: String,
    val dependencies: Set<String>,
    val estimatedMillis: Long,
)

data class JobTiming(
    val startMillis: Long,
    val finishMillis: Long,
)

data class CriticalPathPlan(
    val timings: Map<String, JobTiming>,
    val path: List<String>,
    val lowerBoundMillis: Long,
)

fun validateJobs(jobs: List<Job>, roots: Set<String>): Map<String, Job> {
    require(jobs.size <= 100) { "At most 100 jobs" }
    require(jobs.all { it.id.isNotBlank() }) { "Blank job ID" }
    val byId = jobs.associateBy { it.id }
    require(byId.size == jobs.size) { "Duplicate job ID" }
    require(jobs.all { it.estimatedMillis >= 0 }) { "Negative duration" }
    require(jobs.all { job -> job.dependencies.all { it in byId } }) {
        "Unknown dependency"
    }
    require(roots.all { it in byId }) { "Unknown requested root" }
    return byId
}
