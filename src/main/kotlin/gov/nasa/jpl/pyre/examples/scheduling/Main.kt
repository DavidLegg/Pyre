package gov.nasa.jpl.pyre.examples.scheduling

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.coals.Serialization.encodeToFile
import gov.nasa.jpl.pyre.ember.Duration.Companion.HOUR
import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.ember.Serialization.alias
import gov.nasa.jpl.pyre.ember.times
import gov.nasa.jpl.pyre.ember.toKotlinDuration
import gov.nasa.jpl.pyre.ember.toPyreDuration
import gov.nasa.jpl.pyre.examples.scheduling.data.model.DataModel
import gov.nasa.jpl.pyre.examples.scheduling.geometry.model.GeometryModel
import gov.nasa.jpl.pyre.examples.scheduling.geometry.model.GeometryModel.PointingTarget
import gov.nasa.jpl.pyre.examples.scheduling.gnc.activities.GncSetAgility
import gov.nasa.jpl.pyre.examples.scheduling.gnc.activities.GncSetSystemMode
import gov.nasa.jpl.pyre.examples.scheduling.gnc.activities.GncTurn
import gov.nasa.jpl.pyre.examples.scheduling.gnc.model.GncModel
import gov.nasa.jpl.pyre.examples.scheduling.gnc.model.GncModel.BodyAxis
import gov.nasa.jpl.pyre.examples.scheduling.imager.activities.ImagerPowerOff
import gov.nasa.jpl.pyre.examples.scheduling.imager.activities.ImagerPowerOn
import gov.nasa.jpl.pyre.examples.scheduling.imager.activities.ImagerDoObservation
import gov.nasa.jpl.pyre.examples.scheduling.imager.model.IMAGE
import gov.nasa.jpl.pyre.examples.scheduling.imager.model.ImagerModel
import gov.nasa.jpl.pyre.examples.scheduling.power.model.PowerModel
import gov.nasa.jpl.pyre.examples.scheduling.support_files.CommPass
import gov.nasa.jpl.pyre.examples.scheduling.support_files.ScienceOp
import gov.nasa.jpl.pyre.examples.scheduling.system.activities.GncActivity
import gov.nasa.jpl.pyre.examples.scheduling.system.activities.ImagerActivity
import gov.nasa.jpl.pyre.examples.scheduling.system.activities.TelecomActivity
import gov.nasa.jpl.pyre.examples.scheduling.system.model.SystemModel
import gov.nasa.jpl.pyre.examples.scheduling.telecom.activities.RadioPowerOff
import gov.nasa.jpl.pyre.examples.scheduling.telecom.activities.RadioPowerOn
import gov.nasa.jpl.pyre.examples.scheduling.telecom.activities.RadioSetDownlinkRate
import gov.nasa.jpl.pyre.examples.scheduling.telecom.activities.TelecomPass
import gov.nasa.jpl.pyre.examples.scheduling.telecom.model.TelecomModel
import gov.nasa.jpl.pyre.examples.scheduling.utils.SchedulingAlgorithms.scheduleActivityToEndNear
import gov.nasa.jpl.pyre.examples.scheduling.utils.SchedulingSystem
import gov.nasa.jpl.pyre.examples.units.KILOWATT_HOUR
import gov.nasa.jpl.pyre.flame.plans.GroundedActivity
import gov.nasa.jpl.pyre.flame.plans.activities
import gov.nasa.jpl.pyre.flame.plans.runStandardPlanSimulation
import gov.nasa.jpl.pyre.flame.units.StandardUnits
import gov.nasa.jpl.pyre.flame.units.StandardUnits.DEGREE
import gov.nasa.jpl.pyre.flame.units.StandardUnits.GIGABYTE
import gov.nasa.jpl.pyre.flame.units.StandardUnits.MEGABYTE
import gov.nasa.jpl.pyre.flame.units.StandardUnits.WATT
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.div
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.spark.tasks.InitScope
import gov.nasa.jpl.pyre.spark.tasks.Reactions.every
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import gov.nasa.jpl.pyre.spark.tasks.TaskScope.Companion.delay
import gov.nasa.jpl.pyre.spark.tasks.TaskScope.Companion.spawn
import gov.nasa.jpl.pyre.spark.tasks.task
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.DoubleArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.apache.commons.math3.geometry.euclidean.threed.Rotation
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D
import kotlin.collections.forEach
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.time.Instant
import kotlin.time.TimeSource

