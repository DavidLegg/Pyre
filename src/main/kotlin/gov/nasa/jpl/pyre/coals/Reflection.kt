package gov.nasa.jpl.pyre.coals

import kotlin.reflect.KClassifier
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType

object Reflection {
    fun KClassifier.withArg(argType: KType): KType =
        this.createType(listOf(KTypeProjection.invariant(argType)))
}