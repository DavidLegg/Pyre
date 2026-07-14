package gov.nasa.jpl.parakeet.examples.lander.config

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Serializable
data class SchedulingParams(
    val SEIS_SCI_RUNOUT_FRAC: Double = 0.5,
    val APSS_SCI_RUNOUT_FRAC: Double = 0.5,
    val HeatProbe_SCI_RUNOUT_FRAC: Double = 0.9,
    val MAX_RUNOUT_SEIS_SCI_PROC_DUR: Duration = 20.minutes,
    val MIN_RUNOUT_SEIS_SCI_PROC_DUR: Duration = 5.minutes,
    val MAX_RUNOUT_APSS_SCI_PROC_DUR: Duration = 20.minutes,
    val MIN_RUNOUT_APSS_SCI_PROC_DUR: Duration = 5.minutes,
    val MAX_RUNOUT_HeatProbe_GET_SCIDATA_DUR: Duration = 60.minutes,
    val MIN_RUNOUT_HeatProbe_GET_SCIDATA_DUR: Duration = 5.minutes,
    val RUNOUT_CLEANUP_MARGIN: Duration = 5.minutes,
    val MIN_SUB_RUNOUT_DUR: Duration = 15.minutes,
    val MinSleepDuration: Duration = 20.minutes,
    val MaxSleepDuration: Duration = 170.minutes,
    val PlacePostUplinkWake: Boolean = true,
)