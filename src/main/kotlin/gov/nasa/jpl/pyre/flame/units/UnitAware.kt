package gov.nasa.jpl.pyre.flame.units

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
                UnitAware(one / other.value, Unit.SCALAR / other.unit)
            }
        }

        infix fun <T> UnitAware<T>.named(nameFn: () -> String): UnitAware<T> = UnitAware(value, unit, nameFn)
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
