package gov.nasa.jpl.pyre.flame.units

import gov.nasa.jpl.pyre.spark.reporting.Reporting
import gov.nasa.jpl.pyre.spark.resources.Dynamics
import gov.nasa.jpl.pyre.spark.resources.Resource
import gov.nasa.jpl.pyre.spark.tasks.InitScope
import kotlin.reflect.KType

class UnitAware<out T>(
    private val value: T,
    val unit: Unit,
    private val nameFn: (() -> String)? = null,
) {
    // To get a value, you *must* specify a unit.
    // This should reduce the incidence of people blindly getting the value without considering the units.
    context (scope: ScalableScope<T>)
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

    override fun toString(): String = if (nameFn != null) nameFn() else "$value $unit"

    companion object {
        // Natural-feeling constructor for unit-aware things: multiply the thing by a unit, a la "5 * METER"
        operator fun <T> T.times(unit: Unit): UnitAware<T> = UnitAware(this, unit)

        context (scope: VectorScope<T>)
        operator fun <T> UnitAware<T>.plus(other: UnitAware<T>): UnitAware<T> {
            val otherValue = other.valueIn(unit)
            return with (scope) {
                UnitAware(value + otherValue, unit)
            }
        }

        context (scope: VectorScope<T>)
        operator fun <T> UnitAware<T>.minus(other: UnitAware<T>): UnitAware<T> {
            val otherValue = other.valueIn(unit)
            return with (scope) {
                UnitAware(value - otherValue, unit)
            }
        }

        context (scope: VectorScope<T>)
        operator fun <T> UnitAware<T>.times(scale: Double): UnitAware<T> {
            return with (scope) {
                UnitAware(scale * value, unit)
            }
        }

        context (scope: VectorScope<T>)
        operator fun <T> Double.times(other: UnitAware<T>): UnitAware<T> = other * this

        context (scope: VectorScope<T>)
        operator fun <T> UnitAware<T>.div(scale: Double): UnitAware<T> {
            return with (scope) {
                UnitAware((1.0 / scale) * value, unit)
            }
        }

        context (scope: RingScope<T>)
        operator fun <T> UnitAware<T>.times(other: UnitAware<T>): UnitAware<T> {
            return with (scope) {
                UnitAware(value * other.value, unit * other.unit)
            }
        }

        context (scope: FieldScope<T>)
        operator fun <T> UnitAware<T>.div(other: UnitAware<T>): UnitAware<T> {
            return with (scope) {
                UnitAware(value / other.value, unit / other.unit)
            }
        }

        context (scope: FieldScope<T>)
        operator fun <T> Double.div(other: UnitAware<T>): UnitAware<T> {
            return with (scope) {
                UnitAware(this@div * one / other.value, Unit.SCALAR / other.unit)
            }
        }

        context (scope: ScalableScope<T>)
        fun <T : Comparable<T>> UnitAware<T>.compareTo(other: UnitAware<T>): Int =
            value.compareTo(other.valueIn(unit))

        /**
         * Register a resource in a particular unit.
         * Note: The unit will be appended to the name of the resource automatically.
         */
        context (_: InitScope, scope: ScalableScope<Resource<D>>)
        fun <V, D : Dynamics<V, D>> register(name: String, resource: UnitAware<Resource<D>>, unit: Unit, dynamicsType: KType) =
            Reporting.register("$name ($unit)", resource.valueIn(unit), dynamicsType)

        /**
         * Register a resource in a particular unit.
         * Note: The unit will be appended to the name of the resource automatically.
         */
        context (_: InitScope, scope: ScalableScope<Resource<D>>)
        inline fun <V, reified D : Dynamics<V, D>> register(name: String, resource: UnitAware<Resource<D>>, unit: Unit) =
            Reporting.register("$name ($unit)", resource.valueIn(unit))

        /**
         * Register a resource in a particular unit.
         * Note: The unit will be appended to the name of the resource automatically.
         */
        context (_: InitScope, scope: ScalableScope<Resource<D>>)
        inline fun <V, reified D : Dynamics<V, D>> register(resource: UnitAware<Resource<D>>, unit: Unit) =
            Reporting.register(resource.valueIn(unit))

        infix fun <T> UnitAware<T>.named(nameFn: () -> String): UnitAware<T> = UnitAware(value, unit, nameFn)

        /**
         * Operations involving a general UnitAware and a Quantity (in that order).
         *
         * These operations are split into a separate object to avoid JVM declaration conflicts.
         */
        object VsQuantity {
            context (scope: VectorScope<T>)
            operator fun <T> UnitAware<T>.times(other: Quantity): UnitAware<T> {
                return with (scope) {
                    UnitAware(other.value * value, unit * other.unit)
                }
            }

            context (scope: VectorScope<T>)
            operator fun <T> UnitAware<T>.div(other: Quantity): UnitAware<T> {
                return with (scope) {
                    UnitAware((1.0 / other.value) * value, unit / other.unit)
                }
            }
        }

        /**
         * Operations involving a Quantity and general UnitAware (in that order).
         *
         * These operations are split into a separate object to avoid JVM declaration conflicts.
         */
        object QuantityVs {
            context (scope: VectorScope<T>)
            operator fun <T> Quantity.times(other: UnitAware<T>): UnitAware<T> {
                return with (scope) {
                    UnitAware(value * other.value, unit * other.unit)
                }
            }

            context (scope: FieldScope<T>)
            operator fun <T> Quantity.div(other: UnitAware<T>): UnitAware<T> {
                return with (scope) {
                    UnitAware(value * one / other.value, unit / other.unit)
                }
            }
        }
    }
}

interface ScalableScope<T> {
    operator fun Double.times(other: T): T
}

interface VectorScope<T> : ScalableScope<T> {
    val zero: T
    operator fun T.plus(other: T): T
    operator fun T.minus(other: T): T
}

interface RingScope<T> : VectorScope<T> {
    val one: T
    operator fun T.times(other: T): T
}

interface FieldScope<T> : RingScope<T> {
    operator fun T.div(other: T): T
}
