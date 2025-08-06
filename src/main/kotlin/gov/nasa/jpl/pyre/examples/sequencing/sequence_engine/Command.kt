package gov.nasa.jpl.pyre.examples.sequencing.sequence_engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Command(
    val stem: String,
    val args: List<Arg>,
    val metadata: Map<String, String> = mapOf(),
) {
    @Serializable
    sealed interface Arg {
        @Serializable
        @SerialName("int")
        data class IntArg(val value: Int) : Arg
        @Serializable
        @SerialName("uint")
        data class UIntArg(val value: UInt) : Arg
        @Serializable
        @SerialName("float")
        data class FloatArg(val value: Double) : Arg
        @Serializable
        @SerialName("string")
        data class StringArg(val value: String) : Arg
    }
}

