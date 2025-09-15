package gov.nasa.jpl.pyre.examples.scheduling.geometry.model

import gov.nasa.jpl.pyre.coals.named
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.ember.plus
import gov.nasa.jpl.pyre.ember.rem
import gov.nasa.jpl.pyre.examples.scheduling.geometry.utils.QuantityVector
import gov.nasa.jpl.pyre.examples.scheduling.geometry.model.GeometryModel.PointingTarget.*
import gov.nasa.jpl.pyre.examples.scheduling.geometry.utils.QuantityVectorOperations.minus
import gov.nasa.jpl.pyre.examples.scheduling.geometry.utils.QuantityVectorOperations.plus
import gov.nasa.jpl.pyre.examples.scheduling.geometry.utils.QuantityVectorOperations.times
import gov.nasa.jpl.pyre.examples.scheduling.geometry.utils.QuantityVectorOperations.valueIn
import gov.nasa.jpl.pyre.examples.scheduling.geometry.utils.QuantityVectorResource
import gov.nasa.jpl.pyre.examples.scheduling.geometry.utils.QuantityVectorResourceOperations.constant
import gov.nasa.jpl.pyre.examples.scheduling.geometry.utils.QuantityVectorResourceOperations.minus
import gov.nasa.jpl.pyre.examples.scheduling.geometry.utils.QuantityVectorResourceOperations.register
import gov.nasa.jpl.pyre.examples.scheduling.geometry.utils.QuantityVectorResourceOperations.valueIn
import gov.nasa.jpl.pyre.examples.scheduling.geometry.utils.Vector3DVectorScope.plus
import gov.nasa.jpl.pyre.examples.scheduling.geometry.utils.Vector3DVectorScope.times
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResource
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResourceOperations.DurationQuantityResourceOperations.asQuantity
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResourceOperations.VsQuantity.plus
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResourceOperations.VsQuantity.times
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResourceOperations.valueIn
import gov.nasa.jpl.pyre.flame.units.Quantity
import gov.nasa.jpl.pyre.flame.units.QuantityOperations.DurationQuantityOperations.asDuration
import gov.nasa.jpl.pyre.flame.units.QuantityOperations.div
import gov.nasa.jpl.pyre.flame.units.StandardUnits.KILOMETER
import gov.nasa.jpl.pyre.flame.units.Unit
import gov.nasa.jpl.pyre.flame.units.StandardUnits.DAY
import gov.nasa.jpl.pyre.flame.units.StandardUnits.DEGREE
import gov.nasa.jpl.pyre.flame.units.StandardUnits.RADIAN
import gov.nasa.jpl.pyre.flame.units.StandardUnits.ROTATION
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.named
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.spark.reporting.Reporting.register
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteDynamicsMonad
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.named
import gov.nasa.jpl.pyre.spark.tasks.InitScope
import gov.nasa.jpl.pyre.spark.tasks.InitScope.Companion.subContext
import gov.nasa.jpl.pyre.spark.tasks.Reactions.every
import kotlinx.serialization.Serializable
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D
import kotlin.math.cos
import kotlin.math.sin

val AU = Unit.derived("AU", 149_600_000.0 * KILOMETER)
// In a "real" model, we should be a lot more precise about what kind of year we mean.
// This is good enough for our demo, though.
val YEAR = Unit.derived("yr", 365.25 * DAY)

