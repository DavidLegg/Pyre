package gov.nasa.jpl.pyre.examples.lander.config

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Serializable
data class MasterActivityDurations(
    val GV_WAKEUP_TIME_OFFSET: Duration = 35.seconds,
    val BOOT_INIT_DURATION: Duration = 2.minutes,
    val LOAD_BLOCK_LIB_DURATION: Duration = 43.seconds,
    val WAKEUP_FULL_DURATION: Duration = 7.minutes + 30.seconds,
    val WAKEUP_DIAG_DURATION: Duration = 4.minutes + 10.seconds,
    val FILE_MGMT_DURATION: Duration = 4.seconds,
    val LME_CURVE_SEL_DURATION: Duration = 35.seconds,
    val SUBMASTER_DIAG_DURATION: Duration = 1.minutes,
    val FSW_DIAG_DURATION: Duration = 1.minutes,
    val FILE_COPY_DURATION: Duration = 1.seconds,
    val SHUTDOWN_FULL_DURATION: Duration = 8.minutes,
    val SHUTDOWN_DIAG_DURATION: Duration = 5.minutes,
)