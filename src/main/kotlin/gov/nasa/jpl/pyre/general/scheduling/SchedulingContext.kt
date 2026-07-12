package gov.nasa.jpl.pyre.general.scheduling

/**
 * Represents a context where we're using a [SchedulingSystem] to build a plan.
 *
 * This generally involves simulating and placing activities informed by simulation results.
 */
interface SchedulingContext<M> {
    // TODO: Think through what properties and methods should go here.
    //   Should this even be a scope?
    //   Or should SchedulingSystem itself be the context param?
}