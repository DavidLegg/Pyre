package gov.nasa.jpl.pyre.foundation.plans

import gov.nasa.jpl.pyre.foundation.plans.ActivityActions.ActivityEvent
import gov.nasa.jpl.pyre.foundation.reporting.Channel
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.ChannelData
import gov.nasa.jpl.pyre.foundation.reporting.ChannelReport.ChannelMetadata
import gov.nasa.jpl.pyre.foundation.reporting.ChannelizedReportHandler
import gov.nasa.jpl.pyre.foundation.resources.clock.ClockResourceOperations.clock
import gov.nasa.jpl.pyre.utilities.Reflection.withArg
import gov.nasa.jpl.pyre.kernel.Snapshot
import gov.nasa.jpl.pyre.kernel.KernelSimulator
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScopeResult
import gov.nasa.jpl.pyre.foundation.tasks.coroutineTask
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.channel
import gov.nasa.jpl.pyre.foundation.tasks.ResourceScope.Companion.now
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope
import gov.nasa.jpl.pyre.foundation.tasks.SimulationScope.Companion.subSimulationScope
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import gov.nasa.jpl.pyre.kernel.BasicInitScope
import gov.nasa.jpl.pyre.kernel.Cell
import gov.nasa.jpl.pyre.kernel.Effect
import gov.nasa.jpl.pyre.kernel.Snapshot.Companion.provide
import gov.nasa.jpl.pyre.kernel.Name
import gov.nasa.jpl.pyre.kernel.NameOperations.div
import kotlin.reflect.KType
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Factors a simulation into a model and activities.
 *
 * The model is constructed irrespective of the plan, and defines the world the activities interact with.
 *
 * Activities are atomic units of simulation behavior, which may interact directly with the model but not each other.
 * Activities are specified in a Plan as ActivityDirectives, indicating what activity to perform, and when to perform it.
 * A Plan can be loaded into a running simulation, augmenting its behavior with additional Activities.
 *
 * A standard workflow involves
 * 1. restoring a simulation from a fincon taken at the end of the last planning cycle,
 * 2. loading a plan with the next cycle's activities,
 * 3. simulating until the end of this cycle, and
 * 4. cutting a fincon in preparation for the next cycle.
 */
class PlanSimulation<M>(
    reportHandler: ChannelizedReportHandler,
    startTime: Instant? = null,
    incon: Snapshot? = null,
    constructModel: context (InitScope) () -> M,
) {
    private val simulationScope: SimulationScope
    private val kernelSimulator: KernelSimulator

    init {
        lateinit var tempSimulationScope: SimulationScope
        kernelSimulator = KernelSimulator(reportHandler, incon = incon, startTime = startTime, initialize = {
            // Get the kernel-level init scope
            val basicInitScope = contextOf<BasicInitScope>()
            // Wrap it in a foundation-level init scope
            val initScope = object : InitScope {
                override fun <T : Any> allocate(
                    name: Name,
                    value: T,
                    valueType: KType,
                    stepBy: (T, Duration) -> T,
                    mergeConcurrentEffects: (Effect<T>, Effect<T>) -> Effect<T>
                ): Cell<T> = basicInitScope.allocate(name, value, valueType, stepBy, mergeConcurrentEffects)

                override fun spawn(name: Name, block: suspend context(TaskScope) () -> TaskScopeResult) =
                    // When spawning a task, build a simulation scope which incorporates the task's Name
                    basicInitScope.spawn(name, context(subSimulationScope(contextName / name)) { coroutineTask(block) })

                override fun <V> read(cell: Cell<V>): V = basicInitScope.read(cell)
                override fun <T> report(channel: Channel<T>, value: T) = basicInitScope.report(ChannelData(channel.name, now(), value))

                override fun <T> channel(
                    name: Name,
                    metadata: Map<String, ChannelReport.Metadatum>,
                    valueType: KType
                ): Channel<T> {
                    val reportType = ChannelData::class.withArg(valueType)
                    basicInitScope.report(ChannelMetadata<T>(
                        name,
                        metadata,
                        dataType = valueType,
                        reportType = reportType,
                        metadataType = ChannelMetadata::class.withArg(valueType),
                    ))
                    return Channel(name, reportType)
                }

                override val contextName: Name? = null
                override fun toString() = ""

                override val simulationClock = clock("simulation_clock", requireNotNull(incon?.provide<Instant>("simulation", "time") ?: startTime))

                override val activities = channel<ActivityEvent>(Name("activities"))
                override val stdout = channel<String>(Name("stdout"))
                override val stderr = channel<String>(Name("stderr"))
            }
            // Use the foundation-level init scope to build the model
            constructModel(initScope)
            // Also squirrel away that init scope, as just a simulation scope, to be re-used later
            tempSimulationScope = initScope
        })
        simulationScope = tempSimulationScope
    }

    fun time() = kernelSimulator.time()

    /**
     * Run the simulation until [endTime].
     *
     * This includes stall protection - if the simulation steps at least [SIMULATION_STALL_LIMIT] iterations
     * without advancing in time, an exception is thrown to avoid infinite loops.
     */
    fun runUntil(endTime: Instant) {
        require(endTime >= time()) {
            "Simulation time is currently ${time()}, cannot step backwards to $endTime"
        }
        while (time() < endTime) stepTo(endTime)
    }

    private var stepsWithoutAdvancingTime = 0

    /**
     * Advance the simulation by one step, no further than [endTime].
     *
     * This includes stall protection - if the simulation steps at least [SIMULATION_STALL_LIMIT] iterations
     * without advancing in time, an exception is thrown to avoid infinite loops.
     */
    fun stepTo(endTime: Instant) {
        val timeBeforeStep = time()
        kernelSimulator.stepTo(endTime)
        if (time() > timeBeforeStep) {
            stepsWithoutAdvancingTime = 0
        } else if (++stepsWithoutAdvancingTime > SIMULATION_STALL_LIMIT) {
            kernelSimulator.dump()
            throw IllegalStateException("Simulation has stalled at ${time()} after $stepsWithoutAdvancingTime iterations.")
        }
    }

    fun save() = kernelSimulator.save()

    fun addActivity(activity: GroundedActivity<M>) {
        kernelSimulator.addActivity(activity.time, Name("activity") / activity.name, TODO("Converting a GroundedActivity to a PureTaskStep"))
    }

    fun runPlan(plan: Plan<M>) {
        require(plan.startTime == time()) {
            "Cannot run plan starting at ${plan.startTime}. Simulation is at ${time()}"
        }
        plan.activities.forEach(this::addActivity)
        runUntil(plan.endTime)
    }

    companion object {
        /**
         * The maximum number of iterations the simulation may perform without advancing in time,
         * before it is declared "stalled" and aborted.
         *
         * This provides protection against some kinds of infinitely looping tasks.
         */
        var SIMULATION_STALL_LIMIT: Int = 100
    }
}
