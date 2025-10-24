package gov.nasa.jpl.pyre.flame.units

import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.ratioOver
import gov.nasa.jpl.pyre.kernel.roundTimes
import gov.nasa.jpl.pyre.flame.units.StandardUnits.RADIAN
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.times
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan


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

    fun abs(x: Quantity): Quantity = UnitAware(kotlin.math.abs(x.valueIn(x.unit)), x.unit)

    /**
     * Variation of [kotlin.math.sin] accepting a [Quantity] in units of Angle
     */
    fun sin(x: Quantity): Double = sin(x.valueIn(RADIAN))

    /**
     * Variation of [kotlin.math.cos] accepting a [Quantity] in units of Angle
     */
    fun cos(x: Quantity): Double = cos(x.valueIn(RADIAN))

    /**
     * Variation of [kotlin.math.tan] accepting a [Quantity] in units of Angle
     */
    fun tan(x: Quantity): Double = tan(x.valueIn(RADIAN))

    /**
     * Functions to provide smooth interoperability between [Duration] and [Quantity]
     */
    object DurationQuantityOperations {
        fun Duration.asQuantity(): Quantity = (this ratioOver Duration.SECOND) * StandardUnits.SECOND
        fun Quantity.asDuration(): Duration = this.valueIn(StandardUnits.SECOND) roundTimes Duration.SECOND
    }
}