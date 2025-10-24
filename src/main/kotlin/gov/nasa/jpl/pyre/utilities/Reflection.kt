package gov.nasa.jpl.pyre.utilities

import kotlin.reflect.KClassifier
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType

object Reflection {
    // TODO: Also port over the performance analysis and debugging tools I had in Aerie and further optimize things.
    fun KClassifier.withArg(argType: KType): KType =
        this.createType(listOf(KTypeProjection.invariant(argType)))
}