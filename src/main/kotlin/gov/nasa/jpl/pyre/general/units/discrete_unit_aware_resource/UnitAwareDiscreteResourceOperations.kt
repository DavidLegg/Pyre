package gov.nasa.jpl.pyre.general.units.discrete_unit_aware_resource

import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.equals
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.greaterThan
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.greaterThanOrEquals
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.lessThan
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.lessThanOrEquals
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.notEquals
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.general.units.Scaling
import gov.nasa.jpl.pyre.general.units.UnitAware
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.map

typealias UnitAwareDiscreteResource<T> = UnitAware<DiscreteResource<T>>
typealias MutableUnitAwareDiscreteResource<T> = UnitAware<MutableDiscreteResource<T>>

object UnitAwareDiscreteResourceOperations {
    // constructors

    fun <T> constant(quantity: UnitAware<T>): UnitAwareDiscreteResource<T> =
        quantity.map(DiscreteResourceMonad::pure)

    context (_: InitScope)
    inline fun <reified T> discreteResource(name: String, value: UnitAware<T>): MutableUnitAwareDiscreteResource<T> =
        value.map { discreteResource(name, it) }

    // comparators

    context (_: Scaling<DiscreteResource<T>>)
    infix fun <T : Comparable<T>> UnitAwareDiscreteResource<T>.greaterThan(other: UnitAwareDiscreteResource<T>): BooleanResource =
        valueIn(unit) greaterThan other.valueIn(unit)

    context (_: Scaling<DiscreteResource<T>>)
    infix fun <T : Comparable<T>> UnitAwareDiscreteResource<T>.greaterThanOrEquals(other: UnitAwareDiscreteResource<T>): BooleanResource =
        valueIn(unit) greaterThanOrEquals other.valueIn(unit)

    context (_: Scaling<DiscreteResource<T>>)
    infix fun <T : Comparable<T>> UnitAwareDiscreteResource<T>.lessThan(other: UnitAwareDiscreteResource<T>): BooleanResource =
        valueIn(unit) lessThan other.valueIn(unit)

    context (_: Scaling<DiscreteResource<T>>)
    infix fun <T : Comparable<T>> UnitAwareDiscreteResource<T>.lessThanOrEquals(other: UnitAwareDiscreteResource<T>): BooleanResource =
        valueIn(unit) lessThanOrEquals other.valueIn(unit)

    context (_: Scaling<DiscreteResource<T>>)
    infix fun <T> UnitAwareDiscreteResource<T>.equals(other: UnitAwareDiscreteResource<T>): BooleanResource =
        valueIn(unit) equals other.valueIn(unit)

    context (_: Scaling<DiscreteResource<T>>)
    infix fun <T> UnitAwareDiscreteResource<T>.notEquals(other: UnitAwareDiscreteResource<T>): BooleanResource =
        valueIn(unit) notEquals other.valueIn(unit)

    // TODO: min/max/clamp?

    // effects

    context (_: TaskScope, _: Scaling<MutableDiscreteResource<T>>, _: Scaling<T>)
    fun <T> MutableUnitAwareDiscreteResource<T>.set(quantity: UnitAware<T>) =
        valueIn(unit).set(quantity.valueIn(unit))

    object VsScalar {
        context (_: Scaling<DiscreteResource<T>>)
        infix fun <T : Comparable<T>> UnitAwareDiscreteResource<T>.greaterThan(other: UnitAware<T>): BooleanResource =
            this greaterThan constant(other)

        context (_: Scaling<DiscreteResource<T>>)
        infix fun <T : Comparable<T>> UnitAwareDiscreteResource<T>.greaterThanOrEquals(other: UnitAware<T>): BooleanResource =
            this greaterThanOrEquals constant(other)

        context (_: Scaling<DiscreteResource<T>>)
        infix fun <T : Comparable<T>> UnitAwareDiscreteResource<T>.lessThan(other: UnitAware<T>): BooleanResource =
            this lessThan constant(other)

        context (_: Scaling<DiscreteResource<T>>)
        infix fun <T : Comparable<T>> UnitAwareDiscreteResource<T>.lessThanOrEquals(other: UnitAware<T>): BooleanResource =
            this lessThanOrEquals constant(other)

        context (_: Scaling<DiscreteResource<T>>)
        infix fun <T> UnitAwareDiscreteResource<T>.equals(other: UnitAware<T>): BooleanResource =
            this equals constant(other)

        context (_: Scaling<DiscreteResource<T>>)
        infix fun <T> UnitAwareDiscreteResource<T>.notEquals(other: UnitAware<T>): BooleanResource =
            this notEquals constant(other)
    }

    object ScalarVs {
        context (_: Scaling<DiscreteResource<T>>)
        infix fun <T : Comparable<T>> UnitAware<T>.greaterThan(other: UnitAwareDiscreteResource<T>): BooleanResource =
            constant(this) greaterThan other

        context (_: Scaling<DiscreteResource<T>>)
        infix fun <T : Comparable<T>> UnitAware<T>.greaterThanOrEquals(other: UnitAwareDiscreteResource<T>): BooleanResource =
            constant(this) greaterThanOrEquals other

        context (_: Scaling<DiscreteResource<T>>)
        infix fun <T : Comparable<T>> UnitAware<T>.lessThan(other: UnitAwareDiscreteResource<T>): BooleanResource =
            constant(this) lessThan other

        context (_: Scaling<DiscreteResource<T>>)
        infix fun <T : Comparable<T>> UnitAware<T>.lessThanOrEquals(other: UnitAwareDiscreteResource<T>): BooleanResource =
            constant(this) lessThanOrEquals other

        context (_: Scaling<DiscreteResource<T>>)
        infix fun <T> UnitAware<T>.equals(other: UnitAwareDiscreteResource<T>): BooleanResource =
            constant(this) equals other

        context (_: Scaling<DiscreteResource<T>>)
        infix fun <T> UnitAware<T>.notEquals(other: UnitAwareDiscreteResource<T>): BooleanResource =
            constant(this) notEquals other
    }
}