// From our knowledge of this particular model, we know that a GncTurn never takes more than 45 minutes.
// We'll use this to make our scheduling procedure highly efficient.
private val GNC_TURN_MAX_DURATION = 45 * MINUTE

private val STANDARD_CONFIG = SystemModel.Config(
    GeometryModel.Config(1 * HOUR),
    GncModel.Config(1 * HOUR, 1 * MINUTE, 0.5 * DEGREE),
    TelecomModel.Config(),
    DataModel.Config(32.0 * GIGABYTE),
    PowerModel.Config(300.0 * WATT, 2.2 * KILOWATT_HOUR),
    ImagerModel.Config(1.2 * MEGABYTE / IMAGE, 15.0 * IMAGE / StandardUnits.MINUTE),
)
val JSON_FORMAT = Json {
    serializersModule = SerializersModule {
        // Instant serialization
        contextual<Instant>(String.serializer().alias(InvertibleFunction.of(
            Instant::parse,
            Instant::toString,
        )))
        // Vector3D serialization
        contextual(DoubleArraySerializer().alias(InvertibleFunction.of(
            { Vector3D(it[0], it[1], it[2]) },
            { doubleArrayOf(it.x, it.y, it.z) }
        )))
        // Rotation serialization
        contextual(DoubleArraySerializer().alias(InvertibleFunction.of(
            { Rotation(it[0], it[1], it[2], it[3], false) },
            { doubleArrayOf(it.q0, it.q1, it.q2, it.q3) }
        )))

        activities<SystemModel> {
            // System activities
            // These are primarily "Adapter" or "glue" activities,
            // which adapt a subsystem activity to the system level, or glue together subsystem views of an activity.
            activity(GncActivity::class)
            activity(TelecomActivity::class)
            activity(ImagerActivity::class)

            // Subsystem activities
            // These are the "meaty" activities, which describe detailed interactions with a single subsystem.
            subsystemActivities<GncModel> {
                activity(GncSetAgility::class)
                activity(GncSetSystemMode::class)
                activity(GncTurn::class)
            }

            subsystemActivities<TelecomModel> {
                activity(RadioPowerOff::class)
                activity(RadioPowerOn::class)
                activity(RadioSetDownlinkRate::class)
                activity(TelecomPass::class)
            }

            subsystemActivities<ImagerModel> {
                activity(ImagerPowerOn::class)
                activity(ImagerPowerOff::class)
                activity(ImagerDoObservation::class)
            }
        }
    }
}

fun main(args: Array<String>) {
    when (args[0].lowercase()) {
        "simulate" -> simulationMain(args.sliceArray(1..<args.size))
        "schedule" -> schedulingMain(args.sliceArray(1..<args.size))
        else -> throw IllegalArgumentException("First argument must be a command: simulate, schedule")
    }
}

fun simulationMain(args: Array<String>) {
    runStandardPlanSimulation(
        args[0],
        { SystemModel(this, STANDARD_CONFIG) },
        JSON_FORMAT,
    )
}

