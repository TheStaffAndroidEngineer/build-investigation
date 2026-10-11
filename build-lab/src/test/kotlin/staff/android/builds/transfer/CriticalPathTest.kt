package staff.android.builds.transfer

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

abstract class CriticalPathContract {
    abstract fun plan(jobs: List<Job>, roots: Set<String>): CriticalPathPlan

    @ParameterizedTest(name = "{0}")
    @MethodSource("histories")
    fun producesExactSelectedTimingsAndPath(
        name: String,
        jobs: List<Job>,
        roots: Set<String>,
        expected: CriticalPathPlan,
    ) {
        val actual = assertDoesNotThrow<CriticalPathPlan> { plan(jobs, roots) }
        assertEquals(expected, actual, name)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidInputs")
    fun rejectsMalformedRegistryBeforeSelection(
        name: String, jobs: List<Job>, roots: Set<String>,
    ) {
        assertThrows(IllegalArgumentException::class.java, { plan(jobs, roots) }, name)
    }

    @Test fun rejectsSelectedSelfCycle() {
        assertThrows(IllegalArgumentException::class.java) {
            plan(listOf(job("a", 1, "a")), setOf("a"))
        }
    }

    @Test fun rejectsSelectedLongerCycle() {
        assertThrows(IllegalArgumentException::class.java) {
            plan(listOf(job("a", 1, "b"), job("b", 2, "a")), setOf("b"))
        }
    }

    @Test fun ignoresUnselectedCycleButNotUnknownDeclarations() {
        val jobs = listOf(job("chosen", 7), job("x", 1, "y"), job("y", 2, "x"))
        assertEquals(result(7, listOf("chosen"), "chosen" to timing(0, 7)),
            assertDoesNotThrow<CriticalPathPlan> { plan(jobs, setOf("chosen")) })
    }

    @Test fun rejectsOverflowOnSelectedChain() {
        assertThrows(ArithmeticException::class.java) {
            plan(listOf(job("a", Long.MAX_VALUE), job("b", 1, "a")), setOf("b"))
        }
    }

    @Test fun ignoresOverflowInAnUnselectedChain() {
        val jobs = listOf(job("chosen", 7), job("a", Long.MAX_VALUE), job("b", 1, "a"))
        assertEquals(result(7, listOf("chosen"), "chosen" to timing(0, 7)),
            assertDoesNotThrow<CriticalPathPlan> { plan(jobs, setOf("chosen")) })
    }

    @Test fun acceptsOneHundredJobsWithoutCountingSharedNodesTwice() {
        val jobs = (0 until 100).map { i ->
            job("j%03d".format(i), 1, *if (i == 0) emptyArray()
                else arrayOf("j%03d".format(i - 1)))
        }
        val actual = plan(jobs, setOf("j099", "j050"))
        assertEquals(100, actual.timings.size)
        assertEquals(100L, actual.lowerBoundMillis)
        assertEquals(jobs.map { it.id }, actual.path)
        assertEquals(timing(99, 100), actual.timings.getValue("j099"))
    }

    @Test fun improvingNoncriticalBranchDoesNotChangeDependencyBound() {
        val before = plan(bundle(), setOf("package"))
        val after = plan(bundle().map {
            if (it.id == "thumbnail") it.copy(estimatedMillis = 20) else it
        }, setOf("package"))
        assertEquals(450L, before.lowerBoundMillis)
        assertEquals(450L, after.lowerBoundMillis)
        assertEquals(timing(100, 120), after.timings.getValue("thumbnail"))
        assertEquals(listOf("read", "render", "package"), after.path)
    }

    @Test fun changingCriticalCostCanChangeTheBound() {
        val changed = bundle().map {
            if (it.id == "render") it.copy(estimatedMillis = 180) else it
        }
        val actual = plan(changed, setOf("package"))
        assertEquals(330L, actual.lowerBoundMillis)
        assertEquals(timing(280, 330), actual.timings.getValue("package"))
    }

    @Test fun declarationOrderDoesNotChooseTheTiedPath() {
        val jobs = listOf(job("a", 3), job("b", 3), job("z", 1, "b", "a"))
        val reordered = jobs.reversed().map {
            it.copy(dependencies = it.dependencies.reversed().toSet())
        }
        val expected = result(4, listOf("a", "z"),
            "a" to timing(0, 3), "b" to timing(0, 3), "z" to timing(3, 4))
        assertEquals(expected, plan(jobs, linkedSetOf("z", "b")))
        assertEquals(expected, plan(reordered, linkedSetOf("b", "z")))
    }

    @Test fun preservesExactIdsAndDoesNotMutateCallerCollections() {
        val dependencies = linkedSetOf(" read ")
        val jobs = mutableListOf(job(" read ", 2), Job("done", dependencies, 3))
        val roots = linkedSetOf("done")
        val before = jobs.map { it.copy(dependencies = it.dependencies.toSet()) }
        val actual = plan(jobs, roots)
        assertEquals(result(5, listOf(" read ", "done"),
            " read " to timing(0, 2), "done" to timing(2, 5)), actual)
        assertEquals(before, jobs)
        assertEquals(setOf(" read "), dependencies)
        assertEquals(setOf("done"), roots)
    }

    companion object {
        private fun job(id: String, cost: Long, vararg deps: String) =
            Job(id, deps.toSet(), cost)
        private fun timing(start: Long, finish: Long) = JobTiming(start, finish)
        private fun result(
            bound: Long, path: List<String>, vararg timings: Pair<String, JobTiming>,
        ) = CriticalPathPlan(mapOf(*timings), path, bound)
        private fun bundle() = listOf(
            job("read", 100), job("render", 300, "read"),
            job("thumbnail", 120, "read"), job("package", 50, "render", "thumbnail"),
        )

        @JvmStatic fun histories() = listOf(
            Arguments.of("empty graph", emptyList<Job>(), emptySet<String>(),
                result(0, emptyList())),
            Arguments.of("empty selection still returns no scheduled work",
                listOf(job("a", 9)), emptySet<String>(), result(0, emptyList())),
            Arguments.of("single zero job still supplies a path", listOf(job("a", 0)),
                setOf("a"), result(0, listOf("a"), "a" to timing(0, 0))),
            Arguments.of("serial chain", listOf(job("a", 10), job("b", 20, "a")),
                setOf("b"), result(30, listOf("a", "b"),
                    "a" to timing(0, 10), "b" to timing(10, 30))),
            Arguments.of("report bundle branches overlap", bundle(), setOf("package"),
                result(450, listOf("read", "render", "package"),
                    "read" to timing(0, 100), "render" to timing(100, 400),
                    "thumbnail" to timing(100, 220), "package" to timing(400, 450))),
            Arguments.of("thumbnail-only request", bundle(), setOf("thumbnail"),
                result(220, listOf("read", "thumbnail"),
                    "read" to timing(0, 100), "thumbnail" to timing(100, 220))),
            Arguments.of("independent roots overlap", listOf(job("a", 10), job("b", 20)),
                setOf("a", "b"), result(20, listOf("b"),
                    "a" to timing(0, 10), "b" to timing(0, 20))),
            Arguments.of("equal root durations use complete lexical path",
                listOf(job("b", 3), job("a", 3)), setOf("b", "a"),
                result(3, listOf("a"), "a" to timing(0, 3), "b" to timing(0, 3))),
            Arguments.of("equal predecessors use complete lexical path",
                listOf(job("a", 3), job("b", 3), job("z", 2, "a", "b")), setOf("z"),
                result(5, listOf("a", "z"), "a" to timing(0, 3),
                    "b" to timing(0, 3), "z" to timing(3, 5))),
            Arguments.of("append current node before comparing prefix paths",
                listOf(job("a", 0), job("b", 0, "a"), job("z", 0, "a", "b")), setOf("z"),
                result(0, listOf("a", "b", "z"), "a" to timing(0, 0),
                    "b" to timing(0, 0), "z" to timing(0, 0))),
            Arguments.of("shorter equal-root prefix wins",
                listOf(job("a", 0), job("b", 0, "a")), setOf("a", "b"),
                result(0, listOf("a"), "a" to timing(0, 0), "b" to timing(0, 0))),
            Arguments.of("zero prerequisite remains in chain",
                listOf(job("z", 0), job("a", 0, "z")), setOf("a"),
                result(0, listOf("z", "a"), "z" to timing(0, 0), "a" to timing(0, 0))),
            Arguments.of("largest exact representable duration",
                listOf(job("a", Long.MAX_VALUE)), setOf("a"),
                result(Long.MAX_VALUE, listOf("a"), "a" to timing(0, Long.MAX_VALUE))),
            Arguments.of("parallel durations must not be summed into overflow",
                listOf(job("a", Long.MAX_VALUE), job("b", 1)), setOf("a", "b"),
                result(Long.MAX_VALUE, listOf("a"),
                    "a" to timing(0, Long.MAX_VALUE), "b" to timing(0, 1))),
        )

        @JvmStatic fun invalidInputs() = listOf(
            Arguments.of("duplicate IDs even with no selected roots",
                listOf(job("a", 1), job("a", 2)), emptySet<String>()),
            Arguments.of("blank ID", listOf(job(" ", 1)), emptySet<String>()),
            Arguments.of("unknown dependency in unselected declaration",
                listOf(job("a", 1), job("b", 2, "missing")), setOf("a")),
            Arguments.of("negative cost in unselected declaration",
                listOf(job("a", 1), job("b", -1)), setOf("a")),
            Arguments.of("unknown root", listOf(job("a", 1)), setOf("missing")),
            Arguments.of("blank root", listOf(job("a", 1)), setOf("")),
            Arguments.of("more than one hundred jobs",
                (0..100).map { job("j$it", 0) }, emptySet<String>()),
        )
    }
}

class ReferenceCriticalPathTest : CriticalPathContract() {
    override fun plan(jobs: List<Job>, roots: Set<String>) =
        staff.android.builds.transfer.solution.planCriticalPath(jobs, roots)
}

@Tag("exercise")
class LearnerCriticalPathTest : CriticalPathContract() {
    override fun plan(jobs: List<Job>, roots: Set<String>) =
        staff.android.builds.transfer.exercise.planCriticalPath(jobs, roots)
}
