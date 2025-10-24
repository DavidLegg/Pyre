package gov.nasa.jpl.pyre.examples.scheduling.geometry.utils

import gov.nasa.jpl.pyre.utilities.InvertibleFunction
import gov.nasa.jpl.pyre.flame.resources.lens.MutableResourceLens.view
import gov.nasa.jpl.pyre.flame.units.Quantity
import gov.nasa.jpl.pyre.flame.units.ScalableScope
import gov.nasa.jpl.pyre.flame.units.Unit
import gov.nasa.jpl.pyre.flame.units.UnitAware
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.named
import gov.nasa.jpl.pyre.flame.units.VectorScope
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteMonad
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.resources.named
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D

typealias QuantityVectorResource = UnitAware<DiscreteResource<Vector3D>>
typealias MutableQuantityVectorResource = UnitAware<MutableDiscreteResource<Vector3D>>

object QuantityVectorResourceOperations {
    fun constant(vector: QuantityVector): QuantityVectorResource = with (Vector3DVectorScope) {
        UnitAware(pure(vector.valueIn(vector.unit)), vector.unit) named { vector.toString() }
    }

    context (scope: InitScope)
    fun quantityVectorResource(name: String, vector: QuantityVector): MutableQuantityVectorResource = with (Vector3DVectorScope) {
        UnitAware(discreteResource(name, vector.valueIn(vector.unit)), vector.unit) { name }
    }

    context (scope: InitScope)
    fun registeredQuantityVectorResource(name: String, vector: QuantityVector): MutableQuantityVectorResource =
        quantityVectorResource(name, vector).also { register(it, vector.unit) }

    context (scope: InitScope)
    fun register(name: String, resource: QuantityVectorResource, unit: Unit) = with (VectorResourceVectorScope) {
        UnitAware.register(name, resource, unit)
    }

    context (scope: InitScope)
    fun register(resource: QuantityVectorResource, unit: Unit) = register(resource.toString(), resource, unit)

    context (scope: ResourceScope)
    suspend fun QuantityVectorResource.getValue(): QuantityVector =
        UnitAware(valueIn(unit).getValue(), unit)

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    fun MutableQuantityVectorResource.valueIn(newUnit: Unit): MutableDiscreteResource<Vector3D> =
        with (MutableVectorResourceScaling) {
            this@valueIn.valueIn(newUnit) named this@valueIn::toString
        }

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    fun QuantityVectorResource.valueIn(newUnit: Unit): DiscreteResource<Vector3D> =
        with (VectorResourceVectorScope) {
            this@valueIn.valueIn(newUnit) named this@valueIn::toString
        }

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    context (scope: TaskScope)
    suspend fun MutableQuantityVectorResource.set(value: QuantityVector) =
        with (Vector3DVectorScope) {
            this@set.valueIn(this@set.unit).set(value.valueIn(this@set.unit))
        }

    operator fun QuantityVectorResource.plus(other: QuantityVectorResource): QuantityVectorResource = with (VectorResourceVectorScope) {
        with (UnitAware.Companion) {
            this@plus + other
        }
    }

    operator fun QuantityVectorResource.minus(other: QuantityVectorResource): QuantityVectorResource = with (VectorResourceVectorScope) {
        with (UnitAware.Companion) {
            this@minus - other
        }
    }

    operator fun QuantityVectorResource.times(other: Double): QuantityVectorResource = with (VectorResourceVectorScope) {
        with (UnitAware.Companion) {
            this@times * other
        }
    }

    operator fun Double.times(other: QuantityVectorResource): QuantityVectorResource = with (VectorResourceVectorScope) {
        with (UnitAware.Companion) {
            this@times * other
        }
    }

    object VsQuantity {
        operator fun QuantityVectorResource.times(other: Quantity): QuantityVectorResource = with (VectorResourceVectorScope) {
            with (UnitAware.Companion.VsQuantity) {
                this@times * other
            }
        }
    }

    object QuantityVs {
        operator fun Quantity.times(other: QuantityVectorResource): QuantityVectorResource = with (VectorResourceVectorScope) {
            with (UnitAware.Companion.QuantityVs) {
                this@times * other
            }
        }
    }
}

object MutableVectorResourceScaling : ScalableScope<MutableDiscreteResource<Vector3D>> {
    override fun Double.times(other: MutableDiscreteResource<Vector3D>): MutableDiscreteResource<Vector3D> {
        return other.view(InvertibleFunction.of(
            DiscreteMonad.map { it.scalarMultiply(this) },
            DiscreteMonad.map { it.scalarMultiply(1 / this) }))
    }
}

// Unsurprisingly, DiscreteResource<Vector3D> also forms a vector space over Double:
object VectorResourceVectorScope : VectorScope<DiscreteResource<Vector3D>> {
    override val zero: DiscreteResource<Vector3D> = pure(Vector3D.ZERO)
    override fun DiscreteResource<Vector3D>.plus(other: DiscreteResource<Vector3D>): DiscreteResource<Vector3D> =
        map(this, other, Vector3D::add)
    override fun DiscreteResource<Vector3D>.minus(other: DiscreteResource<Vector3D>): DiscreteResource<Vector3D> =
        map(this, other, Vector3D::subtract)
    override fun Double.times(other: DiscreteResource<Vector3D>): DiscreteResource<Vector3D> =
        map(other) { it.scalarMultiply(this) }
}
