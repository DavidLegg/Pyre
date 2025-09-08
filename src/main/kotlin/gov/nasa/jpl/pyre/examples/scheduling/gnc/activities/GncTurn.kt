package gov.nasa.jpl.pyre.examples.scheduling.gnc.activities

import gov.nasa.jpl.pyre.ember.Duration.Companion.SECOND
import gov.nasa.jpl.pyre.ember.times
import gov.nasa.jpl.pyre.examples.scheduling.geometry.model.GeometryModel
import gov.nasa.jpl.pyre.examples.scheduling.geometry.model.GeometryModel.PointingTarget
import gov.nasa.jpl.pyre.examples.scheduling.gnc.model.GncModel
import gov.nasa.jpl.pyre.examples.scheduling.gnc.model.GncModel.BodyAxis
import gov.nasa.jpl.pyre.flame.plans.Activity
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResourceOperations.VsQuantity.lessThanOrEquals
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.spark.tasks.Reactions.await
import gov.nasa.jpl.pyre.spark.tasks.TaskScope
import gov.nasa.jpl.pyre.spark.tasks.TaskScope.Companion.delay

class GncTurn(
    val primaryPointingTarget: PointingTarget,
    val secondaryPointingTarget: PointingTarget,
    val primaryBodyAxis: BodyAxis,
    val secondaryBodyAxis: BodyAxis,
) : Activity<GncModel> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: GncModel) {
        model.primaryPointingTarget.set(primaryPointingTarget)
        model.secondaryPointingTarget.set(secondaryPointingTarget)
        model.primaryBodyAxis.set(primaryBodyAxis)
        model.secondaryBodyAxis.set(secondaryBodyAxis)
        // Wait to achieve the new attitude:
        await(model.pointingError lessThanOrEquals model.config.pointingErrorTolerance)
    }
}