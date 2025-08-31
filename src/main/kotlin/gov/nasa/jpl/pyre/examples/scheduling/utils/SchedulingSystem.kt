package gov.nasa.jpl.pyre.examples.scheduling.utils

import gov.nasa.jpl.pyre.coals.andThen;
import gov.nasa.jpl.pyre.ember.InconProvider
import gov.nasa.jpl.pyre.ember.JsonConditions
import gov.nasa.jpl.pyre.ember.ReportHandler
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.flame.plans.ActivityActions.ActivityEvent
import gov.nasa.jpl.pyre.flame.plans.GroundedActivity
import gov.nasa.jpl.pyre.flame.plans.Plan
import gov.nasa.jpl.pyre.flame.plans.PlanSimulation
import gov.nasa.jpl.pyre.flame.reporting.ReportHandling.assumeType
import gov.nasa.jpl.pyre.flame.reporting.ReportHandling.channels
import gov.nasa.jpl.pyre.spark.reporting.ChannelizedReport
import gov.nasa.jpl.pyre.spark.tasks.InitScope
import kotlinx.serialization.json.Json
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.require
import kotlin.time.Instant

class SchedulingSystem<M, C> private constructor(
    startTime: Instant?,
    private val config: C,
    private val constructModel: InitScope.(C) -> M,
    private val modelClass: KType,
    private val jsonFormat: Json,
    incon: InconProvider?,
) {
    private val nominalActivities: MutableList<GroundedActivity<M>> = mutableListOf()
    private val resources: MutableMap<String, MutableList<ChannelizedReport<*>>> = mutableMapOf()
    private val activitySpans: MutableMap<Activity<M>, ActivityEvent<M>> = mutableMapOf()
    private val reportHandler: ReportHandler = channels(
        "activities" to (assumeType<ActivityEvent<M>>() andThen { (value, type) ->
            // The event coming straight out of the simulator will have a non-null activity.
            // It's only when deserializing ActivityEvents that we lose the activity object reference.
            // Additionally, ActivityEvents are cumulative - we only want to keep the last one for any given activity.
            activitySpans[requireNotNull(value.data.activity)] = value.data
        }),
        miscHandler = { value, type ->
            if (value is ChannelizedReport<*>) {
                resources.getOrPut(value.channel, ::mutableListOf) += value
            }
        }
    )
    private val simulation: PlanSimulation<M> = if (incon == null) {
        PlanSimulation.withoutIncon(
            reportHandler,
            requireNotNull(startTime),
            startTime,
            { constructModel(config) },
            modelClass,
        )
    } else {
        PlanSimulation.withIncon(
            reportHandler,
            incon,
            { constructModel(config) },
            modelClass,
        )
    }
    // Get the start time from the simulation, regardless of how the simulation was initialized, to keep the two in sync.
    private val startTime: Instant = simulation.time()

    fun time() = simulation.time()

    fun runUntil(endTime: Instant) {
        val startTime = time()
        // Inject only the activities that we're about to run.
        // This way, we can adjust the plan that's still in the future when we're done.
        val activitiesToRun = nominalActivities
            .filter { it.time >= startTime && it.time < endTime }
            .sortedBy { it.time }
        simulation.addActivities(activitiesToRun)
        simulation.runUntil(endTime)
    }

    fun addActivity(activity: GroundedActivity<M>) {
        require(activity.time >= time()) {
            "System is at ${time()}, cannot add activity in the past at ${activity.time}"
        }
        nominalActivities += activity
    }

    fun removeActivity(activity: GroundedActivity<M>) {
        require(activity.time >= time()) {
            "System is at ${time()}, cannot remove activity in the past at ${activity.time}"
        }
        nominalActivities -= activity
    }

    operator fun plusAssign(activity: GroundedActivity<M>) = addActivity(activity)
    operator fun minusAssign(activity: GroundedActivity<M>) = removeActivity(activity)

    fun plan() = Plan(startTime, time(), nominalActivities.toList())

    fun results() = SimulationResults(
        startTime,
        time(),
        resources.toMap(),
        activitySpans.toMap(),
    )

    fun copy(newConfig: C = config): SchedulingSystem<M, C> {
        // Collect the state of this simulation
        val incon = JsonConditions(jsonFormat).also(simulation::save)
        // Use that to initialize a new simulation, configured with newConfig as well.
        val result = SchedulingSystem(
            startTime,
            newConfig,
            constructModel,
            modelClass,
            jsonFormat,
            incon,
        )
        // Copy over all the other bookkeeping data
        // TODO: Consider using a reference back to these data instead of copying all of them
        result.nominalActivities.addAll(this.nominalActivities)
        result.resources.putAll(this.resources)
        result.activitySpans.putAll(this.activitySpans)
        return result
    }

    companion object {
        fun <M, C> withoutIncon(
            startTime: Instant,
            config: C,
            constructModel: InitScope.(C) -> M,
            modelClass: KType,
            jsonFormat: Json = Json,
        ) = SchedulingSystem(startTime, config, constructModel, modelClass, jsonFormat, null)

        inline fun <reified M, C> withoutIncon(
            startTime: Instant,
            config: C,
            noinline constructModel: InitScope.(C) -> M,
            jsonFormat: Json = Json,
        ) = withoutIncon(startTime, config, constructModel, typeOf<M>(), jsonFormat)

        fun <M, C> withIncon(
            incon: InconProvider,
            config: C,
            constructModel: InitScope.(C) -> M,
            modelClass: KType,
            jsonFormat: Json = Json,
        ) = SchedulingSystem(null, config, constructModel, modelClass, jsonFormat, incon)

        inline fun <reified M, C> withIncon(
            incon: InconProvider,
            config: C,
            noinline constructModel: InitScope.(C) -> M,
            jsonFormat: Json = Json,
        ) = withIncon(incon, config, constructModel, typeOf<M>(), jsonFormat)
    }
}