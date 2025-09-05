package gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.ratioOver
import gov.nasa.jpl.pyre.ember.roundTimes
import gov.nasa.jpl.pyre.flame.resources.lens.MutableResourceLens.view
import gov.nasa.jpl.pyre.flame.units.DoubleFieldScope
import gov.nasa.jpl.pyre.flame.units.FieldScope
import gov.nasa.jpl.pyre.flame.units.Quantity
import gov.nasa.jpl.pyre.flame.units.QuantityOperations.valueIn
import gov.nasa.jpl.pyre.flame.units.ScalableScope
import gov.nasa.jpl.pyre.flame.units.StandardUnits
import gov.nasa.jpl.pyre.flame.units.Unit
import gov.nasa.jpl.pyre.flame.units.UnitAware
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.named
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteMonad
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.greaterThan
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.greaterThanOrEquals
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.lessThan
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.lessThanOrEquals
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.decrease
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations.increase
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.resources.named
import gov.nasa.jpl.pyre.spark.tasks.InitScope
import gov.nasa.jpl.pyre.spark.tasks.ResourceScope
import gov.nasa.jpl.pyre.spark.tasks.TaskScope

typealias QuantityResource = UnitAware<DoubleResource>
typealias MutableQuantityResource = UnitAware<MutableDoubleResource>

/**
 * Convenience functions for working with [QuantityResource] and [MutableQuantityResource]
 * Mostly, these are just the functions on [UnitAware], but with [DoubleResourceFieldScope] baked into the context for you.
 */
object QuantityResourceOperations {
    fun constant(quantity: Quantity): QuantityResource = with (DoubleFieldScope) {
        with (quantity) {
            UnitAware(pure(valueIn(unit)), unit) named { toString() }
        }
    }

    // Mutable resource constructor
    /**
     * Construct a unit-aware resource using [value]'s units.
     */
    context (scope: InitScope)
    fun quantityResource(name: String, value: Quantity): MutableQuantityResource = with(DoubleFieldScope) {
        UnitAware(discreteResource(name, value.valueIn(value.unit)), value.unit) { name }
    }

    /**
     * Construct a unit-aware resource and register it with the same units as [value]
     */
    context (scope: InitScope)
    fun registeredQuantityResource(name: String, value: Quantity): MutableQuantityResource =
        quantityResource(name, value).also { register(it, value.unit) }

    // Note: Units can be applied to a derived resource using the generic T * Unit operator.

    /**
     * Register a resource in a particular unit.
     * Note: The unit will be appended to the name of the resource automatically.
     */
    context (scope: InitScope)
    fun register(name: String, resource: QuantityResource, unit: Unit) = with (DoubleResourceFieldScope) {
        UnitAware.register(name, resource, unit)
    }

    /**
     * Register a resource in a particular unit.
     * Note: The unit will be appended to the name of the resource automatically.
     */
    context (scope: InitScope)
    fun register(resource: QuantityResource, unit: Unit) =
        register(resource.toString(), resource, unit)

    context (scope: ResourceScope)
    suspend fun QuantityResource.getValue(): Quantity =
        UnitAware(valueIn(unit).getValue(), unit)

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    fun MutableQuantityResource.valueIn(newUnit: Unit): MutableDoubleResource =
        with(MutableDoubleResourceScaling) {
            this@valueIn.valueIn(newUnit) named this@valueIn::toString
        }

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    fun QuantityResource.valueIn(newUnit: Unit): DoubleResource =
        with(DoubleResourceFieldScope) {
            this@valueIn.valueIn(newUnit) named this@valueIn::toString
        }

    operator fun QuantityResource.plus(other: QuantityResource): QuantityResource =
        with(DoubleResourceFieldScope) {
            with(UnitAware.Companion) {
                (this@plus + other) named { "(${this@plus}) + (${other})" }
            }
        }

    operator fun QuantityResource.minus(other: QuantityResource): QuantityResource =
        with(DoubleResourceFieldScope) {
            with(UnitAware.Companion) {
                (this@minus - other) named { "(${this@minus}) - (${other})" }
            }
        }

    operator fun QuantityResource.times(other: QuantityResource): QuantityResource =
        with(DoubleResourceFieldScope) {
            with(UnitAware.Companion) {
                (this@times * other) named { "(${this@times}) * (${other})" }
            }
        }

    operator fun Double.times(other: QuantityResource): QuantityResource =
        with(DoubleResourceFieldScope) {
            with(UnitAware.Companion) {
                (this@times * other) named { "(${this@times}) * (${other})" }
            }
        }

    operator fun QuantityResource.times(other: Double): QuantityResource =
        with(DoubleResourceFieldScope) {
            with(UnitAware.Companion) {
                (this@times * other) named { "(${this@times}) * (${other})" }
            }
        }

    operator fun QuantityResource.div(other: QuantityResource): QuantityResource =
        with(DoubleResourceFieldScope) {
            with(UnitAware.Companion) {
                (this@div / other) named { "(${this@div}) / (${other})" }
            }
        }

    operator fun Double.div(other: QuantityResource): QuantityResource =
        with(DoubleResourceFieldScope) {
            with(UnitAware.Companion) {
                (this@div / other) named { "(${this@div}) / (${other})" }
            }
        }

    operator fun QuantityResource.div(other: Double): QuantityResource =
        with(DoubleResourceFieldScope) {
            with(UnitAware.Companion) {
                (this@div / other) named { "(${this@div}) / (${other})" }
            }
        }

