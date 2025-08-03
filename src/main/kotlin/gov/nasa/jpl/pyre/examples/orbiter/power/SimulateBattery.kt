package gov.nasa.jpl.pyre.examples.orbiter.power

import gov.nasa.jpl.pyre.ember.JsonConditions
import gov.nasa.jpl.pyre.ember.JsonConditions.Companion.encodeToStream
import gov.nasa.jpl.pyre.flame.plans.Plan
import gov.nasa.jpl.pyre.flame.plans.PlanSimulation
import gov.nasa.jpl.pyre.flame.plans.activity
import gov.nasa.jpl.pyre.flame.plans.activitySerializersModule
import gov.nasa.jpl.pyre.flame.reporting.ReportHandling.jsonlReportHandler
import gov.nasa.jpl.pyre.flame.tasks.subContext
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.MutableDoubleResource
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Run [BatteryModel] as a standalone simulation.
 */
@OptIn(ExperimentalSerializationApi::class)
fun main(args: Array<String>) {
    val planFile = args[0]
    val finconFile = args[1]

    val jsonFormat = Json {
        serializersModule = activitySerializersModule {
            activity(ChangePowerDemand::class)
        }
    }
    val plan: Plan<StandaloneBatteryModel> = jsonFormat.decodeFromStream(FileInputStream(planFile))

    val simulation = PlanSimulation.withoutIncon(
        jsonlReportHandler(),
        plan.startTime,
        plan.startTime,
        ::StandaloneBatteryModel,
    )

    simulation.runPlan(plan)
    JsonConditions(jsonFormat)
        .also(simulation::save)
        .encodeToStream(FileOutputStream(finconFile))
}

/**
 * Dummy interface wrapping the battery model.
 * Directly constructs the inputs for the battery model, to be poked directly by activities.
 */
class StandaloneBatteryModel(
    context: SparkInitContext,
) {
    val powerDemand: MutableDoubleResource
    val powerProduction: MutableDoubleResource
    val battery: BatteryModel

    init {
        with (context) {
            powerDemand = discreteResource("powerDemand", 0.0)
            powerProduction = discreteResource("powerDemand", 0.0)
            battery = BatteryModel(subContext("battery"), BatterySimConfig(), powerDemand, powerProduction)
        }
    }
}
