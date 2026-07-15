package gov.nasa.jpl.parakeet.examples.sequencing.commands.seq

import gov.nasa.jpl.parakeet.examples.sequencing.commands.ModeledCommands

val SEQ_MODELED_COMMANDS = ModeledCommands {
    activity(SeqAdd::class)
    activity(SeqIf::class)
    activity(SeqSetGlobalInt::class)
    activity(SeqSetGlobalUInt::class)
    activity(SeqSetGlobalFloat::class)
    activity(SeqSetGlobalString::class)
}
