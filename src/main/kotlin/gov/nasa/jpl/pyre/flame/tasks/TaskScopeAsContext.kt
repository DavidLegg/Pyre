package gov.nasa.jpl.pyre.flame.tasks

import gov.nasa.jpl.pyre.ember.CellSet.CellHandle
import gov.nasa.jpl.pyre.ember.Condition
import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.PureTaskStep
import gov.nasa.jpl.pyre.ember.minus
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.flame.plans.FloatingActivity
import gov.nasa.jpl.pyre.flame.plans.GroundedActivity
import gov.nasa.jpl.pyre.flame.plans.defer
import gov.nasa.jpl.pyre.flame.plans.float
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResource
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import gov.nasa.jpl.pyre.spark.tasks.await
import gov.nasa.jpl.pyre.spark.tasks.whenTrue
import kotlinx.serialization.json.JsonElement

// Reformats all the TaskScope functions as functions with a context parameter.
// This fits better with Activity code, which presents the Activity class as receiver,
// and the TaskScope as a context parameter.

context(scope: SparkTaskScope<*>)
suspend fun <V, E> emit(cell: CellHandle<V, E>, effect: E) = scope.emit(cell, effect)

context(scope: SparkTaskScope<*>)
suspend fun report(value: JsonElement) = scope.report(value)

context(scope: SparkTaskScope<*>)
suspend fun delay(time: Duration) = scope.delay(time)

context(scope: SparkTaskScope<*>)
suspend fun await(condition: () -> Condition) = scope.await(condition)

context(scope: SparkTaskScope<*>)
suspend fun <S> spawn(childName: String, child: PureTaskStep<S>) = scope.spawn(childName, child)

context(scope: SparkTaskScope<*>)
suspend fun await(condition: BooleanResource) = scope.await(condition)
