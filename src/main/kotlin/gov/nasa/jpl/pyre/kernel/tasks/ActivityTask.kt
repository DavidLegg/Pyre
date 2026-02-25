package gov.nasa.jpl.pyre.kernel.tasks

import gov.nasa.jpl.pyre.kernel.Name
import kotlinx.serialization.Serializable

// TODO: Remove this, and just use PureTaskStep instead

/**
 * A kind of [Task] constructed independently of the model.
 *
 * This class exists primarily to facilitate serialization of activities for saving a [gov.nasa.jpl.pyre.kernel.Snapshot] to disk.
 */
@Serializable
data class ActivityTask(
    override val name: Name,
    val activity: PureTaskStep
) : Task by PureTask(name, activity)
