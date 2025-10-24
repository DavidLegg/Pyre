package gov.nasa.jpl.pyre.general.resources.polynomial.unit_aware

import gov.nasa.jpl.pyre.utilities.InvertibleFunction
import gov.nasa.jpl.pyre.general.resources.discrete.unit_aware.QuantityResource
import gov.nasa.jpl.pyre.general.resources.discrete.unit_aware.QuantityResourceOperations.valueIn
import gov.nasa.jpl.pyre.general.resources.lens.MutableResourceLens.view
import gov.nasa.jpl.pyre.general.resources.polynomial.IntegralResource
import gov.nasa.jpl.pyre.general.resources.polynomial.MutablePolynomialResource
import gov.nasa.jpl.pyre.general.resources.polynomial.Polynomial
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.decrease
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.greaterThan
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.greaterThanOrEquals
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.increase
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.lessThan
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.lessThanOrEquals
import gov.nasa.jpl.pyre.general.resources.polynomial.times
import gov.nasa.jpl.pyre.general.resources.polynomial.unit_aware.PolynomialQuantityOperations.polynomial
import gov.nasa.jpl.pyre.general.units.DoubleFieldScope
import gov.nasa.jpl.pyre.general.units.Quantity
import gov.nasa.jpl.pyre.general.units.QuantityOperations.valueIn
import gov.nasa.jpl.pyre.general.units.ScalableScope
import gov.nasa.jpl.pyre.general.units.StandardUnits.SECOND
import gov.nasa.jpl.pyre.general.units.Unit
import gov.nasa.jpl.pyre.general.units.UnitAware
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.named
import gov.nasa.jpl.pyre.foundation.resources.ResourceMonad.pure
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.resources.resource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope
import gov.nasa.jpl.pyre.foundation.resources.named
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope

typealias PolynomialQuantityResource = UnitAware<PolynomialResource>
typealias MutablePolynomialQuantityResource = UnitAware<MutablePolynomialResource>
typealias IntegralQuantityResource = UnitAware<IntegralResource>

object PolynomialQuantityResourceOperations {
    fun constant(quantity: Quantity): PolynomialQuantityResource = with (DoubleFieldScope) {
            UnitAware(
                pure(Polynomial.polynomial(
                    quantity.valueIn(quantity.unit))),
                quantity.unit) named { quantity.toString() }
        }

    // Mutable resource constructor
    /**
     * Construct a unit-aware resource using unit-aware [coefficients]
     */
    context (scope: InitScope)
    fun quantityResource(name: String, vararg coefficients: Quantity): MutablePolynomialQuantityResource =
        quantityResource(name, polynomial(*coefficients))

    /**
     * Construct a unit-aware resource using a unit-aware default [value]
     */
    context (scope: InitScope)
    fun quantityResource(name: String, value: PolynomialQuantity): MutablePolynomialQuantityResource =
        with(PolynomialRingScope) {
            UnitAware(resource(name, value.valueIn(value.unit)), value.unit) named { name }
        }

    // Note: Units can be applied to a derived resource using the generic T * Unit operator.

    fun QuantityResource.asPolynomial(): PolynomialQuantityResource =
        UnitAware(valueIn(unit).asPolynomial(), unit)

    /**
     * Register a resource in a particular unit.
     * Note: The unit will be appended to the name of the resource automatically.
     */
    context (scope: InitScope)
    fun register(name: String, resource: PolynomialQuantityResource, unit: Unit) = with (PolynomialResourceRingScope) {
        UnitAware.register(name, resource, unit)
    }

    /**
     * Register a resource in a particular unit.
     * Note: The unit will be appended to the name of the resource automatically.
     */
    context (scope: InitScope)
    fun register(resource: PolynomialQuantityResource, unit: Unit) =
        register(resource.toString(), resource, unit)

    context (scope: ResourceScope)
    suspend fun PolynomialQuantityResource.getValue(): Quantity =
        UnitAware(valueIn(unit).getValue(), unit)

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    fun MutablePolynomialQuantityResource.valueIn(newUnit: Unit): MutablePolynomialResource =
        with (MutablePolynomialResourceScaling) {
            this@valueIn.valueIn(newUnit) named this@valueIn::toString
        }

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    fun PolynomialQuantityResource.valueIn(newUnit: Unit): PolynomialResource =
        with (PolynomialResourceRingScope) {
            this@valueIn.valueIn(newUnit) named this@valueIn::toString
        }

    operator fun PolynomialQuantityResource.plus(other: PolynomialQuantityResource): PolynomialQuantityResource =
        with(PolynomialResourceRingScope) {
            with(UnitAware.Companion) {
                (this@plus + other) named { "(${this@plus}) + (${other})" }
            }
        }

    operator fun PolynomialQuantityResource.minus(other: PolynomialQuantityResource): PolynomialQuantityResource =
        with(PolynomialResourceRingScope) {
            with(UnitAware.Companion) {
                (this@minus - other) named { "(${this@minus}) - (${other})" }
            }
        }

