package gov.nasa.jpl.pyre.examples.scheduling.gnc.model

import gov.nasa.jpl.pyre.kernel.Duration
import gov.nasa.jpl.pyre.examples.scheduling.geometry.model.GeometryModel.PointingTarget
import gov.nasa.jpl.pyre.examples.scheduling.geometry.model.GeometryModel.PointingTarget.*
import gov.nasa.jpl.pyre.examples.scheduling.gnc.model.GncModel.BodyAxis.*
import gov.nasa.jpl.pyre.general.resources.caching.ResourceCaching.cached
import gov.nasa.jpl.pyre.general.resources.discrete.unit_aware.MutableQuantityResource
import gov.nasa.jpl.pyre.general.resources.discrete.unit_aware.QuantityResource
import gov.nasa.jpl.pyre.general.resources.discrete.unit_aware.QuantityResourceOperations.DurationQuantityResourceOperations.asQuantity
import gov.nasa.jpl.pyre.general.resources.discrete.unit_aware.QuantityResourceOperations.VsQuantity.lessThanOrEquals
import gov.nasa.jpl.pyre.general.resources.discrete.unit_aware.QuantityResourceOperations.quantityResource
import gov.nasa.jpl.pyre.general.resources.discrete.unit_aware.QuantityResourceOperations.register
import gov.nasa.jpl.pyre.general.resources.discrete.unit_aware.QuantityResourceOperations.times
import gov.nasa.jpl.pyre.general.resources.discrete.unit_aware.QuantityResourceOperations.valueIn
import gov.nasa.jpl.pyre.general.units.Quantity
import gov.nasa.jpl.pyre.general.units.QuantityOperations.div
import gov.nasa.jpl.pyre.general.units.QuantityOperations.valueIn
import gov.nasa.jpl.pyre.general.units.StandardUnits.DEGREE
import gov.nasa.jpl.pyre.general.units.StandardUnits.MINUTE
import gov.nasa.jpl.pyre.general.units.StandardUnits.RADIAN
import gov.nasa.jpl.pyre.general.units.StandardUnits.ROTATION
import gov.nasa.jpl.pyre.general.units.StandardUnits.SECOND
import gov.nasa.jpl.pyre.general.units.Unit
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.named
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.register
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResourceOperations.and
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResourceOperations.choose
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResourceOperations.not
import gov.nasa.jpl.pyre.foundation.resources.discrete.Discrete
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.bind
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.equals
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.registeredDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.getValue
import gov.nasa.jpl.pyre.foundation.resources.named
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations.asTimer
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations.greaterThanOrEquals
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations.restart
import gov.nasa.jpl.pyre.foundation.resources.timer.TimerResourceOperations.timer
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.whenever
import kotlinx.serialization.Serializable
import org.apache.commons.math3.geometry.euclidean.threed.Rotation
import org.apache.commons.math3.geometry.euclidean.threed.RotationConvention
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D

val MRAD = Unit.derived("mrad", 1e-3 * RADIAN)
val MRAD_PER_SECOND = Unit.derived("mrad/s", MRAD / SECOND)

/**
 * Highly simplified GNC model, which assumes the spacecraft turns at its maximum agility at all times.
 */
