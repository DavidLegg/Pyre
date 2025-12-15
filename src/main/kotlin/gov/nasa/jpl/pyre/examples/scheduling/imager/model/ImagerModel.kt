package gov.nasa.jpl.pyre.examples.scheduling.imager.model

import gov.nasa.jpl.pyre.examples.scheduling.data.model.BITS_PER_SECOND
import gov.nasa.jpl.pyre.foundation.reporting.Reporting.registered
import gov.nasa.jpl.pyre.general.units.quantity_resource.QuantityResource
import gov.nasa.jpl.pyre.general.units.quantity.Quantity
import gov.nasa.jpl.pyre.general.units.Unit
import gov.nasa.jpl.pyre.general.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.foundation.resources.discrete.DiscreteResourceOperations.discreteResource
import gov.nasa.jpl.pyre.foundation.resources.discrete.MutableDiscreteResource
import gov.nasa.jpl.pyre.foundation.tasks.InitScope
import gov.nasa.jpl.pyre.general.units.unit_aware_resource.UnitAwareResourceOperations.named
import gov.nasa.jpl.pyre.general.units.unit_aware_resource.UnitAwareResourceOperations.registered
import gov.nasa.jpl.pyre.general.units.unit_aware_resource.UnitAwareResourceOperations.unitAware

// We can define a brand new dimension, like "image", to use the unit system to check calculations around images.
// Note that this will be a different "image" unit and dimension than any other, even if somewhere else we define
// another unit also called "image" with dimension "Image".
/**
 * A single image taken by [ImagerModel]
 */
val IMAGE = Unit.base("image", "Image")

class ImagerModel(
    context: InitScope,
    config: Config,
) {
    data class Config(
        /**
         * Units: Information / [IMAGE]
         */
        val imageSize: Quantity,

        /**
         * Units: [IMAGE] / Time
         */
        val imageRate: Quantity,
    )

    enum class ImagerMode {
        OFF,
        WARMUP,
        STANDBY,
        IMAGING,
    }

    val mode: MutableDiscreteResource<ImagerMode>
    val dataRate: QuantityResource

    init {
        with (context) {
            unitAware {
                mode = discreteResource("mode", ImagerMode.OFF).registered()
                val imagingDataRate_bps = (config.imageRate * config.imageSize).valueIn(BITS_PER_SECOND)
                dataRate = (map(mode) {
                    when (it) {
                        ImagerMode.OFF -> 0.0
                        ImagerMode.WARMUP -> 8.0
                        ImagerMode.STANDBY -> 8.0
                        ImagerMode.IMAGING -> imagingDataRate_bps
                    }
                } * BITS_PER_SECOND)
                    .named { "data_rate" }
                    .registered()
            }
        }
    }
}