class GeometryModel(
    context: InitScope,
    config: Config,
) {
    data class Config(
        val geometrySamplePeriod: Duration
    )

    @Serializable
    enum class PointingTarget {
        J2000_POS_X,
        J2000_POS_Y,
        J2000_POS_Z,
        J2000_NEG_X,
        J2000_NEG_Y,
        J2000_NEG_Z,
        SUN,
        EARTH,
        MARS
    }

    val spacecraftPosition: QuantityVectorResource
    val earthPosition: QuantityVectorResource
    val marsPosition: QuantityVectorResource
    val sunPosition: QuantityVectorResource

    val distanceToEarth: QuantityResource
    val distanceToMars: QuantityResource
    val distanceToSun: QuantityResource

    val pointingDirection: Map<PointingTarget, DiscreteResource<Vector3D>>

    init {
        with (context) {
            // Example of a simple fixed-period discretely sampled resource.
            // We can get a lot more sophisticated than this, but this is fine for now.
            val clock = tickingClock("geometry_update_clock", config.geometrySamplePeriod)
            spacecraftPosition = orbitResource(
                2.828 * YEAR,
                30.0 * DEGREE,
                // Have the spacecraft orbit just a little bit out-of-plane, to keep things interesting
                // Notice also how our unit-awareness framework lets us write mixed-unit expressions, e.g. AU and km.
                2.0 * AU * Vector3D.PLUS_I + 1e7 * KILOMETER * Vector3D.PLUS_K,
                2.0 * AU * Vector3D.PLUS_J - 1e7 * KILOMETER * Vector3D.PLUS_K,
                clock,
            ) named { "spacecraft_position" }
            // Note how we can easily ask for the resource in whichever units we please:
            register(spacecraftPosition, KILOMETER)
            register(spacecraftPosition, AU)

            earthPosition = orbitResource(
                1.0 * YEAR,
                0.0 * DEGREE,
                1.0 * AU * Vector3D.PLUS_I,
                1.0 * AU * Vector3D.PLUS_J,
                clock,
            ) named { "earth_position" }
            register(earthPosition, KILOMETER)

            marsPosition = orbitResource(
                1.84 * YEAR,
                45.0 * DEGREE,
                1.5 * AU * Vector3D.PLUS_I,
                1.5 * AU * Vector3D.PLUS_J,
                clock
            ) named { "mars_position" }
            register(marsPosition, KILOMETER)

            sunPosition = constant(Vector3D.ZERO * AU) named { "sun_position" }
            register(sunPosition, KILOMETER)

            // For operations we haven't explicitly written as unit-aware, we always have the option of "locally" dealing with units.
            // This means asking for our inputs in a specific unit, performing the operation as a scalar operation
            // implicitly in that unit, and then applying the unit explicitly to the result.
            // We can verify this operation is unit-safe locally, just looking at this code.
            // We don't need to know how the position resources were defined, or what units they're in.
            distanceToEarth = map((spacecraftPosition - earthPosition).valueIn(AU)) { it.norm } * AU
            distanceToMars = map((spacecraftPosition - marsPosition).valueIn(AU)) { it.norm } * AU
            distanceToSun = map((spacecraftPosition - sunPosition).valueIn(AU)) { it.norm } * AU

            with (subContext("pointing_direction")) {
                pointingDirection = mapOf(
                    J2000_POS_X to pure(Vector3D.PLUS_I),
                    J2000_POS_Y to pure(Vector3D.PLUS_J),
                    J2000_POS_Z to pure(Vector3D.PLUS_K),
                    J2000_NEG_X to pure(Vector3D.MINUS_I),
                    J2000_NEG_Y to pure(Vector3D.MINUS_J),
                    J2000_NEG_Z to pure(Vector3D.MINUS_K),
                    SUN to map((sunPosition - spacecraftPosition).valueIn(AU), Vector3D::normalize),
                    EARTH to map((earthPosition - spacecraftPosition).valueIn(AU), Vector3D::normalize),
                    MARS to map((marsPosition - spacecraftPosition).valueIn(AU), Vector3D::normalize),
                ).mapValues { (target, resource) -> (resource named target::toString).also { register(it) } }
            }
        }
    }

    // TODO: integrate SPICE to make this more realistic

    // Dummy model of an orbit, using a simple ellipse equation. Not at *all* realistic, but it'll make the numbers interesting.
    private fun orbitResource(
        period: Quantity,
        phase: Quantity,
        majorAxis: QuantityVector,
        semiMajorAxis: QuantityVector,
        clock: DiscreteResource<Duration>,
    ): QuantityVectorResource {
        val periodAsDuration = period.asDuration()
        val angularSpeed = (1.0 * ROTATION) / period
        // Take an exact remainder of the clock, then convert to Quantity, to not lose precision over time.
        // Perform the conversion to Quantity at a resource level for performance, instead of converting each sample.
        val periodicClock: QuantityResource = map(clock) { it % periodAsDuration }.asQuantity()
        val t: QuantityResource = periodicClock * angularSpeed + phase
        val resultUnit = majorAxis.unit
        val scalarMajorAxis = majorAxis.valueIn(resultUnit)
        val scalarSemiMajorAxis = semiMajorAxis.valueIn(resultUnit)
        // Perform the calculation as a scalar, asking the unit-awareness system to guarantee the orbital parameter is in Radians.
        // We could do this derivation as unit-aware resource operations too, but this is slightly more performant.
        // Just make sure we attach the result unit at the end, at the resource level.
        return map(t.valueIn(RADIAN)) {
            sin(it) * scalarMajorAxis + cos(it) * scalarSemiMajorAxis
        } * resultUnit
    }

    context(scope: InitScope)
    private fun tickingClock(name: String, period: Duration) = discreteResource(name, ZERO).apply {
        scope.spawn("update $name", every(period) {
            emit((DiscreteDynamicsMonad.map(period::plus)) named { "Increase by $period" })
        })
    }
}