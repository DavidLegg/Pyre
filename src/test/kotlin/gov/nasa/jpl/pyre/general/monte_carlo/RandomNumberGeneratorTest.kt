package gov.nasa.jpl.pyre.general.monte_carlo

import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope.Companion.delay
import gov.nasa.jpl.pyre.general.testing.UnitTesting.runUnitTest
import gov.nasa.jpl.pyre.kernel.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.kernel.times
import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test
import kotlin.time.Instant

class RandomNumberGeneratorTest {
    private inline fun <reified M> runUnitTest(
        noinline initTestTask: context (InitScope) () -> M,
        noinline testTask: suspend context (TaskScope) (M) -> Unit = {},
    ) {
        runUnitTest(
            Instant.parse("2020-01-01T00:00:00Z"),
            initTestTask,
            testTask,
        )
    }

    @Test
    fun rng_can_be_used_during_init() {
        runUnitTest({
            val rng = RandomNumberGenerator(0L)
            val samples = mutableSetOf<Double>()
            // It's statistically near-impossible for 1000 random doubles to collide
            repeat(1000) {
                val x = rng.nextDouble()
                assert(x !in samples)
                samples += x
            }
        }) {}
    }

    @Test
    fun rng_can_be_used_during_task() {
        runUnitTest({
            RandomNumberGenerator(0L)
        }) { rng ->
            val samples = mutableSetOf<Double>()
            // It's statistically near-impossible for 1000 random doubles to collide
            repeat(1000) {
                val x = rng.nextDouble()
                assert(x !in samples)
                samples += x
            }
        }
    }

    @Test
    fun rng_is_deterministic() {
        val samples1 = mutableListOf<Double>()
        runUnitTest({
            RandomNumberGenerator(0L)
        }) { rng ->
            repeat(1000) {
                samples1 += rng.nextDouble()
            }
        }

        val samples2 = mutableListOf<Double>()
        runUnitTest({
            RandomNumberGenerator(0L)
        }) { rng ->
            repeat(1000) {
                samples2 += rng.nextDouble()
            }
        }

        assertEquals(samples1, samples2)
    }
}