package gov.nasa.jpl.pyre.general.units.quantity

import gov.nasa.jpl.pyre.general.units.StandardUnits
import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.kernel.ratioOver
import gov.nasa.jpl.pyre.kernel.roundTimes
import gov.nasa.jpl.pyre.general.units.StandardUnits.RADIAN
import gov.nasa.jpl.pyre.general.units.UnitAware
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.plus
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.minus
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.div
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan


typealias Quantity = UnitAware<Double>

/**
 * Additional operations allowed on [Quantity], beyond those permitted on a general [UnitAware].
 */
object QuantityOperations {
    // We need overloads for Quantity-Quantity binops, because otherwise multiple methods compete to implement this operation.
    // Giving a type parameter forces it to resolve to UnitAware<T> binops

    operator fun Quantity.plus(other: Quantity): Quantity = context(DoubleField) {
        this@plus.plus<Double>(other)
    }

    operator fun Quantity.minus(other: Quantity): Quantity = context(DoubleField) {
        this@minus.minus<Double>(other)
    }

    operator fun Quantity.times(other: Quantity): Quantity = context(DoubleField) {
        this@times.times<Double>(other)
    }

    operator fun Quantity.div(other: Quantity): Quantity = context(DoubleField) {
        this@div.div<Double>(other)
    }

    fun abs(x: Quantity): Quantity = context (DoubleField) {
        UnitAware(kotlin.math.abs(x.valueIn(x.unit)), x.unit)
    }

    /**
     * Variation of [kotlin.math.sin] accepting a [Quantity] in units of Angle
     */
    fun sin(x: Quantity): Double = context (DoubleField) {
        sin(x.valueIn(RADIAN))
    }

    /**
     * Variation of [kotlin.math.cos] accepting a [Quantity] in units of Angle
     */
    fun cos(x: Quantity): Double = context (DoubleField) {
        cos(x.valueIn(RADIAN))
    }

    /**
     * Variation of [kotlin.math.tan] accepting a [Quantity] in units of Angle
     */
    fun tan(x: Quantity): Double = context (DoubleField) {
        tan(x.valueIn(RADIAN))
    }

    fun Duration.asQuantity(): Quantity = (this ratioOver Duration.SECOND) * StandardUnits.SECOND
    fun Quantity.asDuration(): Duration = context (DoubleField) {
        this.valueIn(StandardUnits.SECOND) roundTimes Duration.SECOND
    }
}