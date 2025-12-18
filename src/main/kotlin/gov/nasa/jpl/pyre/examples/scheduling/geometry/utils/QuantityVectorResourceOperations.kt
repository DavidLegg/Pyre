package gov.nasa.jpl.pyre.examples.scheduling.geometry.utils

import gov.nasa.jpl.pyre.utilities.InvertibleFunction
import gov.nasa.jpl.pyre.general.resources.lens.MutableResourceLens.view
import gov.nasa.jpl.pyre.general.units.Scaling
import gov.nasa.jpl.pyre.general.units.UnitAware
import gov.nasa.jpl.pyre.general.units.VectorSpace
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteMonad
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DoubleResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.general.resources.polynomial.IntegralResource
import gov.nasa.jpl.pyre.general.resources.polynomial.MutablePolynomialResource
import gov.nasa.jpl.pyre.general.resources.polynomial.Polynomial
import gov.nasa.jpl.pyre.general.resources.polynomial.PolynomialResource
import gov.nasa.jpl.pyre.general.units.Field
import gov.nasa.jpl.pyre.general.units.Ring
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.map
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D
import kotlin.contracts.ExperimentalContracts

typealias QuantityVectorResource = UnitAware<DiscreteResource<Vector3D>>
typealias MutableQuantityVectorResource = UnitAware<MutableDiscreteResource<Vector3D>>

object QuantityVectorResourceOperations {
    fun constant(vector: QuantityVector): QuantityVectorResource =
        vector.map(DiscreteResourceMonad::pure)

    context (scope: InitScope)
    fun quantityVectorResource(name: String, vector: QuantityVector): MutableQuantityVectorResource =
        vector.map { discreteResource(name, it) }
}

object MutableVectorResourceScaling : Scaling<MutableDiscreteResource<Vector3D>> {
    override fun Double.times(other: MutableDiscreteResource<Vector3D>): MutableDiscreteResource<Vector3D> {
        return other.view(InvertibleFunction.of(
            DiscreteMonad.map { it.scalarMultiply(this) },
            DiscreteMonad.map { it.scalarMultiply(1 / this) }))
    }
}

// Unsurprisingly, DiscreteResource<Vector3D> also forms a vector space over Double:
object VectorResourceVectorSpace : VectorSpace<DiscreteResource<Vector3D>> {
    override val zero: DiscreteResource<Vector3D> = pure(Vector3D.ZERO)
    override fun DiscreteResource<Vector3D>.plus(other: DiscreteResource<Vector3D>): DiscreteResource<Vector3D> =
        map(this, other, Vector3D::add)
    override fun DiscreteResource<Vector3D>.minus(other: DiscreteResource<Vector3D>): DiscreteResource<Vector3D> =
        map(this, other, Vector3D::subtract)
    override fun Double.times(other: DiscreteResource<Vector3D>): DiscreteResource<Vector3D> =
        map(other) { it.scalarMultiply(this) }
}

/**
 * Convenience method for introducing all algebraic structures introduced by
 * [gov.nasa.jpl.pyre.general.units.unit_aware_resource.UnitAwareResourceOperations.unitAware],
 * as well as those for [Vector3D].
 */
@OptIn(ExperimentalContracts::class)
inline fun <R> unitAware(
    block: context (
        Field<Double>,
        Field<DoubleResource>,
        Scaling<MutableDoubleResource>,
        Ring<Polynomial>,
        Ring<PolynomialResource>,
        Scaling<MutablePolynomialResource>,
        Scaling<IntegralResource>,
        VectorSpace<Vector3D>,
        VectorSpace<DiscreteResource<Vector3D>>,
        Scaling<MutableDiscreteResource<Vector3D>>,
    ) () -> R
): R {
    kotlin.contracts.contract {
        callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    return gov.nasa.jpl.pyre.general.units.unit_aware_resource.UnitAwareResourceOperations.unitAware {
        context (Vector3DVectorSpace, VectorResourceVectorSpace, MutableVectorResourceScaling) {
            block()
        }
    }
}
