package gov.nasa.jpl.pyre.general.resources.polynomial

import gov.nasa.jpl.pyre.utilities.named
import gov.nasa.jpl.pyre.kernel.plus
import gov.nasa.jpl.pyre.general.resources.polynomial.Polynomial.Companion.polynomial
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.register
import gov.nasa.jpl.pyre.foundation.resources.*
import gov.nasa.jpl.pyre.foundation.resources.ResourceMonad.bind
import gov.nasa.jpl.pyre.foundation.resources.ResourceMonad.map
import gov.nasa.jpl.pyre.foundation.resources.ResourceMonad.pure
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.Discrete
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResource
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations.greaterThanOrEquals
import gov.nasa.jpl.pyre.foundation.tasks.*
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.spawn
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.subContext
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.dynamicsChange
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.or
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.whenTrue
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.whenever
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.simulationClock
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope.Companion.await
import gov.nasa.jpl.pyre.kernel.Condition
import gov.nasa.jpl.pyre.kernel.TRUE
import kotlin.let
import kotlin.math.max
import kotlin.math.min

object PolynomialResourceOperations {
    context(scope: InitScope)
    fun polynomialResource(name: String, vararg coefficients: Double): MutablePolynomialResource =
        resource(name, polynomial(*coefficients))

    fun constant(value: Double): PolynomialResource = pure(polynomial(value)) named value::toString

    context (scope: InitScope)
    fun registeredPolynomialResource(name: String, vararg coefficients: Double) =
        polynomialResource(name, *coefficients).also { register(it) }

    fun DoubleResource.asPolynomial(): PolynomialResource =
        map(this) { polynomial(it.value) } named this::toString

    operator fun PolynomialResource.unaryPlus() = this
    operator fun PolynomialResource.unaryMinus() = map(this, Polynomial::unaryMinus) named { "-($this)" }

    operator fun PolynomialResource.plus(other: PolynomialResource) =
        map(this, other) { p, q -> p + q } named { "($this) + ($other)" }
    operator fun PolynomialResource.minus(other: PolynomialResource) =
        map(this, other) { p, q -> p - q } named { "($this) - ($other)" }
    operator fun PolynomialResource.times(other: PolynomialResource) =
        map(this, other) { p, q -> p * q } named { "($this) * ($other)" }

    operator fun PolynomialResource.plus(other: Double) =
        map(this) { it + other } named { "($this) + ($other)" }
    operator fun PolynomialResource.minus(other: Double) =
        map(this) { it - other } named { "($this) - ($other)" }
    operator fun PolynomialResource.times(other: Double) =
        map(this) { it * other } named { "($this) * ($other)" }

    operator fun Double.plus(other: PolynomialResource) =
        map(other) { this + it } named { "($this) + ($other)" }
    operator fun Double.minus(other: PolynomialResource) =
        map(other) { this - it } named { "($this) - ($other)" }
    operator fun Double.times(other: PolynomialResource) =
        map(other) { this * it } named { "($this) * ($other)" }

    operator fun PolynomialResource.div(other: Double) =
        map(this) { it / other } named { "($this) / ($other)" }

    fun PolynomialResource.derivative(): PolynomialResource =
        map(this, Polynomial::derivative) named { "d/dt ($this)" }

    context(scope: InitScope)
    fun PolynomialResource.integral(name: String, startingValue: Double): IntegralResource {
        val integral = polynomialResource(name, startingValue)
        spawn("Update $name", whenever(map(this@integral, integral) {
                p, q -> Discrete(p.integral(q.value()) != q)
        }) {
            val integrandDynamics = this@integral.getDynamics()
            integral.emit { q -> DynamicsMonad.map(integrandDynamics) { it.integral(q.data.value()) } }
        })
        return object : IntegralResource, PolynomialResource by integral {
            context(scope: TaskScope)
            override suspend fun increase(amount: Double) = integral.increase(amount)
            context(scope: TaskScope)
            override suspend fun set(amount: Double) = integral.emit({ p: Polynomial ->
                p.setCoefficient(0, amount)
            } named { "Set value of $this to $amount" })
        } named { name }
    }

    context(context: InitScope)
    fun PolynomialResource.registeredIntegral(name: String, startingValue: Double) =
        integral(name, startingValue).also { register(it) }

