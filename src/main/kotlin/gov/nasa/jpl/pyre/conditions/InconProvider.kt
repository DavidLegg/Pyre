package org.example.gov.nasa.jpl.pyre.conditions

import org.example.gov.nasa.jpl.pyre.io.JsonValue

fun interface InconProvider {
    fun get(keys: Sequence<String>): JsonValue?
    fun get(vararg keys: String): JsonValue? = get(keys.asSequence())
}