@OptIn(ExperimentalSerializationApi::class, ExperimentalPathApi::class)
fun schedulingMain(args: Array<String>) {
    val commScheduleFile = Path(args[0]).absolute()
    val scienceOpFile = Path(args[1]).absolute()
    val outputDir = Path(args[2]).absolute()
    println("Scheduling input files:")
    println("  Comm Passes: $commScheduleFile")
    println("  Science Ops: $scienceOpFile")
    println("  Output Dir:  $outputDir")

    val clock = TimeSource.Monotonic
    println("Begin scheduling procedure")
    val schedulingStart = clock.markNow()

    println("Begin setup")
    val setupStart = clock.markNow()

    val planStart = Instant.parse("2020-01-01T00:00:00Z")
    val planEnd = Instant.parse("2021-01-01T00:00:00Z")
    val baseScheduler = SchedulingSystem.withoutIncon(
        planStart,
        STANDARD_CONFIG,
        ::SystemModel,
        JSON_FORMAT,
    )

    val commPasses: List<CommPass> = commScheduleFile.inputStream()
        .use { JSON_FORMAT.decodeFromStream<List<CommPass>>(it) }
        .sortedBy { it.start }
    val scienceOps: List<ScienceOp> = scienceOpFile.inputStream()
        .use { JSON_FORMAT.decodeFromStream<List<ScienceOp>>(it) }
        .sortedBy { it.start }

    val setupEnd = clock.markNow()
    println("End setup - ${setupEnd - setupStart}")

    // Note, because this is just a regular java program, we can just intermix regular printlns and stuff to get results out.
    println("Begin layer 1 - critical activities")
    val layer1Start = clock.markNow()
    val layer1Scheduler = baseScheduler.copy()

    // Add in some fixed setup activities, akin to configuring the craft after launch:
    layer1Scheduler += GroundedActivity(planStart + (10 * MINUTE).toKotlinDuration(),
        GncActivity(GncSetSystemMode(GncModel.GncSystemMode.ACTIVE)))

    // Add in the "critical" activities - these are meant as stand-ins for things that are basically fully determined.
    // In a real mission, this would be things like TCMs decided by navigation.

    commPasses.filter { it.critical }.forEach {
        layer1Scheduler += GroundedActivity(
            it.start,
            TelecomActivity(TelecomPass(
                (it.end - it.start).toPyreDuration(),
                it.downlinkRate,
            ))
        )
    }

    scienceOps.filter { it.critical }.forEach {
        layer1Scheduler += GroundedActivity(
            it.start,
            ImagerActivity(ImagerDoObservation(
                (it.end - it.start).toPyreDuration(),
            ))
        )
    }

    layer1Scheduler.runUntil(planEnd)

    val layer1End = clock.markNow()
    println("End layer 1 - ${layer1End - layer1Start}")

    println("Begin layer 2 - Turns for critical activities")
    val layer2Start = clock.markNow()
    // Build a new scheduler, starting at the beginning of the plan, but copy the plan from layer 1:
    val layer2Scheduler = baseScheduler.copy()
    layer2Scheduler += layer1Scheduler.plan()

    // In order to schedule all the turns for layer 1, first we'll construct all the necessary turn activities,
    // paired to their desired end times:
    val criticalTurns = (commPasses.filter { it.critical }.map {
        GncTurn(
            PointingTarget.EARTH,
            PointingTarget.J2000_NEG_Z,
            BodyAxis.HGA,
            BodyAxis.PLUS_Y) to it.start
    } + scienceOps.filter { it.critical }.map {
        GncTurn(
            it.target,
            if (
                it.target == PointingTarget.J2000_NEG_Z
                || it.target == PointingTarget.J2000_POS_Z
            ) PointingTarget.J2000_POS_Y else PointingTarget.J2000_NEG_Z,
            BodyAxis.IMAGER,
            BodyAxis.PLUS_Y,
        ) to it.start
    }).sortedBy { (_, t) -> t }

    // Then for each critical turn, we'll advance the layer 2 scheduler and schedule it.
    for ((turn, turnEnd) in criticalTurns) {
        println("  Scheduling turn to ${turn.primaryPointingTarget} ending at $turnEnd")
        layer2Scheduler.runUntil(turnEnd - GNC_TURN_MAX_DURATION.toKotlinDuration())
        layer2Scheduler.scheduleActivityToEndNear(GncActivity(turn), turnEnd)
    }

    // Note:
    //   A real scheduler would not just blindly lay down these turns - it should also check that those turns
    //   don't conflict with any of the critical activities themselves.
    //   (It should also probably be scheduling turns back from the activity to a safer attitude.)
    //   For this demo, at this stage, just laying down all the turns in one shot like this is sufficient.

    // Finally, advance the layer 2 scheduler to the end of the plan to finish out simulation:
    layer2Scheduler.runUntil(planEnd)

    val layer2End = clock.markNow()
    println("End layer 2 - ${layer2End - layer2Start}")

    // TODO: Schedule layer 3 - opportunistic science - time permitting, schedule all the observations you can,
    //    factoring in the time to do turns to the target.

    // TODO: Schedule layer 4 - required additional downlinks - as needed to prevent data overflow

    println("Begin writing output")
    val outputStart = clock.markNow()

    val plan = layer2Scheduler.plan()

    if (outputDir.exists()) outputDir.deleteRecursively()
    outputDir.createDirectories()
    JSON_FORMAT.encodeToFile(plan, outputDir / "plan.json")

    val outputEnd = clock.markNow()
    println("End writing output - ${outputEnd - outputStart}")

    val schedulingEnd = clock.markNow()
    println("End scheduling procedure - ${schedulingEnd - schedulingStart}")
}
