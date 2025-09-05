package gov.nasa.jpl.pyre.examples.scheduling.gnc.model

import gov.nasa.jpl.pyre.examples.scheduling.geometry.model.GeometryModel.PointingTarget
import gov.nasa.jpl.pyre.examples.scheduling.geometry.model.GeometryModel.PointingTarget.*
import gov.nasa.jpl.pyre.examples.scheduling.gnc.model.GncModel.BodyAxis.*
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.MutableQuantityResource
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResource
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResourceOperations.quantityResource
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResourceOperations.register
import gov.nasa.jpl.pyre.flame.units.QuantityOperations.div
import gov.nasa.jpl.pyre.flame.units.QuantityOperations.minus
import gov.nasa.jpl.pyre.flame.units.StandardUnits.DEGREE
import gov.nasa.jpl.pyre.flame.units.StandardUnits.MINUTE
import gov.nasa.jpl.pyre.flame.units.StandardUnits.RADIAN
import gov.nasa.jpl.pyre.flame.units.StandardUnits.ROTATION
import gov.nasa.jpl.pyre.flame.units.StandardUnits.SECOND
import gov.nasa.jpl.pyre.flame.units.Unit
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.named
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.spark.reporting.Reporting.register
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.bind
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.spark.resources.named
import gov.nasa.jpl.pyre.spark.tasks.InitScope
import org.apache.commons.math3.geometry.euclidean.threed.Rotation
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D

val MRAD = Unit.derived("mrad", 1e-3 * RADIAN)
val MRAD_PER_SECOND = Unit.derived("mrad/s", MRAD / SECOND)
val MRAD_PER_SECOND_SQUARED = Unit.derived("mrad/s^2", MRAD / SECOND.pow(2))

