package gov.nasa.jpl.pyre.general.units.polynomial_quantity_resource

import gov.nasa.jpl.pyre.general.units.quantity_resource.QuantityResource
import gov.nasa.jpl.pyre.general.resources.polynomial.IntegralResource
import gov.nasa.jpl.pyre.general.resources.polynomial.MutablePolynomialResource
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.asPolynomial
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.decrease
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.greaterThan
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.greaterThanOrEquals
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.increase
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.lessThan
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.lessThanOrEquals
import gov.nasa.jpl.pyre.general.units.quantity.DoubleField
import gov.nasa.jpl.pyre.general.units.quantity.Quantity
import gov.nasa.jpl.pyre.general.units.StandardUnits.SECOND
import gov.nasa.jpl.pyre.general.units.UnitAware
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.clamp
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.clampedIntegral
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.derivative
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResourceOperations.integral
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.map
import gov.nasa.jpl.pyre.general.units.polynomial_quantity.PolynomialQuantityOperations
import gov.nasa.jpl.pyre.general.units.unit_aware_resource.UnitAwareResourceOperations.resource

typealias PolynomialQuantityResource = UnitAware<PolynomialResource>
typealias MutablePolynomialQuantityResource = UnitAware<MutablePolynomialResource>
typealias IntegralQuantityResource = UnitAware<IntegralResource>

object PolynomialQuantityResourceOperations {
    fun constant(quantity: Quantity): PolynomialQuantityResource =
        quantity.map(PolynomialResourceOperations::constant)

    // Mutable resource constructor
    /**
     * Construct a unit-aware resource using unit-aware [coefficients]
     */
    context (scope: InitScope)
    fun quantityResource(name: String, vararg coefficients: Quantity): MutablePolynomialQuantityResource =
        resource(name, PolynomialQuantityOperations.polynomial(*coefficients))

    // Note: Units can be applied to a derived resource using the generic T * Unit operator.

    fun QuantityResource.asPolynomial(): PolynomialQuantityResource =
        map { it.asPolynomial() }

    fun PolynomialQuantityResource.derivative(): PolynomialQuantityResource =
        context (PolynomialResourceRing) {
            UnitAware(valueIn(unit).derivative(), unit / SECOND)
        }

    context (scope: InitScope)
    fun PolynomialQuantityResource.integral(name: String, startingValue: Quantity): IntegralQuantityResource =
        context (DoubleField, PolynomialResourceRing) {
            UnitAware(
                valueIn(startingValue.unit / SECOND)
                    .integral(name, startingValue.valueIn(startingValue.unit)),
                startingValue.unit)
        }

    context (scope: InitScope)
    fun PolynomialQuantityResource.clampedIntegral(
        name: String,
        lowerBound: PolynomialQuantityResource,
        upperBound: PolynomialQuantityResource,
        startingValue: Quantity,
    ): ClampedQuantityIntegralResult = context (DoubleField, PolynomialResourceRing, IntegralPolynomialResourceScaling) {
        val unit = startingValue.unit
        val scalarResult = valueIn(unit / SECOND).clampedIntegral(
            name,
            lowerBound.valueIn(unit),
            upperBound.valueIn(unit),
            startingValue.valueIn(unit))
        return ClampedQuantityIntegralResult(
            UnitAware(scalarResult.integral, unit),
            UnitAware(scalarResult.overflow, unit / SECOND),
            UnitAware(scalarResult.underflow, unit / SECOND))
    }

    data class ClampedQuantityIntegralResult(
        val integral: PolynomialQuantityResource,
        val overflow: PolynomialQuantityResource,
        val underflow: PolynomialQuantityResource,
    )

    infix fun PolynomialQuantityResource.greaterThan(other: PolynomialQuantityResource): BooleanResource =
        context (PolynomialResourceRing) {
            valueIn(unit) greaterThan other.valueIn(unit)
        }
    infix fun PolynomialQuantityResource.greaterThanOrEquals(other: PolynomialQuantityResource): BooleanResource =
        context (PolynomialResourceRing) {
            valueIn(unit) greaterThanOrEquals other.valueIn(unit)
        }
    infix fun PolynomialQuantityResource.lessThan(other: PolynomialQuantityResource): BooleanResource =
        context (PolynomialResourceRing) {
            valueIn(unit) lessThan other.valueIn(unit)
        }
    infix fun PolynomialQuantityResource.lessThanOrEquals(other: PolynomialQuantityResource): BooleanResource =
        context (PolynomialResourceRing) {
            valueIn(unit) lessThanOrEquals other.valueIn(unit)
        }

    fun min(p: PolynomialQuantityResource, q: PolynomialQuantityResource): PolynomialQuantityResource =
        context (PolynomialResourceRing) {
            UnitAware(PolynomialResourceOperations.min(p.valueIn(p.unit), q.valueIn(p.unit)), p.unit)
        }

    fun max(p: PolynomialQuantityResource, q: PolynomialQuantityResource): PolynomialQuantityResource =
        context (PolynomialResourceRing) {
            UnitAware(PolynomialResourceOperations.max(p.valueIn(p.unit), q.valueIn(p.unit)), p.unit)
        }

    fun PolynomialQuantityResource.clamp(lowerBound: PolynomialQuantityResource, upperBound: PolynomialQuantityResource): PolynomialQuantityResource =
        context (PolynomialResourceRing) {
            UnitAware(valueIn(unit).clamp(lowerBound.valueIn(unit), upperBound.valueIn(unit)), unit)
        }

    context (scope: TaskScope)
    suspend fun MutablePolynomialQuantityResource.increase(amount: Quantity) =
        context (DoubleField, MutablePolynomialResourceScaling) {
            valueIn(unit).increase(amount.valueIn(unit))
        }

    context (scope: TaskScope)
    suspend fun MutablePolynomialQuantityResource.decrease(amount: Quantity) =
        context (DoubleField, MutablePolynomialResourceScaling) {
            valueIn(unit).decrease(amount.valueIn(unit))
        }

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
