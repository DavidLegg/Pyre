package gov.nasa.jpl.pyre.examples.sequencing.commands

import gov.nasa.jpl.pyre.examples.sequencing.SequencingDemo
import gov.nasa.jpl.pyre.examples.sequencing.commands.telecom.RadioPowerOff
import gov.nasa.jpl.pyre.examples.sequencing.commands.telecom.RadioPowerOn
import gov.nasa.jpl.pyre.examples.sequencing.commands.telecom.TwtaPowerOff
import gov.nasa.jpl.pyre.examples.sequencing.commands.telecom.TwtaPowerOn
import gov.nasa.jpl.pyre.examples.sequencing.sequence_engine.Command
import gov.nasa.jpl.pyre.examples.sequencing.sequence_engine.CommandBehavior
import gov.nasa.jpl.pyre.flame.plans.Activity
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.serializer

object ModeledCommands {
    fun modeledCommands(model: SequencingDemo): Map<String, CommandBehavior> = mapOf(
        RadioPowerOn.COMMAND_STEM to activity<RadioPowerOn>(model),
        RadioPowerOff.COMMAND_STEM to activity<RadioPowerOff>(model),
        TwtaPowerOn.COMMAND_STEM to activity<TwtaPowerOn>(model),
        TwtaPowerOff.COMMAND_STEM to activity<TwtaPowerOff>(model),
    )

    // TODO: Factor out some of this demo code to generic utilities,
    //   perhaps as a new "sequencing" utility package users can import.

    @OptIn(ExperimentalSerializationApi::class)
    private inline fun <reified A : Activity<SequencingDemo>> activity(model: SequencingDemo) = CommandBehavior { command ->
        val serializer = serializer<A>()

        val jsonObject = buildJsonObject {
            for ((i, arg) in command.args.withIndex()) {
                val argName = serializer.descriptor.getElementName(i)
                val argDescriptor = serializer.descriptor.getElementDescriptor(i)

                fun requireSerialKind(vararg allowedKinds: SerialKind) {
                    require(argDescriptor.kind in allowedKinds) {
                        "Actual argument kind ${arg.javaClass.simpleName} is incompatible with expected kinds" +
                                " ${allowedKinds.joinToString(",")} for $argName"
                    }
                }

                val jsonValue = when (arg) {
                    is Command.Arg.FloatArg -> {
                        requireSerialKind(PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE)
                        JsonPrimitive(arg.value)
                    }
                    is Command.Arg.IntArg -> {
                        requireSerialKind(PrimitiveKind.INT, PrimitiveKind.LONG)
                        JsonPrimitive(arg.value)
                    }
                    is Command.Arg.UIntArg -> {
                        requireSerialKind(PrimitiveKind.INT, PrimitiveKind.LONG)
                        JsonPrimitive(arg.value)
                    }
                    is Command.Arg.StringArg -> {
                        requireSerialKind(PrimitiveKind.STRING, SerialKind.ENUM)
                        JsonPrimitive(arg.value)
                    }
                }
                put(argName, jsonValue)
            }
        }

        val activity = Json.decodeFromJsonElement(serializer, jsonObject)
        activity.effectModel(model)
    }
}