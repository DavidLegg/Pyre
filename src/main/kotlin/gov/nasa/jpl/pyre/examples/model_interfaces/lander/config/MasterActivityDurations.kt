package gov.nasa.jpl.pyre.examples.model_interfaces.lander.config

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.ember.plus
import gov.nasa.jpl.pyre.ember.times

data class MasterActivityDurations(
    val GV_WAKEUP_TIME_OFFSET: Duration = 35 * SECOND,
    val BOOT_INIT_DURATION: Duration = 2 * MINUTE,
    val LOAD_BLOCK_LIB_DURATION: Duration = 43 * SECOND,
    val WAKEUP_FULL_DURATION: Duration = 7 * MINUTE + 30 * SECOND,
    val WAKEUP_DIAG_DURATION: Duration = 4 * MINUTE + 10 * SECOND,
    val FILE_MGMT_DURATION: Duration = 4 * SECOND,
    val LME_CURVE_SEL_DURATION: Duration = 35 * SECOND,
    val SUBMASTER_DIAG_DURATION: Duration = 1 * MINUTE,
    val FSW_DIAG_DURATION: Duration = 1 * MINUTE,
    val FILE_COPY_DURATION: Duration = 1 * SECOND,
    val SHUTDOWN_FULL_DURATION: Duration = 8 * MINUTE,
    val SHUTDOWN_DIAG_DURATION: Duration = 5 * MINUTE,
)