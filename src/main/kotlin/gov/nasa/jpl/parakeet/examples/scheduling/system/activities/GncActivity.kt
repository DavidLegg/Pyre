@file:UseContextualSerialization(GncModel::class)

package gov.nasa.jpl.parakeet.examples.scheduling.system.activities

import gov.nasa.jpl.parakeet.examples.scheduling.gnc.model.GncModel
import gov.nasa.jpl.parakeet.examples.scheduling.system.model.SystemModel
import gov.nasa.jpl.parakeet.foundation.plans.Activity
import gov.nasa.jpl.parakeet.foundation.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization

@Serializable
@SerialName("GncActivity")
class GncActivity(
    val subsystemActivity: Activity<GncModel>,
) : Activity<SystemModel> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: SystemModel) {
        subsystemActivity.effectModel(model.gnc)
    }
}