package org.example.gov.nasa.jpl.pyre.conditions

import org.example.gov.nasa.jpl.pyre.io.JsonValue

fun interface FinconCollector {
    fun report(keys: Sequence<String>, value: JsonValue)
    fun report(vararg keys: String, value: JsonValue) = report(keys.asSequence(), value)
}