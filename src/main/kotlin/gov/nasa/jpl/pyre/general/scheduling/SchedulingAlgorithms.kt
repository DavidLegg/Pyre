package gov.nasa.jpl.pyre.general.scheduling

import gov.nasa.jpl.pyre.kernel.Duration.Companion.EPSILON
import gov.nasa.jpl.pyre.kernel.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.kernel.ratioOver
import gov.nasa.jpl.pyre.kernel.roundTimes
import gov.nasa.jpl.pyre.kernel.toKotlinDuration
import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.plans.GroundedActivity
import org.apache.commons.math3.analysis.UnivariateFunction
import org.apache.commons.math3.analysis.solvers.AllowedSolution
import org.apache.commons.math3.analysis.solvers.BracketingNthOrderBrentSolver
import org.apache.commons.math3.exception.NoBracketingException
import org.apache.commons.math3.exception.NumberIsTooLargeException
import org.apache.commons.math3.exception.TooManyEvaluationsException
import kotlin.time.DurationUnit
import kotlin.time.Instant

/**
 * A suite of useful scheduling algorithms, written as extensions on [SchedulingSystem].
 *
 * These encode more sophisticated scheduling patterns than simply placing an activity at a certain start time.
 * Many of these make extensive use of [SchedulingSystem.copy] to perform backtracking search.
 */
object SchedulingAlgorithms {
    private val END_TIME_SOLVER = BracketingNthOrderBrentSolver(
        // We'll demand absurd relative accuracy, because a large absolute solution just means the activity is far
        // in the future; this should affect solver accuracy.
//        1e-100,
        // No point in giving times more precise than EPSILON, since they'll just get rounded anyways
        EPSILON ratioOver SECOND,
        // Similarly, no point in requiring function values more accurate than EPSILON, since the simulation can't do that.
//        EPSILON ratioOver SECOND,
        // default maximal order used by no-arg constructor
        2,
    )

    /**
     * Schedule [activity] to end at [endTime].
     *
     * To test a start time, the period from [earliestStart] through the activity end time will be re-simulated.
     * For better performance, choose later [earliestStart] times to limit search and re-simulation effort.
     *
     * @param earliestStart
     * The earliest the scheduled activity may start, defaulting to the current time of this scheduler.
     *
     * Setting this parameter limits search and simulation, without advancing the current scheduler.
     * Advancing [this] instead of using [earliestStart] is slightly more performant, since it avoids one additional [this.copy].
     *
     * @throws CouldNotScheduleException
     * If solver cannot find a suitable start time.
     */
    fun <M, C> SchedulingSystem<M, C>.scheduleActivityToEndNear(
        activity: Activity<M>,
        endTime: Instant,
        earliestStart: Instant = time(),
        name: String = requireNotNull(activity::class.simpleName),
    ): GroundedActivity<M> {
        // If the earliest start is later than now, save computation by copying the scheduler
        // and advancing the copy to the earliest start.
        // This avoids modifying this scheduler, and avoids re-simulating now through earliestStart.
        val testScheduler = if (time() >= earliestStart) this else copy().apply { runUntil(earliestStart) }
        val start = testScheduler.time()
        val f = UnivariateFunction { tDouble ->
            // Compute the start time as an offset from now:
            val tInstant = start + (tDouble roundTimes SECOND).toKotlinDuration()
            // Copy this scheduler and run the activity at that start time
            val tEnd = testScheduler.copy().runUntil(GroundedActivity(tInstant, activity, name=name))
            // Return the error in end time, also in seconds.
            // Using seconds as the input and output unit ensures slopes near 1.0, for a well-conditioned root-finding problem.
            (tEnd - endTime).toDouble(DurationUnit.SECONDS)
        }

        val exc: Throwable
        try {
            // Since an activity must start before it ends, and must start after now, use the bracketing constructor.
            // Further, if we're scheduling by end time, it's to ensure that an activity ends by a specific time.
            // For that reason, choose the "BELOW_SIDE" solution, which corresponds to the solution finishing by the indicated end time.
            val selectedStartDouble = END_TIME_SOLVER.solve(
                100,
                f,
                0.0,
                (endTime - start).toDouble(DurationUnit.SECONDS),
                AllowedSolution.BELOW_SIDE,
            )
            // Having selected our start time as a double, add the activity to this at that time:
            val groundedActivity = GroundedActivity(start + (selectedStartDouble roundTimes SECOND).toKotlinDuration(), activity, name=name)
            this += groundedActivity
            return groundedActivity
        } catch (e: NoBracketingException) {
            exc = e
        } catch (e: TooManyEvaluationsException) {
            exc = e
        } catch (e: NumberIsTooLargeException) {
            exc = e
        }
        throw CouldNotScheduleException("Solver failed trying to schedule $activity to end at $endTime", exc)
    }

    class CouldNotScheduleException(message: String, cause: Throwable) : RuntimeException(message, cause)
}