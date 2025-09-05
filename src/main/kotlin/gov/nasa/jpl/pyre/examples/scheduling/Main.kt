package gov.nasa.jpl.pyre.examples.scheduling

import gov.nasa.jpl.pyre.examples.scheduling.geometry.model.GeometryModel
import gov.nasa.jpl.pyre.examples.scheduling.utils.SchedulingSystem
import gov.nasa.jpl.pyre.spark.tasks.InitScope
import kotlin.time.Instant

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
    val scheduler = SchedulingSystem.withoutIncon(
        Instant.parse("2020-01-01T00:00:00Z"),
        TODO(),
        TODO(),
    )
}

class FullSimulation(
    context: InitScope,
    config: Config,
) {
    data class Config(
        val geometryConfig: GeometryModel.Config,
    )

    val geometryModel: GeometryModel

    init {
        with (context) {
            geometryModel = GeometryModel(context, config.geometryConfig)
        }
    }
}
