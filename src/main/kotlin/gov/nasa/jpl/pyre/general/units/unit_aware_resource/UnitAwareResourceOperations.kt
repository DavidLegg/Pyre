package gov.nasa.jpl.pyre.general.units.unit_aware_resource

import gov.nasa.jpl.pyre.foundation.reporting.Reporting
import gov.nasa.jpl.pyre.foundation.resources.Dynamics
import gov.nasa.jpl.pyre.foundation.resources.FullDynamics
import gov.nasa.jpl.pyre.foundation.resources.MutableResource
import gov.nasa.jpl.pyre.foundation.resources.Resource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.foundation.resources.fullyNamed
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.resources.named
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope
import gov.nasa.jpl.pyre.general.resources.polynomial.IntegralResource
import gov.nasa.jpl.pyre.general.resources.polynomial.MutablePolynomialResource
import gov.nasa.jpl.pyre.general.resources.polynomial.Polynomial
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.general.units.Field
import gov.nasa.jpl.pyre.general.units.Ring
import gov.nasa.jpl.pyre.general.units.Scaling
import gov.nasa.jpl.pyre.general.units.Unit
import gov.nasa.jpl.pyre.general.units.UnitAware
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.map
import gov.nasa.jpl.pyre.general.units.polynomial_quantity.PolynomialRing
import gov.nasa.jpl.pyre.general.units.polynomial_quantity_resource.IntegralPolynomialResourceScaling
import gov.nasa.jpl.pyre.general.units.polynomial_quantity_resource.MutablePolynomialResourceScaling
import gov.nasa.jpl.pyre.general.units.polynomial_quantity_resource.PolynomialResourceRing
import gov.nasa.jpl.pyre.general.units.quantity.DoubleField
import gov.nasa.jpl.pyre.general.units.quantity_resource.DoubleResourceField
import gov.nasa.jpl.pyre.general.units.quantity_resource.MutableDoubleResourceScaling
import gov.nasa.jpl.pyre.kernel.Name
import kotlin.contracts.ExperimentalContracts
import kotlin.reflect.KType

object UnitAwareResourceOperations {
    /**
     * Construct a unit-aware mutable resource
     */
    context (_: InitScope)
    inline fun <V, reified D : Dynamics<V, D>> resource(name: String, initialDynamics: UnitAware<D>): UnitAware<MutableResource<D>> =
        initialDynamics.map { gov.nasa.jpl.pyre.foundation.resources.resource(name, it) }

    /**
     * Register a resource in a particular unit.
     * Note: The unit will be appended to the name of the resource automatically.
     */
    context (_: InitScope, _: Scaling<R>)
    fun <V, D : Dynamics<V, D>, R : Resource<D>> register(name: String, resource: UnitAware<R>, unit: Unit, dynamicsType: KType) =
        Reporting.register("$name ($unit)", resource.valueIn(unit), dynamicsType)

    /**
     * Register a resource in a particular unit.
     * Note: The unit will be appended to the name of the resource automatically.
     */
    context (_: InitScope, _: Scaling<R>)
    inline fun <V, reified D : Dynamics<V, D>, R : Resource<D>> register(name: String, resource: UnitAware<R>, unit: Unit) =
        Reporting.register("$name ($unit)", resource.valueIn(unit))

    /**
     * Register a resource in a particular unit.
     * Note: The unit will be appended to the name of the resource automatically.
     */
    context (_: InitScope, _: Scaling<R>)
    inline fun <V, reified D : Dynamics<V, D>, R : Resource<D>> register(resource: UnitAware<R>, unit: Unit) =
        // TODO: See if there's a way to clean up this naming rule.
        // It would be nice if this renaming rule were done in the "valueIn" method instead, somehow, so it applied to all conversions.
        Reporting.register(resource.valueIn(unit).named { "${resource.valueIn(resource.unit)} ($unit)" })

    /**
     * Get unit-aware full dynamics from a unit-aware resource.
     */
    context (_: ResourceScope)
    fun <V, D : Dynamics<V, D>> UnitAware<Resource<D>>.getDynamics(): UnitAware<FullDynamics<D>> = map { it.getDynamics() }

    /**
     * Get unit-aware value from a unit-aware resource.
     */
    context (_: ResourceScope)
    fun <V, D : Dynamics<V, D>> UnitAware<Resource<D>>.getValue(): UnitAware<V> = map { it.getValue() }

    context (_: SimulationScope)
    fun <D> UnitAware<Resource<D>>.named(nameFn: () -> String): UnitAware<Resource<D>> =
        map { it.named(nameFn) }

    fun <D> UnitAware<Resource<D>>.fullyNamed(nameFn: () -> Name): UnitAware<Resource<D>> =
        map { it.fullyNamed(nameFn) }

    /**
     * Convenience method for introducing all algebraic structures supporting "standard" unit-aware values and resources.
     */
    @OptIn(ExperimentalContracts::class)
    inline fun <R> unitAware(
        block: context (
            Field<Double>,
            Field<DoubleResource>,
            Scaling<MutableDoubleResource>,
            Ring<Polynomial>,
            Ring<PolynomialResource>,
            Scaling<MutablePolynomialResource>,
            Scaling<IntegralResource>,
        ) () -> R
    ): R {
        kotlin.contracts.contract {
            callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
        }
        return context(
            DoubleField,
            DoubleResourceField,
            MutableDoubleResourceScaling,
            PolynomialRing,
            PolynomialResourceRing,
            MutablePolynomialResourceScaling,
            IntegralPolynomialResourceScaling
        ) {
            block()
        }
    }
}

object MutableUnitAwareResourceOperations {
    context (_: Scaling<MutableResource<D>>)
    fun <D> UnitAware<MutableResource<D>>.named(nameFn: () -> String): UnitAware<MutableResource<D>> =
        UnitAware(valueIn(unit) named nameFn, unit)
}
