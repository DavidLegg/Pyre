package gov.nasa.jpl.pyre.examples.sequencing.sequence_engine

import gov.nasa.jpl.pyre.coals.InvertibleFunction
import gov.nasa.jpl.pyre.ember.Serialization.alias
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
data class Command(
    val stem: String,
    val args: List<Arg>,
    val metadata: Map<String, String> = mapOf(),
) {
    @Serializable
    sealed interface Arg {
        @Serializable(with = IntArg.Serializer::class)
        data class IntArg(val value: Int) : Arg {
            class Serializer : KSerializer<IntArg> by Int.serializer().alias(InvertibleFunction.of(::IntArg, IntArg::value))
        }
        @Serializable(with = UIntArg.Serializer::class)
        data class UIntArg(val value: UInt) : Arg {
            class Serializer : KSerializer<UIntArg> by UInt.serializer().alias(InvertibleFunction.of(::UIntArg, UIntArg::value))
        }
        @Serializable(with = FloatArg.Serializer::class)
        data class FloatArg(val value: Double) : Arg {
            class Serializer : KSerializer<FloatArg> by Double.serializer().alias(InvertibleFunction.of(::FloatArg, FloatArg::value))
        }
        @Serializable(with = StringArg.Serializer::class)
        data class StringArg(val value: String) : Arg {
            class Serializer : KSerializer<StringArg> by String.serializer().alias(InvertibleFunction.of(::StringArg, StringArg::value))
        }
    }
}

