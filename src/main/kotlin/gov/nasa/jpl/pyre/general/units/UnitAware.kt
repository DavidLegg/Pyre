package gov.nasa.jpl.pyre.general.units

import gov.nasa.jpl.pyre.foundation.resources.Resource
import gov.nasa.jpl.pyre.general.units.quantity.Quantity
import gov.nasa.jpl.pyre.general.units.unit_aware_resource.UnitAwareResourceOperations.registered

class UnitAware<T>(
    private val value: T,
    val unit: Unit,
) {
    // To get a value, you *must* specify a unit.
    // This should reduce the incidence of people blindly getting the value without considering the units.
    context (scope: Scaling<T>)
    fun valueIn(newUnit: Unit): T {
        // Short-circuit for efficiency
        if (unit === newUnit) return value
        require(unit.dimension == newUnit.dimension) {
            "Dimension mismatch: $unit cannot be converted to $newUnit" +
                    " (${unit.dimension} is not ${newUnit.dimension})"
        }
        return with(scope) {
            (unit.scale / newUnit.scale) * value
        }
    }

    override fun toString(): String = "$value $unit"

    companion object {
        // Natural-feeling constructor for unit-aware things: multiply the thing by a unit, a la "5 * METER"
        operator fun <T> T.times(unit: Unit): UnitAware<T> = UnitAware(this, unit)

        // Unit-stacking constructors - these avoid needing to group your units with parens
        operator fun <T> UnitAware<T>.times(unit: Unit): UnitAware<T> = UnitAware(value, this.unit * unit)
        operator fun <T> UnitAware<T>.div(unit: Unit): UnitAware<T> = UnitAware(value, this.unit / unit)

        // Pass-through unit-independent mapping operation
        fun <T, S> UnitAware<T>.map(f: (T) -> S): UnitAware<S> = UnitAware(f(value), unit)

        // TODO: See if we can re-work UnitAware's type parameter to be "out" (covariant) and get rid of upcast.
        /** Up-cast this to a [UnitAware] around a more generic type */
        fun <T, S : T> UnitAware<S>.upcast(): UnitAware<T> = map { it }

        context (scope: VectorSpace<T>)
        operator fun <T> UnitAware<T>.plus(other: UnitAware<T>): UnitAware<T> {
            val otherValue = other.valueIn(unit)
            return with (scope) {
                UnitAware(value + otherValue, unit)
            }
        }

        context (scope: VectorSpace<T>)
        operator fun <T> UnitAware<T>.minus(other: UnitAware<T>): UnitAware<T> {
            val otherValue = other.valueIn(unit)
            return with (scope) {
                UnitAware(value - otherValue, unit)
            }
        }

        context (scope: VectorSpace<T>)
        operator fun <T> UnitAware<T>.times(scale: Double): UnitAware<T> {
            return with (scope) {
                UnitAware(scale * value, unit)
            }
        }

        context (scope: VectorSpace<T>)
        operator fun <T> Double.times(other: UnitAware<T>): UnitAware<T> = other * this

        context (scope: VectorSpace<T>)
        operator fun <T> UnitAware<T>.div(scale: Double): UnitAware<T> {
            return with (scope) {
                UnitAware((1.0 / scale) * value, unit)
            }
        }

        context (scope: Ring<T>)
        operator fun <T> UnitAware<T>.times(other: UnitAware<T>): UnitAware<T> {
            return with (scope) {
                UnitAware(value * other.value, unit * other.unit)
            }
        }

        context (scope: Field<T>)
        operator fun <T> UnitAware<T>.div(other: UnitAware<T>): UnitAware<T> {
            return with (scope) {
                UnitAware(value / other.value, unit / other.unit)
            }
        }

        context (scope: Field<T>)
        operator fun <T> Double.div(other: UnitAware<T>): UnitAware<T> {
            return with (scope) {
                UnitAware(this@div * one / other.value, Unit.SCALAR / other.unit)
            }
        }

        context (scope: Scaling<T>)
        fun <T : Comparable<T>> UnitAware<T>.compareTo(other: UnitAware<T>): Int =
            value.compareTo(other.valueIn(unit))

        /**
         * Operations involving a general UnitAware and a Quantity (in that order).
         *
         * These operations are split into a separate object to avoid JVM declaration conflicts.
         */
        object VsQuantity {
            // Adding a Quantity to a general UnitAware<T> may look strange, but requiring a Ring<T> makes it reasonable.
            // In short, if 1_T is the multiplicative unit of T, then "multiplication by 1_T" is a field isomorphism from
            // Double to (Double * 1_T) which realizes Double as a field within T.

            // Put another way, you can only define Ring<T> with scaling over Double if Double has a natural
            // embedding within T, hence adding a Double and a T makes sense under normal ring addition.

            // For example, it makes sense to add a Double and a Polynomial because Doubles can be seen
            // naturally as degree-0 polynomials.

            context (scope: Ring<T>)
            operator fun <T> UnitAware<T>.plus(other: Quantity): UnitAware<T> = with (scope) {
                this@plus + UnitAware(other.value * one, other.unit)
            }

            context (scope: Ring<T>)
            operator fun <T> UnitAware<T>.minus(other: Quantity): UnitAware<T> = with (scope) {
                this@minus - UnitAware(other.value * one, other.unit)
            }

            // Contrasted with adding and subtracting a Quantity and general UnitAware,
            // that additional subfield structure is not required for multiplying and dividing by an arbitrary Quantity.
            // Hence T is required to be a VectorSpace only, not a Ring.

            context (scope: VectorSpace<T>)
            operator fun <T> UnitAware<T>.times(other: Quantity): UnitAware<T> = with(scope) {
                UnitAware(other.value * value, unit * other.unit)
            }

            context (scope: VectorSpace<T>)
            operator fun <T> UnitAware<T>.div(other: Quantity): UnitAware<T> = with(scope) {
                UnitAware((1.0 / other.value) * value, unit / other.unit)
            }

            // By the same reasoning as for plus and minus, if and only if T is a Ring can we compare directly to Quantity.

            context (scope: Ring<T>)
            fun <T: Comparable<T>> UnitAware<T>.compareTo(other: Quantity): Int = with (scope) {
                this@compareTo.compareTo(UnitAware(other.value * one, other.unit))
            }
        }

        /**
         * Operations involving a Quantity and general UnitAware (in that order).
         *
         * These operations are split into a separate object to avoid JVM declaration conflicts.
         */
        object QuantityVs {
            context (scope: Ring<T>)
            operator fun <T> Quantity.plus(other: UnitAware<T>): UnitAware<T> = with (scope) {
                UnitAware(value * one, unit) + other
            }

            context (scope: Ring<T>)
            operator fun <T> Quantity.minus(other: UnitAware<T>): UnitAware<T> = with (scope) {
                UnitAware(value * one, unit) - other
            }

            context (scope: VectorSpace<T>)
            operator fun <T> Quantity.times(other: UnitAware<T>): UnitAware<T> = with(scope) {
                UnitAware(value * other.value, unit * other.unit)
            }

            context (scope: Field<T>)
            operator fun <T> Quantity.div(other: UnitAware<T>): UnitAware<T> = with(scope) {
                UnitAware(value * one / other.value, unit / other.unit)
            }

            context (scope: Ring<T>)
            fun <T : Comparable<T>> Quantity.compareTo(other: UnitAware<T>): Int = with (scope) {
                UnitAware(value * one, unit).compareTo(other)
            }
        }

        // Needs to be done in the Companion object to access value, rather than requiring additional scopes for conversion.
        val <D> UnitAware<out Resource<D>>.name get() = value.name
    }
}

