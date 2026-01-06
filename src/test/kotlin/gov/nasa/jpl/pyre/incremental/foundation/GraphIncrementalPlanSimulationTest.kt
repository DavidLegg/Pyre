package gov.nasa.jpl.pyre.incremental.foundation

import gov.nasa.jpl.pyre.foundation.plans.Plan
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.IntResourceOperations.increment
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableIntResource
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.resources.named
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.spawn
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.whenever
import gov.nasa.jpl.pyre.foundation.tasks.TaskOperations.delay
import gov.nasa.jpl.pyre.foundation.tasks.task
import gov.nasa.jpl.pyre.general.resources.polynomial.MutablePolynomialResource
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.greaterThan
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.polynomialResource
import gov.nasa.jpl.pyre.kernel.Duration.Companion.EPSILON
import gov.nasa.jpl.pyre.kernel.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.kernel.times
import org.junit.jupiter.api.Assertions.*
import kotlin.math.roundToInt
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.time.Instant

class GraphIncrementalPlanSimulationTest {
    private val planStart = Instant.parse("2025-01-01T00:00:00Z")
    private val planEnd = Instant.parse("2025-02-01T00:00:00Z")
    private inline fun <reified M> test(
        plan: Plan<M> = Plan(planStart, planEnd),
        noinline constructModel: context (InitScope) () -> M
    ) = IncrementalSimulationTester(constructModel, plan, typeOf<M>())

    fun `empty model`() {
        test { /* no model to build */ }
    }


    fun `model with unregistered resources`() {
        test {
            val x = discreteResource("x", 10)
            val p = polynomialResource("p", 0.0, 1e-3)
            val b = (p greaterThan map(x) { it.toDouble() }.asPolynomial()).named { "b" }
            Triple(x, p, b)
        }
    }

    fun `model with registered resources`() {
        test {
            val x = discreteResource("x", 10).registered()
            val p = polynomialResource("p", 0.0, 1e-3).registered()
            val b = (p greaterThan map(x) { it.toDouble() }.asPolynomial()).named { "b" }.registered()
            Triple(x, p, b)
        }
    }

    fun `model with simple daemon`() {
        test {
            val x = discreteResource("x", 10).registered()
            val p = polynomialResource("p", 0.0, 1e-3).registered()
            val b = (p greaterThan map(x) { it.toDouble() }.asPolynomial()).named { "b" }.registered()
            spawn("daemon", whenever(b) {
                x.increment(10)
            })
            Triple(x, p, b)
        }
    }

    fun `model with daemon that spawns children`() {
        test {
            val x = discreteResource("x", 10).registered()
            val p = polynomialResource("p", 0.0, 1e-3).registered()
            val b = (p greaterThan map(x) { it.toDouble() }.asPolynomial()).named { "b" }.registered()
            spawn("daemon", whenever(b) {
                spawn("daemon child", task {
                    repeat(10) {
                        x.increment()
                        delay(5 * SECOND)
                    }
                })
                // Delay epsilon so the child task can bump x up, avoiding an infinite loop in the parent
                delay(EPSILON)
            })
            Triple(x, p, b)
        }
    }

    // TODO: Model with activities, empty plan
    // TODO: Non-empty initial plan (poke registered resource)
    // TODO: Non-empty initial plan (poke resource that triggers daemon to spawn child)
    // TODO: Empty initial plan + edit to add plan
    // TODO: Non-empty initial plan + edit to add plan

    // TODO: Find a way to bulk- / fuzz-test incremental edits.
}

private class IncrementalSimulationTester<M>(
    constructModel: context (InitScope) () -> M,
    plan: Plan<M>,
    modelClass: KType,
) {
    private val baselineSimulation = NonIncrementalPlanSimulation(constructModel, plan, modelClass)
    private val testSimulation = GraphIncrementalPlanSimulation(constructModel, plan, modelClass)

    init {
        assertSynced()
    }

    fun run(edits: PlanEdits<M>) {
        baselineSimulation.run(edits)
        testSimulation.run(edits)
        assertSynced()
    }

    private fun assertSynced() {
        assertEquals(baselineSimulation.plan, testSimulation.plan)
        assertEquals(baselineSimulation.results, testSimulation.results)
    }
}
