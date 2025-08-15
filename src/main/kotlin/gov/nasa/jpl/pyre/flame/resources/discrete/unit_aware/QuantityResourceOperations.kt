package gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.flame.resources.lens.MutableResourceLens.view
import gov.nasa.jpl.pyre.flame.units.DoubleFieldScope
import gov.nasa.jpl.pyre.flame.units.FieldScope
import gov.nasa.jpl.pyre.flame.units.ScalableScope
import gov.nasa.jpl.pyre.flame.units.Unit
import gov.nasa.jpl.pyre.flame.units.UnitAware
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.named
import gov.nasa.jpl.pyre.spark.reporting.Reporting.register
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteMonad.map
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DoubleResourceOperations
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.spark.resources.named
import gov.nasa.jpl.pyre.spark.tasks.InitScope

typealias QuantityResource = UnitAware<DoubleResource>
typealias MutableQuantityResource = UnitAware<MutableDoubleResource>

/**
 * Convenience functions for working with [QuantityResource] and [MutableQuantityResource]
 * Mostly, these are just the functions on [UnitAware], but with [DoubleResourceField] baked into the context for you.
 */
object QuantityResourceOperations {
    // Mutable resource constructor
    /**
     * Construct a unit-aware resource using [value]'s units.
     */
    context (scope: InitScope)
    fun quantityResource(name: String, value: UnitAware<Double>): MutableQuantityResource = with(DoubleFieldScope) {
        UnitAware(discreteResource(name, value.valueIn(value.unit)), value.unit) { name }
    }

    /**
     * Construct a unit-aware resource and register it with the same units as [value]
     */
    context (scope: InitScope)
    fun registeredQuantityResource(name: String, value: UnitAware<Double>): MutableQuantityResource =
        quantityResource(name, value).also { register(it, value.unit) }

    // Note: Units can be applied to a derived resource using the generic T * Unit operator.

    /**
     * Register a resource in a particular unit.
     * Note: The unit will be appended to the name of the resource automatically.
     */
    context (scope: InitScope)
    fun register(name: String, resource: QuantityResource, unit: Unit) =
        register("$name ($unit)", resource.valueIn(unit))

    /**
     * Register a resource in a particular unit.
     * Note: The unit will be appended to the name of the resource automatically.
     */
    context (scope: InitScope)
    fun register(resource: QuantityResource, unit: Unit) =
        register(resource.toString(), resource, unit)

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    fun MutableQuantityResource.valueIn(newUnit: Unit): MutableDoubleResource =
        with (MutableDoubleResourceScaling) {
            this@valueIn.valueIn(newUnit) named this@valueIn::toString
        }

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    fun QuantityResource.valueIn(newUnit: Unit): DoubleResource =
        with (DoubleResourceField) {
            this@valueIn.valueIn(newUnit) named this@valueIn::toString
        }

    operator fun QuantityResource.plus(other: QuantityResource): QuantityResource =
        with (DoubleResourceField) {
            (this@plus + other) named { "(${this@plus}) + (${other})" }
        }

    operator fun QuantityResource.minus(other: QuantityResource): QuantityResource =
        with (DoubleResourceField) {
            (this@minus - other) named { "(${this@minus}) - (${other})" }
        }

    operator fun QuantityResource.times(other: QuantityResource): QuantityResource =
        with (DoubleResourceField) {
            (this@times * other) named { "(${this@times}) * (${other})" }
        }

    operator fun Double.times(other: QuantityResource): QuantityResource =
        with (DoubleResourceField) {
            (this@times * other) named { "(${this@times}) * (${other})" }
        }

    operator fun QuantityResource.times(other: Double): QuantityResource =
        with (DoubleResourceField) {
            (this@times * other) named { "(${this@times}) * (${other})" }
        }

    operator fun QuantityResource.div(other: QuantityResource): QuantityResource =
        with (DoubleResourceField) {
            (this@div / other) named { "(${this@div}) / (${other})" }
        }
}

object MutableDoubleResourceScaling : ScalableScope<MutableDoubleResource> {
    // Since scaling is invertible, we can scale a mutable double resource, preserving mutability, through a view.
    override fun Double.times(other: MutableDoubleResource): MutableDoubleResource =
        other.view(InvertibleFunction.of(map { this * it }, map { it / this }))
}

object DoubleResourceField : FieldScope<DoubleResource> {
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