    /**
     * Compute the integral of integrand, starting at startingValue.
     * Also clamp the integral between lowerBound and upperBound (inclusive).
     * <p>
     *   Note that <code>clampedIntegrate(r, l, u, s)</code> differs from
     *   <code>clamp(integrate(r, s), l, u)</code> in how they handle reversing from a boundary.
     * </p>
     * <p>
     *   To see how, consider bounds of [0, 5], with an integrand of 1 for 10 seconds, then -1 for 10 seconds.
     * </p>
     * <p>
     *   clamp and integrate:
     * </p>
     * <pre>
     *   5       /----------\
     *          /            \
     *         /              \
     *        /                \
     *   0   /                  \
     * time  0        10        20
     * </pre>
     * <p>
     *   clampedIntegrate:
     * </p>
     * <pre>
     *   5       /-----\
     *          /       \
     *         /         \
     *        /           \
     *   0   /             \-----
     * time  0        10        20
     * </pre>
     * <p>
     *     NOTE: This method assumes that lowerBound <= upperBound at all times.
     *     Failure to meet this precondition may lead to incorrect outputs, crashing the simulation, stalling in an infinite loop,
     *     or other misbehavior.
     *     If this condition cannot be guaranteed a priori, consider using
     *     {@link gov.nasa.jpl.aerie.contrib.streamline.modeling.discrete.DiscreteResources#assertThat(String, Resource)}
     *     to guarantee this condition at runtime.
     * </p>
     */
    context(scope: InitScope)
    fun PolynomialResource.clampedIntegral(
        name: String,
        lowerBound: PolynomialResource,
        upperBound: PolynomialResource,
        startingValue: Double
    ): ClampedIntegralResult {
        // There are more elegant ways to write this method, but profiling suggests this is often a bottleneck to models that use it.
        // So, we're writing it very carefully to maximize performance.

        val integrand = this
        val integral = polynomialResource(name, startingValue)
        val (overflow, underflow) = subContext(name) {
            Pair(
                polynomialResource("overflow", 0.0),
                polynomialResource("underflow", 0.0),
            )
        }

        // Run once immediately to get the loop started.
        var condition = TRUE
        spawn("Compute $name", repeatingTask {
            // Perform the await at the start of the task, to minimize saved task history
            await(condition)

            // Note we need to call dynamicsChange on each iteration because it depends on the resource's current value,
            // so *don't* factor these out of the loop.
            val someInputChanges = (dynamicsChange(integrand)
                    or dynamicsChange(lowerBound)
                    or dynamicsChange(upperBound))

            val integralValue = integral.getValue()
            val now = simulationClock.getValue()

            // Get all dynamics once per update loop to minimize sampling cost.
            val result = DynamicsMonad.map(
                integrand.getDynamics(),
                lowerBound.getDynamics(),
                upperBound.getDynamics()
            ) { integrand, lowerBound, upperBound ->

                // Clamp the integral value to take care of small overshoots due to the discretization of time
                // and discrete changes in bounds that cut into the integral.
                // Also, just get the current value, don't try to derive a value.
                // This intentionally erases expiry info from the integral since this is a resource-graph back-edge,
                // and will throw and blow up this resource if integral fails.
                val integralValue = max(min(integralValue, upperBound.value()), lowerBound.value())
                val integral = integrand.integral(integralValue)

                if (lowerBound._dominates(integral)) {
                    // Lower bound dominates "real" integral, so we clamp to the lower bound
                    // We stop clamping to the lower bound when the integrand rate exceeds the lowerBound rate.
                    // Since we already know that lowerBound dominates integral and integral value is at least lower value,
                    // we know that lower rate dominates integrand. Hence, there's no need to check that value here.
                    val lowerRate = lowerBound.derivative()
                    val lowerRateExpires = lowerRate.dominates(integrand).expiry.time
                    val clampingStops =
                        lowerRateExpires?.let { whenTrue(simulationClock greaterThanOrEquals now + it) }
                    // We change out of this condition when we stop clamping to the lower bound, or something changes.
                    ClampedIntegrateInternalResult(
                        lowerBound,
                        polynomial(0.0),
                        lowerRate - integrand,
                        clampingStops?.let { someInputChanges or it } ?: someInputChanges,
                    )
                } else if (!upperBound._dominates(integral)) {
                    // Upper bound doesn't dominate integral, so we clamp to the upper bound
                    // We stop clamping to the upper bound when the integrand rate falls below the upperBound rate.
                    // Since we already know that lowerBound dominates integral and integral value is at least lower value,
                    // we know that lower rate dominates integrand. Hence, there's no need to check that value here.
                    val upperRate = upperBound.derivative()
                    val upperRateExpires = upperRate.dominates(integrand).expiry.time
                    val clampingStops =
                        upperRateExpires?.let { whenTrue(simulationClock greaterThanOrEquals now + it) }
                    // We change out of this condition when we stop clamping to the upper bound, or something changes.
                    ClampedIntegrateInternalResult(
                        upperBound,
                        integrand - upperRate,
                        polynomial(0.0),
                        clampingStops?.let { someInputChanges or it } ?: someInputChanges,
                    )
                } else {
                    // Otherwise, the integral is between the bounds, so we just set it as-is.
                    // We start clamping when one or the other bound impacts this integral, i.e., when a dominates value changes.
                    // Although we re-compute the value of dominates$ here, that's cheap.
                    // We avoid computing the more expensive expiry when we're clamping, which is a win overall.
                    val startClampingToLowerBound = lowerBound.dominates(integral).expiry
                    val startClampingToUpperBound = upperBound.dominates(integral).expiry
                    val clampingStarts = (startClampingToLowerBound or startClampingToUpperBound).time?.let {
                        whenTrue(simulationClock greaterThanOrEquals now + it)
                    }
                    ClampedIntegrateInternalResult(
                        integral,
                        polynomial(0.0),
                        polynomial(0.0),
                        clampingStarts?.let { someInputChanges or it } ?: someInputChanges,
                    )
                }
            }

            var newIntegralDynamics = DynamicsMonad.map(result, ClampedIntegrateInternalResult::integral)
            var newOverflowDynamics = DynamicsMonad.map(result, ClampedIntegrateInternalResult::overflow)
            var newUnderflowDynamics = DynamicsMonad.map(result, ClampedIntegrateInternalResult::underflow)

            integral.emit { newIntegralDynamics }
            overflow.emit { newOverflowDynamics }
            underflow.emit { newUnderflowDynamics }

            // Update the condition to be awaited next iteration
            condition = result.data.retryCondition
        })

        return ClampedIntegralResult(integral, overflow, underflow)
    }

