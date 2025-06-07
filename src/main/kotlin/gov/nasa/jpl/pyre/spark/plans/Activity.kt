package gov.nasa.jpl.pyre.spark.plans

import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope

interface Activity<M, R> {
    context (SparkTaskScope<R>)
    fun effectModel(model: M): R
}