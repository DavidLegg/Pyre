package gov.nasa.jpl.pyre.examples.model_interfaces.lander.config

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Duration.Companion.MINUTE
import gov.nasa.jpl.pyre.ember.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.ember.Duration.Companion.ZERO
import gov.nasa.jpl.pyre.ember.times

data class CommParameters(
    val XBAND_PRE_COMM_MARGIN: Duration = 5 * MINUTE,
    val XBAND_POST_COMM_MARGIN: Duration = 5 * MINUTE,
    val UHF_PRE_COMM_MARGIN: Duration = 35 * MINUTE,
    val UHF_POST_COMM_MARGIN: Duration = 8 * MINUTE,
    val XBAND_PREP_OVERHEAD: Duration = 23 * SECOND,
    val XBAND_CLEANUP_DURATION: Duration = 19 * SECOND,
    val UHF_RT_DUR_1: Duration = 35 * SECOND,
    val UHF_SCI_DUR_2_OFFSET: Duration = ZERO,
    val UHF_RT_3_DUR: Duration = ZERO,
    val UHF_CLEANUP_DUR: Duration = 40 * SECOND,
    val UHF_RETURN_LINK_UNENCODED_EFFICIENCY: Double = 0.9811,
    val UHF_RETURN_LINK_ENCODED_EFFICIENCY: Double = 0.8507,
    val UHF_DATA_VOLUME_SCALAR: Double = 0.91,
    val UHF_DELAY_FOR_VC00: Boolean = false,
)
