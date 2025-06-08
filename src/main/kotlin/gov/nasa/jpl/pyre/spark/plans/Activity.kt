package gov.nasa.jpl.pyre.spark.plans

import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope

interface Activity<M, R> {
    val name: String
    val typeName: String

    context (SparkTaskScope<R>)
    suspend fun effectModel(model: M): R
}