package staff.android.builds.transfer.solution

import staff.android.builds.transfer.CriticalPathPlan
import staff.android.builds.transfer.Job
import staff.android.builds.transfer.JobTiming
import staff.android.builds.transfer.validateJobs

private fun before(left: List<String>, right: List<String>): Boolean {
    for (index in 0 until minOf(left.size, right.size)) {
        val comparison = left[index].compareTo(right[index])
        if (comparison != 0) return comparison < 0
    }
    return left.size < right.size
}

fun planCriticalPath(jobs: List<Job>, roots: Set<String>): CriticalPathPlan {
    val byId = validateJobs(jobs, roots)
    val visiting = mutableSetOf<String>()
    val timings = mutableMapOf<String, JobTiming>()
    val paths = mutableMapOf<String, List<String>>()

    fun visit(id: String) {
        if (id in timings) return
        require(visiting.add(id)) { "Cycle in selected jobs: $id" }
        val job = byId.getValue(id)
        var start = 0L
        var path = listOf(id)
        var hasPredecessor = false
        for (dependency in job.dependencies.sorted()) {
            visit(dependency)
            val finish = timings.getValue(dependency).finishMillis
            val candidate = paths.getValue(dependency) + id
            if (!hasPredecessor || finish > start ||
                (finish == start && before(candidate, path))) {
                start = finish
                path = candidate
                hasPredecessor = true
            }
        }
        val finish = Math.addExact(start, job.estimatedMillis)
        timings[id] = JobTiming(start, finish)
        paths[id] = path
        visiting.remove(id)
    }

    var longest = 0L
    var winner = emptyList<String>()
    for (root in roots.sorted()) {
        visit(root)
        val finish = timings.getValue(root).finishMillis
        val candidate = paths.getValue(root)
        if (winner.isEmpty() || finish > longest ||
            (finish == longest && before(candidate, winner))) {
            longest = finish
            winner = candidate
        }
    }
    return CriticalPathPlan(timings.toMap(), winner.toList(), longest)
}
