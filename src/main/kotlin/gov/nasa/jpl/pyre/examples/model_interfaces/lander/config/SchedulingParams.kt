package gov.nasa.jpl.pyre.examples.model_interfaces.lander.config

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.times

data class SchedulingParams(
    val SEIS_SCI_RUNOUT_FRAC: Double = 0.5,
    val APSS_SCI_RUNOUT_FRAC: Double = 0.5,
    val HeatProbe_SCI_RUNOUT_FRAC: Double = 0.9,
    val MAX_RUNOUT_SEIS_SCI_PROC_DUR: Duration = 20 * MINUTE,
    val MIN_RUNOUT_SEIS_SCI_PROC_DUR: Duration = 5 * MINUTE,
    val MAX_RUNOUT_APSS_SCI_PROC_DUR: Duration = 20 * MINUTE,
    val MIN_RUNOUT_APSS_SCI_PROC_DUR: Duration = 5 * MINUTE,
    val MAX_RUNOUT_HeatProbe_GET_SCIDATA_DUR: Duration = 60 * MINUTE,
    val MIN_RUNOUT_HeatProbe_GET_SCIDATA_DUR: Duration = 5 * MINUTE,
    val RUNOUT_CLEANUP_MARGIN: Duration = 5 * MINUTE,
    val MIN_SUB_RUNOUT_DUR: Duration = 15 * MINUTE,
    val MinSleepDuration: Duration = 20 * MINUTE,
    val MaxSleepDuration: Duration = 170 * MINUTE,
    val PlacePostUplinkWake: Boolean = true,
)