class GncModel(
    context: InitScope,
    val inputs: Inputs,
) {
    // While not strictly necessary for a system with this few inputs,
    // gathering all the resource inputs to the model in one place is good practice.
    // This makes it easy to build "stub" inputs for testing or subsystem simulations later.
    data class Inputs(
        val pointingTargets: Map<PointingTarget, DiscreteResource<Vector3D>>,
    )

    enum class BodyAxis(val vector: Vector3D) {
        // Standard frame vectors
        PLUS_X(Vector3D.PLUS_I),
        PLUS_Y(Vector3D.PLUS_J),
        PLUS_Z(Vector3D.PLUS_K),
        MINUS_X(Vector3D.MINUS_I),
        MINUS_Y(Vector3D.MINUS_J),
        MINUS_Z(Vector3D.MINUS_K),
        // Fixed antenna/instrument vectors
        HGA(Vector3D(-1.0, 0.0, -0.2).normalize()),
        IMAGER(Vector3D(0.2, 0.0, -0.8).normalize()),
    }

    enum class GncSystemMode {
        /**
         * Apply no control to spacecraft attitude.
         *
         * System will maintain [GncControlMode.IDLE] until commanded otherwise.
         */
        IDLE,

        /**
         * Actively control spacecraft attitude.
         *
         * System will automatically switch between [GncControlMode.HOLD] and [GncControlMode.TURN] as needed.
         */
        ACTIVE,
    }

    enum class GncControlMode {
        /**
         * Apply no control to spacecraft attitude.
         */
        IDLE,

        /**
         * Maintain current commanded attitude
         */
        HOLD,

        /**
         * Turn to next commanded attitude
         */
        TURN,
    }

    /**
     * Commanded mode for the GNC system. See values of [GncSystemMode] for details.
     */
    val systemMode: MutableDiscreteResource<GncSystemMode>

    // Control mode is controlled by GNC, as a consequence of system mode and commanding.
    private val _controlMode: MutableDiscreteResource<GncControlMode>

    /**
     * The [GncControlMode] currently applied by the GNC controller.
     */
    val controlMode: DiscreteResource<GncControlMode> get() = _controlMode

    /**
     * Target to align [primaryBodyAxis] with
     */
    val primaryPointingTarget: MutableDiscreteResource<PointingTarget>

    /**
     * Target to align [secondaryBodyAxis] with
     */
    val secondaryPointingTarget: MutableDiscreteResource<PointingTarget>

    /**
     * Axis to align with [primaryPointingTarget]
     */
    val primaryBodyAxis: MutableDiscreteResource<BodyAxis>

    /**
     * Axis to align with [secondaryPointingTarget]
     */
    val secondaryBodyAxis: MutableDiscreteResource<BodyAxis>

    /**
     * Maximum rotational acceleration that can be commanded.
     *
     * Units: Angle / Time^2, e.g. rad/s^2
     */
    val maxAcceleration: MutableQuantityResource

    /**
     * Maximum rotational velocity that can be commanded.
     *
     * Units: Angle / Time
     */
    val agility: MutableQuantityResource

    /**
     * The rotation commanded for [spacecraftAttitude],
     * which would align [primaryBodyAxis] with [primaryPointingTarget] exactly,
     * and then align [secondaryBodyAxis] with [secondaryPointingTarget] as closely as possible.
     */
    val targetAttitude: DiscreteResource<Rotation>

    // Spacecraft attitude is computed by GNC system, as a consequence of commanding and kinematics.
    private val _spacecraftAttitude: MutableDiscreteResource<Rotation>

    /**
     * Rotation of [PLUS_X] from [J2000_POS_X]
     */
    val spacecraftAttitude: DiscreteResource<Rotation> get() = _spacecraftAttitude

    /**
     * The angle between [targetAttitude] and [spacecraftAttitude]
     */
    val pointingError: QuantityResource

    init {
        with (context) {
            systemMode = registeredDiscreteResource("system_mode", GncSystemMode.IDLE)
            _controlMode = registeredDiscreteResource("control_mode", GncControlMode.IDLE)

            primaryPointingTarget = registeredDiscreteResource("primary_pointing_target", J2000_POS_X)
            secondaryPointingTarget = registeredDiscreteResource("secondary_pointing_target", J2000_POS_Y)
            primaryBodyAxis = registeredDiscreteResource("primary_body_axis", PLUS_X)
            secondaryBodyAxis = registeredDiscreteResource("secondary_body_axis", PLUS_Y)

            // Instead of choosing agilities directly, I'm characterizing the system in terms of a desired behavior.
            // I'd like it to choose the time to accelerate to max velocity, and the time to complete a half rotation.
            // Using a trapezoidal turn profile, we can derive agility and maxAcceleration as follows:
            val timeToMaxVelocity = 5.0 * MINUTE
            val timeForHalfRotation = 30.0 * MINUTE
            val initialAgility = (0.5 * ROTATION) / (timeForHalfRotation - timeToMaxVelocity)
            val initialMaxAcceleration = initialAgility / timeToMaxVelocity

            // Since doing the derivation above likely produced some weird units,
            // split the declaration and registration, so I can choose the registration unit explicitly.
            maxAcceleration = quantityResource("max_acceleration", initialMaxAcceleration)
                .also { register(it, MRAD_PER_SECOND_SQUARED) }
            agility = quantityResource("agility", initialAgility)
                .also { register(it, MRAD_PER_SECOND) }

            val primaryPointingTargetVector = (bind(primaryPointingTarget, inputs.pointingTargets::getValue)
                    named { "${primaryPointingTarget}_vector" }).also { register(it) }
            val secondaryPointingTargetVector = (bind(secondaryPointingTarget, inputs.pointingTargets::getValue)
                    named { "${secondaryPointingTarget}_vector" }).also { register(it) }
            val primaryBodyAxisVector = (map(primaryBodyAxis, BodyAxis::vector)
                    named { "${primaryBodyAxis}_vector" }).also { register(it) }
            val secondaryBodyAxisVector = (map(secondaryBodyAxis, BodyAxis::vector)
                    named { "${secondaryBodyAxis}_vector" }).also { register(it) }

            targetAttitude = (map(
                primaryBodyAxisVector,
                secondaryBodyAxisVector,
                primaryPointingTargetVector,
                secondaryPointingTargetVector,
                ::Rotation
            ) named { "target_attitude" }).also { register(it) }

            _spacecraftAttitude = registeredDiscreteResource("spacecraft_attitude", Rotation.IDENTITY)

            pointingError = ((map(targetAttitude, spacecraftAttitude) {
                q, r -> q.applyInverseTo(r).angle
            } * RADIAN) named { "pointing_error" })
            register(pointingError, MRAD)
            register(pointingError, DEGREE)

            // TODO: Daemon controlling the actual turns
        }
    }
}