package staff.android.builds.transfer.exercise

import staff.android.builds.transfer.CriticalPathPlan
import staff.android.builds.transfer.Job
import staff.android.builds.transfer.JobTiming
import staff.android.builds.transfer.validateJobs

// Deliberately serializes independent branches. Repair this implementation.
fun planCriticalPath(jobs: List<Job>, roots: Set<String>): CriticalPathPlan {
    val byId = validateJobs(jobs, roots)
    val visiting = mutableSetOf<String>()
    val visited = mutableSetOf<String>()
    val order = mutableListOf<String>()
    fun visit(id: String) {
        if (id in visited) return
        require(visiting.add(id)) { "Cycle in selected jobs: $id" }
        for (dependency in byId.getValue(id).dependencies.sorted()) {
            visit(dependency)
        }
        visiting.remove(id)
        visited += id
        order += id
    }
    roots.sorted().forEach(::visit)
    var cursor = 0L
    val timings = mutableMapOf<String, JobTiming>()
    for (id in order) {
        val finish = Math.addExact(cursor, byId.getValue(id).estimatedMillis)
        timings[id] = JobTiming(cursor, finish)
        cursor = finish
    }
    return CriticalPathPlan(timings.toMap(), order.toList(), cursor)
}
