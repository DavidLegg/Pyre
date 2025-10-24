package gov.nasa.jpl.pyre.examples.scheduling.geometry.utils

import gov.nasa.jpl.pyre.general.units.QuantityOperations.valueIn
import gov.nasa.jpl.pyre.general.units.Quantity
import gov.nasa.jpl.pyre.general.units.Unit
import gov.nasa.jpl.pyre.general.units.UnitAware
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.general.units.VectorScope
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D

typealias QuantityVector = UnitAware<Vector3D>

object QuantityVectorOperations {
    operator fun QuantityVector.plus(other: QuantityVector): QuantityVector = with (Vector3DVectorScope) {
        with (UnitAware.Companion) {
            this@plus + other
        }
    }

    operator fun QuantityVector.minus(other: QuantityVector): QuantityVector = with (Vector3DVectorScope) {
        with (UnitAware.Companion) {
            this@minus - other
        }
    }

    operator fun QuantityVector.times(other: Double): QuantityVector = with (Vector3DVectorScope) {
        with (UnitAware.Companion) {
            this@times * other
        }
    }

    operator fun Double.times(other: QuantityVector): QuantityVector = with (Vector3DVectorScope) {
        with (UnitAware.Companion) {
            this@times * other
        }
    }

    operator fun Vector3D.times(other: Quantity): QuantityVector =
        this.scalarMultiply(other.valueIn(other.unit)) * other.unit

    operator fun Quantity.times(other: Vector3D): QuantityVector =
        other.scalarMultiply(this.valueIn(this.unit)) * this.unit

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    fun QuantityVector.valueIn(newUnit: Unit): Vector3D =
        with (Vector3DVectorScope) {
            this@valueIn.valueIn(newUnit)
        }

    object VsQuantity {
        operator fun QuantityVector.times(other: Quantity): QuantityVector = with (Vector3DVectorScope) {
            with (UnitAware.Companion.VsQuantity) {
                this@times * other
            }
        }
    }

    object QuantityVs {
        operator fun Quantity.times(other: QuantityVector): QuantityVector = with (Vector3DVectorScope) {
            with (UnitAware.Companion.QuantityVs) {
                this@times * other
            }
        }
    }
}

// Unsurprisingly, Vector3D forms a vector space over Double:
object Vector3DVectorScope : VectorScope<Vector3D> {
    override val zero: Vector3D = Vector3D.ZERO
    override fun Vector3D.plus(other: Vector3D): Vector3D = this.add(other)
    override fun Vector3D.minus(other: Vector3D): Vector3D = this.subtract(other)
    override fun Double.times(other: Vector3D): Vector3D = other.scalarMultiply(this)
}
