@file:UseContextualSerialization(ImagerModel::class)
package gov.nasa.jpl.pyre.examples.scheduling.system.activities

import gov.nasa.jpl.pyre.examples.scheduling.imager.model.ImagerModel
import gov.nasa.jpl.pyre.examples.scheduling.system.model.SystemModel
import gov.nasa.jpl.pyre.general.plans.Activity
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization

@Serializable
@SerialName("ImagerActivity")
class ImagerActivity(
    val subsystemActivity: Activity<ImagerModel>,
) : Activity<SystemModel> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: SystemModel) {
        subsystemActivity.effectModel(model.imager)
    }
}