    operator fun PolynomialQuantityResource.times(other: PolynomialQuantityResource): PolynomialQuantityResource =
        with(PolynomialResourceRingScope) {
            with(UnitAware.Companion) {
                (this@times * other) named { "(${this@times}) * (${other})" }
            }
        }

    operator fun Double.times(other: PolynomialQuantityResource): PolynomialQuantityResource =
        with(PolynomialResourceRingScope) {
            with(UnitAware.Companion) {
                (this@times * other) named { "(${this@times}) * (${other})" }
            }
        }

    operator fun PolynomialQuantityResource.times(other: Double): PolynomialQuantityResource =
        with(PolynomialResourceRingScope) {
            with(UnitAware.Companion) {
                (this@times * other) named { "(${this@times}) * (${other})" }
            }
        }

    operator fun PolynomialQuantityResource.div(other: Double): PolynomialQuantityResource =
        with(PolynomialResourceRingScope) {
            with(UnitAware.Companion) {
                (this@div / other) named { "(${this@div}) / (${other})" }
            }
        }

    fun PolynomialQuantityResource.derivative(): PolynomialQuantityResource =
        with (PolynomialResourceOperations) {
            UnitAware(valueIn(unit).derivative(), unit / SECOND)
        }

    context (scope: InitScope)
    fun PolynomialQuantityResource.integral(name: String, startingValue: Quantity): IntegralQuantityResource =
        with(PolynomialResourceOperations) {
            UnitAware(
                valueIn(startingValue.unit / SECOND).integral(name, startingValue.valueIn(startingValue.unit)),
                startingValue.unit
            ) named { name }
        }

    context (scope: InitScope)
    fun PolynomialQuantityResource.registeredIntegral(name: String, startingValue: Quantity): IntegralQuantityResource =
        integral(name, startingValue).also { register(it, startingValue.unit) }

    context (scope: InitScope)
    fun PolynomialQuantityResource.clampedIntegral(
        name: String,
        lowerBound: PolynomialQuantityResource,
        upperBound: PolynomialQuantityResource,
        startingValue: Quantity,
    ): ClampedQuantityIntegralResult = with (PolynomialResourceOperations) {
        val unit = startingValue.unit
        val scalarResult = valueIn(unit / SECOND).clampedIntegral(
            name,
            lowerBound.valueIn(unit),
            upperBound.valueIn(unit),
            startingValue.valueIn(unit))
        return ClampedQuantityIntegralResult(
            UnitAware(scalarResult.integral, unit, scalarResult.integral::toString),
            UnitAware(scalarResult.overflow, unit / SECOND, scalarResult.overflow::toString),
            UnitAware(scalarResult.underflow, unit / SECOND, scalarResult.underflow::toString))
    }

    data class ClampedQuantityIntegralResult(
        val integral: PolynomialQuantityResource,
        val overflow: PolynomialQuantityResource,
        val underflow: PolynomialQuantityResource,
    )

    infix fun PolynomialQuantityResource.greaterThan(other: PolynomialQuantityResource): BooleanResource =
        valueIn(unit) greaterThan other.valueIn(unit)
    infix fun PolynomialQuantityResource.greaterThanOrEquals(other: PolynomialQuantityResource): BooleanResource =
        valueIn(unit) greaterThanOrEquals other.valueIn(unit)
    infix fun PolynomialQuantityResource.lessThan(other: PolynomialQuantityResource): BooleanResource =
        valueIn(unit) lessThan other.valueIn(unit)
    infix fun PolynomialQuantityResource.lessThanOrEquals(other: PolynomialQuantityResource): BooleanResource =
        valueIn(unit) lessThanOrEquals other.valueIn(unit)

    fun min(p: PolynomialQuantityResource, q: PolynomialQuantityResource): PolynomialQuantityResource =
        UnitAware(PolynomialResourceOperations.min(p.valueIn(p.unit), q.valueIn(p.unit)), p.unit)

    fun max(p: PolynomialQuantityResource, q: PolynomialQuantityResource): PolynomialQuantityResource =
        UnitAware(PolynomialResourceOperations.max(p.valueIn(p.unit), q.valueIn(p.unit)), p.unit)

    fun PolynomialQuantityResource.clamp(lowerBound: PolynomialQuantityResource, upperBound: PolynomialQuantityResource): PolynomialQuantityResource =
        with (PolynomialResourceOperations) {
            UnitAware(valueIn(unit).clamp(lowerBound.valueIn(unit), upperBound.valueIn(unit)), unit)
        }

    context (scope: TaskScope)
    suspend fun MutablePolynomialQuantityResource.increase(amount: Quantity) =
        valueIn(unit).increase(amount.valueIn(unit))

    context (scope: TaskScope)
    suspend fun MutablePolynomialQuantityResource.decrease(amount: Quantity) =
        valueIn(unit).decrease(amount.valueIn(unit))

    context (scope: TaskScope)
    suspend operator fun MutablePolynomialQuantityResource.plusAssign(amount: Quantity) = increase(amount)

    context (scope: TaskScope)
    suspend operator fun MutablePolynomialQuantityResource.minusAssign(amount: Quantity) = decrease(amount)

