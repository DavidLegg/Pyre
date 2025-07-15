package gov.nasa.jpl.pyre.ember

import gov.nasa.jpl.pyre.ember.FinconCollector.Companion.withPrefix
import gov.nasa.jpl.pyre.ember.FinconCollector.Companion.withSuffix
import gov.nasa.jpl.pyre.ember.InconProvider.Companion.withPrefix
import gov.nasa.jpl.pyre.ember.InconProvider.Companion.withSuffix

interface Conditions : FinconCollector, InconProvider {
    companion object {
        fun Conditions.withPrefix(key: String): Conditions = object : Conditions,
            FinconCollector by (this as FinconCollector).withPrefix(key),
            InconProvider by (this as InconProvider).withPrefix(key) {}

        fun Conditions.withSuffix(key: String): Conditions = object : Conditions,
            FinconCollector by (this as FinconCollector).withSuffix(key),
            InconProvider by (this as InconProvider).withSuffix(key) {}
    }
}