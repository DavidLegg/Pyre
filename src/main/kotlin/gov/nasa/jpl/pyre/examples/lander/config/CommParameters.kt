package gov.nasa.jpl.pyre.examples.lander.config

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Serializable
data class CommParameters(
    val XBAND_PRE_COMM_MARGIN: Duration = 5.minutes,
    val XBAND_POST_COMM_MARGIN: Duration = 5.minutes,
    val UHF_PRE_COMM_MARGIN: Duration = 35.minutes,
    val UHF_POST_COMM_MARGIN: Duration = 8.minutes,
    val XBAND_PREP_OVERHEAD: Duration = 23.seconds,
    val XBAND_CLEANUP_DURATION: Duration = 19.seconds,
    val UHF_RT_DUR_1: Duration = 35.seconds,
    val UHF_SCI_DUR_2_OFFSET: Duration = ZERO,
    val UHF_RT_3_DUR: Duration = ZERO,
    val UHF_CLEANUP_DUR: Duration = 40.seconds,
    val UHF_RETURN_LINK_UNENCODED_EFFICIENCY: Double = 0.9811,
    val UHF_RETURN_LINK_ENCODED_EFFICIENCY: Double = 0.8507,
    val UHF_DATA_VOLUME_SCALAR: Double = 0.91,
    val UHF_DELAY_FOR_VC00: Boolean = false,
)
