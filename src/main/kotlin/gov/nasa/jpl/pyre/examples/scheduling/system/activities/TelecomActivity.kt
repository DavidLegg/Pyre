@file:UseContextualSerialization(TelecomModel::class)
package gov.nasa.jpl.pyre.examples.scheduling.system.activities

import gov.nasa.jpl.pyre.examples.scheduling.system.model.SystemModel
import gov.nasa.jpl.pyre.examples.scheduling.telecom.model.TelecomModel
import gov.nasa.jpl.pyre.foundation.plans.Activity
import gov.nasa.jpl.pyre.foundation.tasks.TaskScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization

@Serializable
@SerialName("TelecomActivity")
class TelecomActivity(
    val subsystemActivity: Activity<TelecomModel>,
) : Activity<SystemModel> {
    context(scope: TaskScope)
    override suspend fun effectModel(model: SystemModel) {
        subsystemActivity.effectModel(model.telecom)
    }
}