    private data class ClampedIntegrateInternalResult(
        val integral: Polynomial,
        val overflow: Polynomial,
        val underflow: Polynomial,
        val retryCondition: Condition
    )

    data class ClampedIntegralResult(
        /**
         * The clamped integral value
         */
        val integral: PolynomialResource,
        /**
         * The rate of overflow.
         *
         * Positive only when the integral is clamping to the upper bound,
         * otherwise zero.
         */
        val overflow: PolynomialResource,
        /**
         * The rate of underflow.
         *
         * Positive only when the integral is clamping to the lower bound,
         * otherwise zero.
         */
        val underflow: PolynomialResource,
    )

    infix fun PolynomialResource.greaterThan(other: PolynomialResource): BooleanResource =
        bind(this, other) { p, q -> ThinResourceMonad.pure(p greaterThan q) } named { "($this) > ($other)" }
    infix fun PolynomialResource.greaterThanOrEquals(other: PolynomialResource): BooleanResource =
        bind(this, other) { p, q -> ThinResourceMonad.pure(p greaterThanOrEquals q) } named { "($this) >= ($other)" }
    infix fun PolynomialResource.lessThan(other: PolynomialResource): BooleanResource =
        bind(this, other) { p, q -> ThinResourceMonad.pure(p lessThan q) } named { "($this) < ($other)" }
    infix fun PolynomialResource.lessThanOrEquals(other: PolynomialResource): BooleanResource =
        bind(this, other) { p, q -> ThinResourceMonad.pure(p lessThanOrEquals q) } named { "($this) <= ($other)" }

    infix fun PolynomialResource.greaterThan(other: Double) = this greaterThan constant(other)
    infix fun PolynomialResource.greaterThanOrEquals(other: Double) = this greaterThanOrEquals constant(other)
    infix fun PolynomialResource.lessThan(other: Double) = this lessThan constant(other)
    infix fun PolynomialResource.lessThanOrEquals(other: Double) = this lessThanOrEquals constant(other)

    fun min(p: PolynomialResource, q: PolynomialResource): PolynomialResource = bind(p, q) { p, q ->
        ThinResourceMonad.pure(DynamicsMonad.map(p.dominates(q)) { if (it.value) q else p }) named { "min($p, $q)" }
    }

    fun max(p: PolynomialResource, q: PolynomialResource): PolynomialResource = bind(p, q) { p, q ->
        ThinResourceMonad.pure(DynamicsMonad.map(p.dominates(q)) { if (it.value) p else q }) named { "max($p, $q)" }
    }

    fun PolynomialResource.clamp(lowerBound: PolynomialResource, upperBound: PolynomialResource): PolynomialResource =
        min(max(this, lowerBound), upperBound) named { "$this.clamp($lowerBound, $upperBound)" }

    context(scope: TaskScope)
    suspend fun MutablePolynomialResource.increase(amount: Double) = emit({ p: Polynomial -> p + amount } named { "Increase $this by $amount" })
    context(scope: TaskScope)
    suspend fun MutablePolynomialResource.decrease(amount: Double) = emit({ p: Polynomial -> p - amount } named { "Decrease $this by $amount" })

    context(scope: TaskScope)
    suspend operator fun MutablePolynomialResource.plusAssign(amount: Double) = increase(amount)
    context(scope: TaskScope)
    suspend operator fun MutablePolynomialResource.minusAssign(amount: Double) = decrease(amount)

    context(scope: TaskScope)
    suspend fun IntegralResource.decrease(amount: Double) = increase(-amount)
    context(scope: TaskScope)
    suspend operator fun IntegralResource.plusAssign(amount: Double) = increase(amount)
    context(scope: TaskScope)
    suspend operator fun IntegralResource.minusAssign(amount: Double) = decrease(amount)
}

/**
 * A restriction of a full [MutablePolynomialResource] which supports only increase/decrease/set operations.
 * This can be used to represent a quantity with both discrete and continuous changes.
 * Integrating a rate resource accounts for continuous change, and increase/decrease/set operations on the result
 * account for discrete changes.
 */
interface IntegralResource : PolynomialResource {
    context(scope: TaskScope)
    suspend fun increase(amount: Double)
    context(scope: TaskScope)
    suspend fun set(amount: Double)
}

infix fun IntegralResource.named(nameFn: () -> String) = object : IntegralResource by this {
    override fun toString() = nameFn()
}
