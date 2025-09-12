package gov.nasa.jpl.pyre.examples.scheduling

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Duration.Companion.HOUR
import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.Serialization.alias
import gov.nasa.jpl.pyre.ember.times
import gov.nasa.jpl.pyre.examples.scheduling.data.model.DataModel
import gov.nasa.jpl.pyre.examples.scheduling.geometry.model.GeometryModel
import gov.nasa.jpl.pyre.examples.scheduling.gnc.model.GncModel
import gov.nasa.jpl.pyre.examples.scheduling.power.model.PowerModel
import gov.nasa.jpl.pyre.examples.scheduling.system.model.SystemModel
import gov.nasa.jpl.pyre.examples.scheduling.utils.SchedulingSystem
import gov.nasa.jpl.pyre.examples.units.KILOWATT_HOUR
import gov.nasa.jpl.pyre.flame.plans.activitySerializersModule
import gov.nasa.jpl.pyre.flame.units.StandardUnits.DEGREE
import gov.nasa.jpl.pyre.flame.units.StandardUnits.GIGABYTE
import gov.nasa.jpl.pyre.flame.units.StandardUnits.WATT
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.times
import kotlinx.serialization.builtins.DoubleArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.apache.commons.math3.geometry.euclidean.threed.Rotation
import kotlin.time.Instant
import kotlin.time.TimeSource

// TODO: Add a system model with
//   - geometry, attitude, power, and data
//   - Config to enable/disable these independently
//     - Bonus: Write "replay" models which accept a SimulationResults and replay the resources for a subsystem from that!
//   - Activities: TCM, Observation, TelecomPass, Turn
// TODO: build SchedulingSystem with that model
// TODO: build a scheduling procedure in a few layers:
//   - Baseline sim - all enabled
//   - TCM pass - no resimulation - lay down TCMs on a fixed schedule
//   - Observations pass - power only - lay down Observations and their Turns
//   - Telecom pass - data only - lay down TelecomPasses and their Turns
//   - Final sim - all enabled - no activities to add

fun main(args: Array<String>) {
    val clock = TimeSource.Monotonic
    val schedulingStart = clock.markNow()
    val planStart = Instant.parse("2020-01-01T00:00:00Z")
    val planEnd = Instant.parse("2021-01-01T00:00:00Z")

    val jsonFormat = Json {
        serializersModule = SerializersModule {
            // Instant serialization
            contextual(String.serializer().alias(InvertibleFunction.of(Instant::parse, Instant::toString)))
            // Rotation serialization
            contextual(DoubleArraySerializer().alias(InvertibleFunction.of(
                { Rotation(it[0], it[1], it[2], it[3], false) },
                { doubleArrayOf(it.q0, it.q1, it.q2, it.q3) }
            )))

            include(activitySerializersModule<SystemModel> {
                // TODO: Activities
            })
        }
    }

    val baseScheduler = SchedulingSystem.withoutIncon(
        planStart,
        SystemModel.Config(
            GeometryModel.Config(1 * HOUR),
            GncModel.Config(1 * HOUR, 5 * MINUTE, 0.5 * DEGREE),
            DataModel.Config(3.1 * GIGABYTE),
            PowerModel.Config(300.0 * WATT, 2.2 * KILOWATT_HOUR),
        ),
        ::SystemModel,
        jsonFormat,
    )

    // Note, because this is just a regular java program, we can just intermix regular printlns and stuff to get results out.
    println("Begin layer 1")
    val layer1Start = clock.markNow()

    // Run an empty plan to get a baseline set of resources
    val completedBaseScheduler = baseScheduler.copy().apply { runUntil(planEnd) }

    val layer1End = clock.markNow()
    println("End layer 1 - ${layer1End - layer1Start}")

    // Schedule some "fixed" activities, like TCM's.

    val schedulingEnd = clock.markNow()
    println("End of scheduling procedure - ${schedulingEnd - schedulingStart}")
}
