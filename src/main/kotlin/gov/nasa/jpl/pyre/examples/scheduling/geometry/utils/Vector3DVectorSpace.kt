package gov.nasa.jpl.pyre.examples.scheduling.geometry.utils

import gov.nasa.jpl.pyre.general.units.VectorSpace
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D

// Unsurprisingly, Vector3D forms a vector space over Double:
object Vector3DVectorSpace : VectorSpace<Vector3D> {
    override val zero: Vector3D = Vector3D.ZERO
    override fun Vector3D.plus(other: Vector3D): Vector3D = this.add(other)
    override fun Vector3D.minus(other: Vector3D): Vector3D = this.subtract(other)
    override fun Double.times(other: Vector3D): Vector3D = other.scalarMultiply(this)
}