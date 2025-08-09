package gov.nasa.jpl.pyre.examples.sequencing.commands.telecom

import gov.nasa.jpl.pyre.examples.sequencing.commands.ModeledCommands

val TELECOM_MODELED_COMMANDS = ModeledCommands {
    activity(ChangeBitRate::class)
    activity(RadioPowerOff::class)
    activity(RadioPowerOn::class)
    activity(SetPrimeRadio::class)
    activity(SetPrimeTwta::class)
    activity(TwtaPowerOff::class)
    activity(TwtaPowerOn::class)
}
