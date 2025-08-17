package gov.nasa.jpl.pyre.flame.units


typealias Quantity = UnitAware<Double>

/**
 * Specialization of [UnitAware] operations to [Quantity], baking [DoubleFieldScope] in implicitly.
 */
object QuantityOperations {
    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    fun Quantity.valueIn(newUnit: Unit): Double = with (DoubleFieldScope) {
        this@valueIn.valueIn(newUnit)
    }

    operator fun Quantity.plus(other: Quantity): Quantity = with (DoubleFieldScope) {
        with (UnitAware.Companion) {
            this@plus + other
        }
    }

    operator fun Quantity.minus(other: Quantity): Quantity = with (DoubleFieldScope) {
        with (UnitAware.Companion) {
            this@minus - other
        }
    }

    operator fun Quantity.times(other: Quantity): Quantity = with (DoubleFieldScope) {
        with (UnitAware.Companion) {
            this@times * other
        }
    }

    operator fun Quantity.times(other: Double): Quantity = with (DoubleFieldScope) {
        with (UnitAware.Companion) {
            this@times * other
        }
    }

    operator fun Double.times(other: Quantity): Quantity = with (DoubleFieldScope) {
        with (UnitAware.Companion) {
            this@times * other
        }
    }

    operator fun Quantity.div(other: Quantity): Quantity = with (DoubleFieldScope) {
        with (UnitAware.Companion) {
            this@div / other
        }
    }

    operator fun Quantity.div(other: Double): Quantity = with (DoubleFieldScope) {
        with (UnitAware.Companion) {
            this@div / other
        }
    }

    operator fun Double.div(other: Quantity): Quantity = with (DoubleFieldScope) {
        with (UnitAware.Companion) {
            this@div / other
        }
    }

    fun Quantity.compareTo(other: Quantity): Int = with (DoubleFieldScope) {
        with (UnitAware.Companion) {
            this@compareTo.compareTo(other)
        }
    }
}