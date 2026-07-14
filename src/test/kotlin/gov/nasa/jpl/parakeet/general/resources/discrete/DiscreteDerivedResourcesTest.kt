package gov.nasa.jpl.parakeet.general.resources.discrete

import gov.nasa.jpl.parakeet.foundation.reporting.Reporting.registered
import gov.nasa.jpl.parakeet.foundation.resources.discrete.BooleanResource
import gov.nasa.jpl.parakeet.foundation.resources.discrete.Discrete
import gov.nasa.jpl.parakeet.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.parakeet.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.parakeet.foundation.resources.discrete.IntResource
import gov.nasa.jpl.parakeet.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.parakeet.foundation.resources.discrete.MutableIntResource
import gov.nasa.jpl.parakeet.foundation.resources.getValue
import gov.nasa.jpl.parakeet.foundation.resources.named
import gov.nasa.jpl.parakeet.foundation.resources.timer.MutableTimerResource
import gov.nasa.jpl.parakeet.foundation.resources.timer.TimerResourceOperations.asTimer
import gov.nasa.jpl.parakeet.foundation.resources.timer.TimerResourceOperations.greaterThanOrEquals
import gov.nasa.jpl.parakeet.foundation.resources.timer.TimerResourceOperations.reset
import gov.nasa.jpl.parakeet.foundation.resources.timer.TimerResourceOperations.resume
import gov.nasa.jpl.parakeet.foundation.resources.timer.TimerResourceOperations.timer
import gov.nasa.jpl.parakeet.foundation.tasks.InitScope
import gov.nasa.jpl.parakeet.foundation.tasks.TaskOperations.delay
import gov.nasa.jpl.parakeet.general.resources.discrete.DiscreteDerivedResources.DiscreteDerivedResourceScope
import gov.nasa.jpl.parakeet.general.resources.discrete.DiscreteDerivedResources.discreteDerivedResource
import gov.nasa.jpl.parakeet.general.resources.discrete.DiscreteDerivedResources.getValue
import gov.nasa.jpl.parakeet.general.resources.discrete.DiscreteDerivedResourcesTest.TimerModel
import gov.nasa.jpl.parakeet.general.resources.discrete.DiscreteDerivedResourcesTest.XYModel
import gov.nasa.jpl.parakeet.general.results.ResourceResults
import gov.nasa.jpl.parakeet.general.testing.UnitTesting.runUnitTest
import gov.nasa.jpl.parakeet.kernel.Name
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DiscreteDerivedResourcesTest {
    val start: Instant = Instant.parse("2030-01-01T00:00:00Z")

    class XYModel(initScope: InitScope) {
        val x: MutableIntResource
        val y: MutableIntResource
        val z: IntResource

        init {
            context (initScope) {
                x = discreteResource("x", 1).registered()
                y = discreteResource("y", 2).registered()
                z = discreteDerivedResource { x.getValue() + y.getValue() }.named { "z" }.registered()
            }
        }
    }

    @Test
    fun `discrete derived resource reports the correct initial value`() {
        val results = runUnitTest(
            start,
            ::XYModel
        ) { model ->
            assertEquals(1, model.x.getValue())
            assertEquals(2, model.y.getValue())
            // We can read the derived resource from a task within simulation
            assertEquals(3, model.z.getValue())
        }

        // And we can read the same values in the results, since we registered the derived resource.
        @Suppress("UNCHECKED_CAST")
        val iter = (results.resources.getValue(Name("z")) as ResourceResults<Discrete<Int>>)
            .data
            .map { it.time to it.data.value }
            .iterator()
        assertEquals(start to 3, iter.next())
        assertFalse(iter.hasNext())
    }

    @Test
    fun `discrete derived resource reports the correct value as source resources change`() {
        val results = runUnitTest(
            start,
            ::XYModel
        ) { model ->
            assertEquals(3, model.z.getValue())
            model.x.set(4)
            assertEquals(6, model.z.getValue())
            delay(10.seconds)
            assertEquals(6, model.z.getValue())
            model.y.set(4)
            assertEquals(8, model.z.getValue())
            delay(10.seconds)
            assertEquals(8, model.z.getValue())
        }

        @Suppress("UNCHECKED_CAST")
        val iter = (results.resources.getValue(Name("z")) as ResourceResults<Discrete<Int>>)
            .data
            .map { it.time to it.data.value }
            .iterator()
        assertEquals(start to 3, iter.next())
        assertEquals(start to 6, iter.next())
        assertEquals(start + 10.seconds to 8, iter.next())
        assertFalse(iter.hasNext())
    }

    class TimerModel(initScope: InitScope) {
        val timer: MutableTimerResource
        val maxTime: MutableDiscreteResource<Duration>
        val timerExpired: BooleanResource

        val x: MutableIntResource
        val y: MutableIntResource

        val complexDerivation: IntResource

        init {
            context (initScope) {
                timer = timer("timer", initialRate = 0.0).registered()
                maxTime = discreteResource("maxTime", 10.seconds).registered()
                timerExpired = (timer greaterThanOrEquals maxTime.asTimer()).named { "timerExpired" }.registered()

                x = discreteResource("x", 1).registered()
                y = discreteResource("y", 2).registered()

                complexDerivation = discreteDerivedResource {
                    // TODO: This next line still compiles, using initScope instead of the implicit derivedResourceScope
                    // timer.getValue()
                    // This is a fairly random rule, but hopefully you can imagine something more meaningful in here.
                    if (timerExpired.getValue()) 100 * y.getValue() else 10 * x.getValue()
                }.named { "complexDerivation" }.registered()
            }
        }
    }

    /**
     * The derived resource can tolerate "early-expiring" source resources.
     * These are derived resources that expire before any of the cells they're derived from change.
     * The most common example of this comes from comparisons, e.g. a polynomial or timer crossing some threshold.
     */
    @Test
    fun `discrete derived resource reacts to early-expiring sources`() {
        val results = runUnitTest(
            start,
            ::TimerModel
        ) { model ->
            // We'll intersperse 0s delays to let the resource registration task observe each effect independently.
            assertEquals(false, model.timerExpired.getValue())
            assertEquals(10, model.complexDerivation.getValue())
            // This effect doesn't (immediately) change the value of the derivation:
            delay(0.seconds)
            model.timer.resume()
            assertEquals(false, model.timerExpired.getValue())
            assertEquals(10, model.complexDerivation.getValue())
            // But this effect does immediately change the value of the derivation:
            delay(0.seconds)
            model.x.set(2)
            assertEquals(false, model.timerExpired.getValue())
            assertEquals(20, model.complexDerivation.getValue())
            // This effect doesn't change the derivation now:
            delay(0.seconds)
            model.y.set(3)
            assertEquals(false, model.timerExpired.getValue())
            assertEquals(20, model.complexDerivation.getValue())
            // After some time elapses, the timer expires, which changes the derivation:
            delay(10.seconds)
            assertEquals(true, model.timerExpired.getValue())
            assertEquals(300, model.complexDerivation.getValue())
            // Now, changing x won't change the derivation:
            delay(0.seconds)
            model.x.set(4)
            assertEquals(true, model.timerExpired.getValue())
            assertEquals(300, model.complexDerivation.getValue())
            // But changing y will:
            delay(0.seconds)
            model.y.set(5)
            assertEquals(true, model.timerExpired.getValue())
            assertEquals(500, model.complexDerivation.getValue())
            // Finally, resetting the timer will change the derivation again:
            delay(0.seconds)
            model.timer.reset()
            assertEquals(false, model.timerExpired.getValue())
            assertEquals(40, model.complexDerivation.getValue())

            // Finally, delay for a non-zero amount of time to let the resource registration react to any final effects
            delay(1.seconds)
        }

        @Suppress("UNCHECKED_CAST")
        val iter = (results.resources.getValue(Name("complexDerivation")) as ResourceResults<Discrete<Int>>)
            .data
            .map { it.time to it.data.value }
            .iterator()
        assertEquals(start to 10, iter.next())
        assertEquals(start to 20, iter.next())
        assertEquals(start + 10.seconds to 300, iter.next())
        assertEquals(start + 10.seconds to 500, iter.next())
        assertEquals(start + 10.seconds to 40, iter.next())
        assertFalse(iter.hasNext())
    }

    /** Factors complexDerivation into a separate method */
    class TimerModel2(initScope: InitScope) {
        val timer: MutableTimerResource
        val maxTime: MutableDiscreteResource<Duration>
        val timerExpired: BooleanResource

        val x: MutableIntResource
        val y: MutableIntResource

        val complexDerivation: IntResource

        init {
            context (initScope) {
                timer = timer("timer", initialRate = 0.0).registered()
                maxTime = discreteResource("maxTime", 10.seconds).registered()
                timerExpired = (timer greaterThanOrEquals maxTime.asTimer()).named { "timerExpired" }.registered()

                x = discreteResource("x", 1).registered()
                y = discreteResource("y", 2).registered()

                // Factoring the derivation into a method like this has a couple benefits:
                // 1. For truly complex derivations, it tends to be more readable to split them up and give them names.
                // 2. Since the context param is listed for the function, the that function can't mistakenly access initScope.
                //    As a result, the derivation is unlikely to contain a subtle bug due to scope confusion.
                complexDerivation = discreteDerivedResource { computeComplexDerivation() }
                    .named { "complexDerivation" }
                    .registered()
            }
        }

        context (_: DiscreteDerivedResourceScope)
        fun computeComplexDerivation(): Int {
            // Unlike in TestModel above, the following line will fail to compile because timer is not discrete:
            // timer.getValue()
            // This is a fairly random rule, but hopefully you can imagine something more meaningful in here.
            return if (timerExpired.getValue()) {
                100 * y.getValue()
            } else {
                10 * x.getValue()
            }
        }
    }

    @Test
    fun `discrete derived resource using separate method functions identically to in-line derivation`() {
        val results = runUnitTest(
            start,
            ::TimerModel2
        ) { model ->
            // We'll intersperse 0s delays to let the resource registration task observe each effect independently.
            assertEquals(false, model.timerExpired.getValue())
            assertEquals(10, model.complexDerivation.getValue())
            // This effect doesn't (immediately) change the value of the derivation:
            delay(0.seconds)
            model.timer.resume()
            assertEquals(false, model.timerExpired.getValue())
            assertEquals(10, model.complexDerivation.getValue())
            // But this effect does immediately change the value of the derivation:
            delay(0.seconds)
            model.x.set(2)
            assertEquals(false, model.timerExpired.getValue())
            assertEquals(20, model.complexDerivation.getValue())
            // This effect doesn't change the derivation now:
            delay(0.seconds)
            model.y.set(3)
            assertEquals(false, model.timerExpired.getValue())
            assertEquals(20, model.complexDerivation.getValue())
            // After some time elapses, the timer expires, which changes the derivation:
            delay(10.seconds)
            assertEquals(true, model.timerExpired.getValue())
            assertEquals(300, model.complexDerivation.getValue())
            // Now, changing x won't change the derivation:
            delay(0.seconds)
            model.x.set(4)
            assertEquals(true, model.timerExpired.getValue())
            assertEquals(300, model.complexDerivation.getValue())
            // But changing y will:
            delay(0.seconds)
            model.y.set(5)
            assertEquals(true, model.timerExpired.getValue())
            assertEquals(500, model.complexDerivation.getValue())
            // Finally, resetting the timer will change the derivation again:
            delay(0.seconds)
            model.timer.reset()
            assertEquals(false, model.timerExpired.getValue())
            assertEquals(40, model.complexDerivation.getValue())

            // Finally, delay for a non-zero amount of time to let the resource registration react to any final effects
            delay(1.seconds)
        }

        @Suppress("UNCHECKED_CAST")
        val iter = (results.resources.getValue(Name("complexDerivation")) as ResourceResults<Discrete<Int>>)
            .data
            .map { it.time to it.data.value }
            .iterator()
        assertEquals(start to 10, iter.next())
        assertEquals(start to 20, iter.next())
        assertEquals(start + 10.seconds to 300, iter.next())
        assertEquals(start + 10.seconds to 500, iter.next())
        assertEquals(start + 10.seconds to 40, iter.next())
        assertFalse(iter.hasNext())
    }
}