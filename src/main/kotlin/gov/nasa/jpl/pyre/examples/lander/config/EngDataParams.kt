package gov.nasa.jpl.pyre.examples.lander.config

data class EngDataParams(
    val AWAKE_ENG_DATA_RATE: Double = 1.0104 / 3600,
    val UHF_PREP_DATA_DUMP_DIAG: Double = 1.532,
    val UHF_PREP_DATA_DUMP_NO_DIAG: Double = 0.971,
    val UHF_ACTIVE_DATA_RATE: Double = 0.432 / 3600,
    val SHUTDOWN_DATA_DUMP: Double = 0.000,
    val WAKEUP_FULL_DATA_DUMP: Double = 0.526,
    val WAKEUP_DIAG_DATA_DUMP: Double = 0.256,
)