class GncModel(
    context: InitScope,
    val config: Config,
    val inputs: Inputs,
) {
    data class Config(
        val backgroundSamplePeriod: Duration,
        val turningSamplePeriod: Duration,
        val pointingErrorTolerance: Quantity,
    )

    // While not strictly necessary for a system with this few inputs,
    // gathering all the resource inputs to the model in one place is good practice.
    // This makes it easy to build "stub" inputs for testing or subsystem simulations later.
    data class Inputs(
        val pointingTargets: Map<PointingTarget, DiscreteResource<Vector3D>>,
    )

    @Serializable
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

    /**
     * The [GncControlMode] currently applied by the GNC controller.
     */
    val controlMode: DiscreteResource<GncControlMode>

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

    /**
     * Whether the model has settled to point at the desired attitude
     */
    val isSettled: BooleanResource

    init {
        with (context) {
            systemMode = registeredDiscreteResource("system_mode", GncSystemMode.IDLE)

            primaryPointingTarget = registeredDiscreteResource("primary_pointing_target", J2000_POS_X)
            secondaryPointingTarget = registeredDiscreteResource("secondary_pointing_target", J2000_POS_Y)
            primaryBodyAxis = registeredDiscreteResource("primary_body_axis", PLUS_X)
            secondaryBodyAxis = registeredDiscreteResource("secondary_body_axis", PLUS_Y)

            // Instead of choosing agilities directly, I'm characterizing the system in terms of a desired behavior.
            // Namely, I want it to do a half rotation in about 30 minutes.
            // That should make the scheduling problem interesting but fairly tractable.
            val timeForHalfRotation = 30.0 * MINUTE
            val initialAgility = 0.5 * ROTATION / timeForHalfRotation

            // Since doing the derivation above likely produced some weird units,
            // split the declaration and registration, so I can choose the registration unit explicitly.
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

            val targetAttitudeCacheUpdateTolerance = config.pointingErrorTolerance.valueIn(RADIAN) * 1e-2
            targetAttitude = map(
                primaryBodyAxisVector,
                secondaryBodyAxisVector,
                primaryPointingTargetVector,
                secondaryPointingTargetVector,
                ::Rotation
            ).named { "target_attitude" }
                // Register the cached version because Rotation has a poorly-behaved equality
                .also { register(it.cached("target_attitude(c)", Discrete(Rotation.IDENTITY), {
                    r, s -> r.value.applyInverseTo(s.value).angle < targetAttitudeCacheUpdateTolerance
            })) }

            _spacecraftAttitude = registeredDiscreteResource("spacecraft_attitude", Rotation.IDENTITY)

            pointingError = ((map(targetAttitude, spacecraftAttitude) {
                q, r -> q.applyInverseTo(r).angle
            } * RADIAN) named { "pointing_error" })
            register(pointingError, MRAD)
            register(pointingError, DEGREE)

            isSettled = (pointingError lessThanOrEquals config.pointingErrorTolerance)
                .named { "is_settled" }
                .also { register(it) }

            val turnRequired = isSettled.not()
            controlMode = (bind(systemMode) {
                when (it) {
                    GncSystemMode.IDLE -> pure(GncControlMode.IDLE)
                    GncSystemMode.ACTIVE -> turnRequired.choose(pure(GncControlMode.TURN), pure(GncControlMode.HOLD))
                }
            } named { "control_mode" }).also { register(it) }

            val timeSinceLastControllerStep = timer("time_since_last_controller_step")
            val controllerStepSize = map(controlMode) {
                when (it) {
                    GncControlMode.IDLE, GncControlMode.HOLD -> config.backgroundSamplePeriod
                    GncControlMode.TURN -> config.turningSamplePeriod
                }
            } named { "controller_step_size" }

            val maxSingleStepTurnRadians = (agility * controllerStepSize.asQuantity()).valueIn(RADIAN)
            spawn("GNC Controller", whenever(
                (systemMode equals GncSystemMode.ACTIVE)
                        and (timeSinceLastControllerStep greaterThanOrEquals controllerStepSize.asTimer())) {
                when (controlMode.getValue()) {
                    GncControlMode.IDLE -> { /* Do nothing */ }
                    GncControlMode.HOLD, GncControlMode.TURN -> {
                        val maxTurnAngle = maxSingleStepTurnRadians.getValue()
                        val t = targetAttitude.getValue()
                        val s = spacecraftAttitude.getValue()
                        val commandedRotation = t.applyTo(s.revert())
                        if (commandedRotation.angle <= maxTurnAngle) {
                            _spacecraftAttitude.set(t)
                        } else {
                            val achievedRotation = Rotation(
                                commandedRotation.getAxis(RotationConvention.VECTOR_OPERATOR),
                                maxTurnAngle,
                                RotationConvention.VECTOR_OPERATOR)
                            _spacecraftAttitude.set(achievedRotation.applyTo(s))
                        }
                    }
                }
                timeSinceLastControllerStep.restart()
            })
        }
    }
}