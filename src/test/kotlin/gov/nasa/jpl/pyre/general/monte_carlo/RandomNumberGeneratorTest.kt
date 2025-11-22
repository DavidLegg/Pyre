package gov.nasa.jpl.pyre.general.monte_carlo

import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.every
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delay
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope.Companion.spawn
import gov.nasa.jpl.pyre.general.testing.UnitTesting.runUnitTest
import gov.nasa.jpl.pyre.kernel.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.kernel.times
import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test
import kotlin.test.assertEquals
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
                assert(samples.add(rng.nextDouble()))
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
                assert(samples.add(rng.nextDouble()))
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

    @Test
    fun rng_changes_with_different_seeds() {
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
            RandomNumberGenerator(1L)
        }) { rng ->
            repeat(1000) {
                samples2 += rng.nextDouble()
            }
        }

        assertNotEquals(samples1, samples2)
    }

    /**
     * When the RNG is sampled concurrently (multiple tasks accessing the RNG at precisely the same time),
     * they should get the same values.
     *
     * This is less a desirable feature of the RNG, and more a consequence of its determinism.
     * Since there's nothing to distinguish the calls to the RNG, they must be treated identically to be deterministic.
     */
    @Test
    fun concurrent_rng_access_is_identical() {
        val samples1 = mutableListOf<Double>()
        val samples2 = mutableListOf<Double>()
        val samples3 = mutableListOf<Double>()
        val samples4 = mutableListOf<Double>()

        runUnitTest({
            RandomNumberGenerator(0L)
        }) { rng ->
            // Parallel tasks will sample the rng in parallel, at exactly the same times
            spawn("Task 1", every(SECOND) { samples1 += rng.nextDouble() })
            spawn("Task 2", every(SECOND) { samples2 += rng.nextDouble() })
            spawn("Task 3", every(SECOND) { samples3 += rng.nextDouble() })
            spawn("Task 4", every(SECOND) { samples4 += rng.nextDouble() })

            // Collect a bunch of samples
            delay(1000 * SECOND)
        }

        assertEquals(samples1, samples2)
        assertEquals(samples1, samples3)
        assertEquals(samples1, samples4)
    }

    @Test
    fun rng_can_be_split_during_init() {
        runUnitTest({
            RandomNumberGenerator(0L).split()
        }) {}
    }

    @Test
    fun split_rng_can_be_sampled_during_init() {
        runUnitTest({
            val parentRng = RandomNumberGenerator(0L)
            val childRng = parentRng.split()

            // Each RNG produces completely distinct numbers
            val samples = mutableSetOf<Double>()
            repeat(1000) {
                // It's statistically near-impossible for 1000 random doubles to collide
                assert(samples.add(parentRng.nextDouble()))
            }

            repeat(1000) {
                // It's still statistically near-impossible for 2000 random doubles to collide
                assert(samples.add(childRng.nextDouble()))
            }
        }) {}
    }

    @Test
    fun split_rng_can_be_sampled_during_simulation() {
        runUnitTest({
            RandomNumberGenerator(0L).let { it to it.split() }
        }) { (parentRng, childRng) ->
            // Even in the worst case, running the two RNGs in lockstep, they produce completely different samples.

            // Note: We're technically breaking determinism with samples.
            //   It's mutable state information visible to multiple tasks.
            //   I'm relying on this rule-bending to ensure concurrent samples get different values:
            //   Since logically concurrent samples will physically happen one at a time, the second (physical)
            //   sample will observe the first (physical) sample, and ensure it's different.
            val samples = mutableSetOf<Double>()

            spawn("Sample parentRng", every(SECOND) {
                assert(samples.add(parentRng.nextDouble()))
            })
            spawn("Sample childRng", every(SECOND) {
                assert(samples.add(childRng.nextDouble()))
            })

            delay(1000 * SECOND)
        }
    }

    /**
     * Each split of an RNG is influenced only by the actions on that split.
     *
     * This is a way of reducing the overall chaos of a stochastic simulation.
     * Most large models are hierarchical, e.g. system/subsystem/equipment.
     * By splitting the RNG for each branch and level of that hierarchy,
     * modifying one branch doesn't change the behavior of another branch through changes to a shared RNG state.
     */
    @Test
    fun split_rngs_are_independent() {
        val parentSamples1 = mutableListOf<Double>()
        val childSamples1 = mutableListOf<Double>()
        runUnitTest({
            RandomNumberGenerator(0L).let { it to it.split() }
        }) { (parentRng, childRng) ->
            // In sim 1, sample the parent and then the child
            repeat(1000) { parentSamples1 += parentRng.nextDouble() }
            repeat(1000) { childSamples1 += childRng.nextDouble() }
        }

        val parentSamples2 = mutableListOf<Double>()
        val childSamples2 = mutableListOf<Double>()
        runUnitTest({
            RandomNumberGenerator(0L).let { it to it.split() }
        }) { (parentRng, childRng) ->
            // In sim 2, sample the child and then the parent
            repeat(1000) { childSamples2 += childRng.nextDouble() }
            repeat(1000) { parentSamples2 += parentRng.nextDouble() }
        }

        // Both sims should produce the same results, because each split RNG was used the same way.
        assertEquals(parentSamples1, parentSamples2)
        assertEquals(childSamples1, childSamples2)
    }
}