    /**
     * Operations involving a PolynomialQuantityResource and a Quantity (in that order).
     *
     * These operations are split into a separate object to avoid JVM declaration conflicts.
     */
    object VsQuantity {
        operator fun PolynomialQuantityResource.plus(other: Quantity): PolynomialQuantityResource =
            this + constant(other)

        operator fun PolynomialQuantityResource.minus(other: Quantity): PolynomialQuantityResource =
            this - constant(other)

        operator fun PolynomialQuantityResource.times(other: Quantity): PolynomialQuantityResource =
            with(PolynomialResourceRingScope) {
                with(UnitAware.Companion.VsQuantity) {
                    (this@times * other) named { "(${this@times}) * (${other})" }
                }
            }

        operator fun PolynomialQuantityResource.div(other: Quantity): PolynomialQuantityResource =
            with(PolynomialResourceRingScope) {
                with(UnitAware.Companion.VsQuantity) {
                    (this@div / other) named { "(${this@div}) / (${other})" }
                }
            }

        infix fun PolynomialQuantityResource.greaterThan(other: Quantity): BooleanResource =
            this greaterThan constant(other)
        infix fun PolynomialQuantityResource.greaterThanOrEquals(other: Quantity): BooleanResource =
            this greaterThanOrEquals constant(other)
        infix fun PolynomialQuantityResource.lessThan(other: Quantity): BooleanResource =
            this lessThan constant(other)
        infix fun PolynomialQuantityResource.lessThanOrEquals(other: Quantity): BooleanResource =
            this lessThanOrEquals constant(other)

        fun min(p: PolynomialQuantityResource, q: Quantity): PolynomialQuantityResource =
            min(p, constant(q))
        fun max(p: PolynomialQuantityResource, q: Quantity): PolynomialQuantityResource =
            max(p, constant(q))
        fun PolynomialQuantityResource.clamp(lowerBound: Quantity, upperBound: Quantity): PolynomialQuantityResource =
            clamp(constant(lowerBound), constant(upperBound))
    }

    /**
     * Operations involving a PolynomialQuantityResource and a Quantity (in that order).
     *
     * These operations are split into a separate object to avoid JVM declaration conflicts.
     */
    object QuantityVs {
        operator fun Quantity.plus(other: PolynomialQuantityResource): PolynomialQuantityResource =
            constant(this) + other

        operator fun Quantity.minus(other: PolynomialQuantityResource): PolynomialQuantityResource =
            constant(this) - other

        operator fun Quantity.times(other: PolynomialQuantityResource): PolynomialQuantityResource =
            with(PolynomialResourceRingScope) {
                with(UnitAware.Companion.QuantityVs) {
                    (this@times * other) named { "(${this@times}) * (${other})" }
                }
            }

        infix fun Quantity.greaterThan(other: PolynomialQuantityResource): BooleanResource =
            constant(this) greaterThan other
        infix fun Quantity.greaterThanOrEquals(other: PolynomialQuantityResource): BooleanResource =
            constant(this) greaterThanOrEquals other
        infix fun Quantity.lessThan(other: PolynomialQuantityResource): BooleanResource =
            constant(this) lessThan other
        infix fun Quantity.lessThanOrEquals(other: PolynomialQuantityResource): BooleanResource =
            constant(this) lessThanOrEquals other

        fun min(p: Quantity, q: PolynomialQuantityResource): PolynomialQuantityResource =
            min(constant(p), q)
        fun max(p: Quantity, q: PolynomialQuantityResource): PolynomialQuantityResource =
            max(constant(p), q)
    }
}

// Operations on IntegralQuantityResources, which are also UnitAware<...>, must be in another object to avoid JVM declaration clashes.
object IntegralQuantityResourceOperations {
    context (scope: TaskScope)
    suspend fun IntegralQuantityResource.increase(amount: Quantity) = with (IntegralPolynomialResourceScaling) {
        valueIn(unit).increase(amount.valueIn(unit))
    }

    context (scope: TaskScope)
    suspend fun IntegralQuantityResource.decrease(amount: Quantity) = with (IntegralPolynomialResourceScaling) {
        valueIn(unit).decrease(amount.valueIn(unit))
    }

    context (scope: TaskScope)
    suspend operator fun IntegralQuantityResource.plusAssign(amount: Quantity) = increase(amount)

    context (scope: TaskScope)
    suspend operator fun IntegralQuantityResource.minusAssign(amount: Quantity) = decrease(amount)
}

object MutablePolynomialResourceScaling : ScalableScope<MutablePolynomialResource> {
    // Since scaling is invertible, we can scale a mutable polynomial resource, preserving mutability, through a view.
    override fun Double.times(other: MutablePolynomialResource): MutablePolynomialResource =
        other.view(InvertibleFunction.of({ this * it }, { it / this }))
}

object IntegralPolynomialResourceScaling : ScalableScope<IntegralResource> {
    override fun Double.times(other: IntegralResource): IntegralResource =
        object : IntegralResource, PolynomialResource by this@times * other {
            context(scope: TaskScope)
            override suspend fun increase(amount: Double) = other.increase(amount / this@times)

            context(scope: TaskScope)
            override suspend fun set(amount: Double) = other.set(amount / this@times)
        }
}