    infix fun QuantityResource.greaterThan(other: QuantityResource): BooleanResource =
        valueIn(unit) greaterThan other.valueIn(unit)
    infix fun QuantityResource.greaterThanOrEquals(other: QuantityResource): BooleanResource =
        valueIn(unit) greaterThanOrEquals other.valueIn(unit)
    infix fun QuantityResource.lessThan(other: QuantityResource): BooleanResource =
        valueIn(unit) lessThan other.valueIn(unit)
    infix fun QuantityResource.lessThanOrEquals(other: QuantityResource): BooleanResource =
        valueIn(unit) lessThanOrEquals other.valueIn(unit)

    // TODO: min/max/clamp?

    context (scope: TaskScope)
    suspend fun MutableQuantityResource.increase(amount: Quantity) {
        valueIn(unit).increase(amount.valueIn(unit))
    }

    context (scope: TaskScope)
    suspend fun MutableQuantityResource.decrease(amount: Quantity) {
        valueIn(unit).decrease(amount.valueIn(unit))
    }

    context (scope: TaskScope)
    suspend operator fun MutableQuantityResource.plusAssign(amount: Quantity) = increase(amount)

    context (scope: TaskScope)
    suspend operator fun MutableQuantityResource.minusAssign(amount: Quantity) = decrease(amount)

    /**
     * Operations involving a QuantityResource and a Quantity (in that order).
     *
     * These operations are split into a separate object to avoid JVM declaration conflicts.
     */
    object VsQuantity {
        operator fun QuantityResource.plus(other: Quantity): QuantityResource =
            this + constant(other)

        operator fun QuantityResource.minus(other: Quantity): QuantityResource =
            this - constant(other)

        operator fun QuantityResource.times(other: Quantity): QuantityResource =
            with(DoubleResourceFieldScope) {
                with(UnitAware.Companion.VsQuantity) {
                    (this@times * other) named { "(${this@times}) * (${other})" }
                }
            }

        operator fun QuantityResource.div(other: Quantity): QuantityResource =
            with(DoubleResourceFieldScope) {
                with(UnitAware.Companion.VsQuantity) {
                    (this@div / other) named { "(${this@div}) / (${other})" }
                }
            }

        infix fun QuantityResource.greaterThan(other: Quantity): BooleanResource =
            this greaterThan constant(other)
        infix fun QuantityResource.greaterThanOrEquals(other: Quantity): BooleanResource =
            this greaterThanOrEquals constant(other)
        infix fun QuantityResource.lessThan(other: Quantity): BooleanResource =
            this lessThan constant(other)
        infix fun QuantityResource.lessThanOrEquals(other: Quantity): BooleanResource =
            this lessThanOrEquals constant(other)
    }

    /**
     * Operations involving a Quantity and a QuantityResource (in that order).
     *
     * These operations are split into a separate object to avoid JVM declaration conflicts.
     */
    object QuantityVs {
        operator fun Quantity.plus(other: QuantityResource): QuantityResource =
            constant(this) + other

        operator fun Quantity.minus(other: QuantityResource): QuantityResource =
            constant(this) - other

        operator fun Quantity.times(other: QuantityResource): QuantityResource =
            with(DoubleResourceFieldScope) {
                with(UnitAware.Companion.QuantityVs) {
                    (this@times * other) named { "(${this@times}) * (${other})" }
                }
            }

        operator fun Quantity.div(other: QuantityResource): QuantityResource =
            with(DoubleResourceFieldScope) {
                with(UnitAware.Companion.QuantityVs) {
                    (this@div / other) named { "(${this@div}) / (${other})" }
                }
            }

        infix fun Quantity.greaterThan(other: QuantityResource): BooleanResource =
            constant(this) greaterThan other
        infix fun Quantity.greaterThanOrEquals(other: QuantityResource): BooleanResource =
            constant(this) greaterThanOrEquals other
        infix fun Quantity.lessThan(other: QuantityResource): BooleanResource =
            constant(this) lessThan other
        infix fun Quantity.lessThanOrEquals(other: QuantityResource): BooleanResource =
            constant(this) lessThanOrEquals other
    }

    object DurationQuantityResourceOperations {
        // Do the unit-awareness conversions at the resource level, so dimension checking happens only once
        fun DiscreteResource<Duration>.asQuantity(): QuantityResource =
            map(this) { it ratioOver Duration.SECOND } * StandardUnits.SECOND
        fun QuantityResource.asDuration(): DiscreteResource<Duration> =
            map(this.valueIn(StandardUnits.SECOND)) { it roundTimes Duration.SECOND }
    }
}

object MutableDoubleResourceScaling : ScalableScope<MutableDoubleResource> {
    // Since scaling is invertible, we can scale a mutable double resource, preserving mutability, through a view.
    override fun Double.times(other: MutableDoubleResource): MutableDoubleResource =
        other.view(InvertibleFunction.of(DiscreteMonad.map { this * it }, DiscreteMonad.map { it / this }))
}

object DoubleResourceFieldScope : FieldScope<DoubleResource> {
    override val zero: DoubleResource = pure(0.0)
    override val one: DoubleResource = pure(1.0)

    override fun DoubleResource.plus(other: DoubleResource): DoubleResource =
        with(DoubleResourceOperations) { this@plus + other }

    override fun DoubleResource.minus(other: DoubleResource): DoubleResource =
        with(DoubleResourceOperations) { this@minus - other }

    override fun Double.times(other: DoubleResource): DoubleResource =
        with(DoubleResourceOperations) { this@times * other }

    override fun DoubleResource.times(other: DoubleResource): DoubleResource =
        with(DoubleResourceOperations) { this@times * other }

    override fun DoubleResource.div(other: DoubleResource): DoubleResource =
        with(DoubleResourceOperations) { this@div / other }
}
