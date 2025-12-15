package gov.nasa.jpl.pyre.examples.lander.models.dsn

import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResourceOperations.and
import gov.nasa.jpl.pyre.foundation.resources.discrete.BooleanResourceOperations.not
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.equals
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.set
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.await
import gov.nasa.jpl.pyre.foundation.tasks.Reactions.whenever
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.spawn
import gov.nasa.jpl.pyre.foundation.tasks.InitScope.Companion.subContext

class DSNModel(
    context: InitScope,
) {
    enum class DSNStation {
        Canberra,
        Madrid,
        Goldstone,
        None
    }

    enum class VisibilityEnum {
        InView,
        Hidden
    }

    enum class AllocatedEnum {
        Allocated,
        NotAllocated
    }

    private val currentStation: MutableDiscreteResource<DSNStation>
    private val stations: Map<DSNStation, DsnStationModel>

    init {
        with (context) {
            currentStation = discreteResource("allocstation", DSNStation.None)
            stations = DSNStation.entries
                .filter { it != DSNStation.None }
                .associateWith { DsnStationModel(subContext(it.toString())) }

            for ((stationId, stationModel) in stations) {
                val shouldBeCurrentStation = ((stationModel.visible equals VisibilityEnum.InView)
                        and (stationModel.allocated equals AllocatedEnum.Allocated))
                spawn("Monitor $stationId", whenever (shouldBeCurrentStation) {
                    currentStation.set(stationId)
                    await(shouldBeCurrentStation.not())
                    currentStation.set(DSNStation.None)
                })
            }
        }
    }

    operator fun get(station: DSNStation): DsnStationModel = stations.getValue(station)

    class DsnStationModel(
        context: InitScope,
    ) {
        val allocated: DiscreteResource<AllocatedEnum>
        val visible: DiscreteResource<VisibilityEnum>

        init {
            with (context) {
                allocated = discreteResource("allocated", AllocatedEnum.NotAllocated).registered()
                visible = discreteResource("visible", VisibilityEnum.Hidden).registered()
            }
        }
    }
}