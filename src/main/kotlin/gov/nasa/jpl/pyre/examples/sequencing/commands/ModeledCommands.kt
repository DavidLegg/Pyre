package gov.nasa.jpl.pyre.examples.sequencing.commands

import gov.nasa.jpl.pyre.examples.sequencing.SequencingDemo
import gov.nasa.jpl.pyre.examples.sequencing.commands.seq.SEQ_MODELED_COMMANDS
import gov.nasa.jpl.pyre.examples.sequencing.commands.telecom.TELECOM_MODELED_COMMANDS
import gov.nasa.jpl.pyre.examples.sequencing.sequence_engine.Command
import gov.nasa.jpl.pyre.examples.sequencing.sequence_engine.CommandBehavior
import gov.nasa.jpl.pyre.general.plans.Activity
import gov.nasa.jpl.pyre.general.plans.ActivityActions.call
import gov.nasa.jpl.pyre.general.plans.ActivityModuleBuilder
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

val ALL_MODELED_COMMANDS = ModeledCommands {
    include(SEQ_MODELED_COMMANDS)
    include(TELECOM_MODELED_COMMANDS)
}

interface ModeledCommands<M : Any> {
    /**
     * Given a model, returns all of the modeled intrinsic command behavior.
     * Intrinsic command behavior is everything *except* sequence control flow.
     */
    fun modeledCommands(model: M): Map<String, CommandBehavior>

    /**
     * Include all the command activities in the receiving serializers module.
     */
    context (builder: ActivityModuleBuilder<M>)
    fun includeModeledCommands()

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        inline fun <reified A : Activity<SequencingDemo>> activity(model: SequencingDemo) = CommandBehavior { command ->
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
            call(activity, model)
        }
    }
}

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
inline fun <reified M : Any> ModeledCommands(block: ModeledCommandsBuilder<M>.() -> Unit) : ModeledCommands<M> {
    val modeledCommandsByName: MutableMap<String, (M) -> CommandBehavior> = mutableMapOf()
    val activityInclusions: MutableList<ActivityModuleBuilder<M>.() -> Unit> = mutableListOf()
    val includedModeledCommands: MutableList<ModeledCommands<M>> = mutableListOf()

    val builder = object : ModeledCommandsBuilder<M> {
        override fun <A : Activity<M>> activity(clazz: KClass<A>) {
            val serializer = clazz.serializer()
            val commandStem = serializer.descriptor.serialName
            require(commandStem !in modeledCommandsByName) { "Duplicate command stem $commandStem" }
            modeledCommandsByName[commandStem] = { model ->
                CommandBehavior { command ->
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
                    call(activity, model)
                }
            }
            activityInclusions += { activity(clazz, serializer) }
        }

        override fun include(other: ModeledCommands<M>) {
            includedModeledCommands += other
        }
    }

    // Run the block in the context of the local builder, collecting all the command info from it.
    builder.block()

    // Use that collected command info to build the ModeledCommands object
    return object : ModeledCommands<M> {
        // TODO: Check for command stem collisions between all the sub-modules?
        override fun modeledCommands(model: M): Map<String, CommandBehavior> = (includedModeledCommands
            .flatMap { it.modeledCommands(model).entries }
            .associate { (k, v) -> k to v }
                + modeledCommandsByName.mapValues { it.value(model) })

        context (builder: ActivityModuleBuilder<M>)
        override fun includeModeledCommands() {
            includedModeledCommands.forEach { it.includeModeledCommands() }
            activityInclusions.forEach { builder.it() }
        }
    }
}

interface ModeledCommandsBuilder<M : Any> {
    fun <A : Activity<M>> activity(clazz: KClass<A>)
    fun include(other: ModeledCommands<M>)
}
