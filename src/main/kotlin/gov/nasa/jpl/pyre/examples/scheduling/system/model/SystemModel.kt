package gov.nasa.jpl.pyre.examples.scheduling.system.model

import gov.nasa.jpl.pyre.ember.Duration.Companion.DAY
import gov.nasa.jpl.pyre.ember.Duration.Companion.HOUR
import gov.nasa.jpl.pyre.ember.times
import gov.nasa.jpl.pyre.examples.scheduling.data.model.DataModel
import gov.nasa.jpl.pyre.examples.scheduling.geometry.model.GeometryModel
import gov.nasa.jpl.pyre.examples.scheduling.geometry.model.GeometryModel.PointingTarget
import gov.nasa.jpl.pyre.examples.scheduling.gnc.model.GncModel
import gov.nasa.jpl.pyre.examples.scheduling.gnc.model.GncModel.BodyAxis
import gov.nasa.jpl.pyre.examples.scheduling.imager.model.ImagerModel
import gov.nasa.jpl.pyre.examples.scheduling.power.model.PowerModel
import gov.nasa.jpl.pyre.examples.scheduling.power.model.PowerModel.OnOff
import gov.nasa.jpl.pyre.examples.scheduling.telecom.model.TelecomModel
import gov.nasa.jpl.pyre.spark.resources.discrete.BooleanResourceOperations.and
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceOperations.equals
import gov.nasa.jpl.pyre.spark.tasks.InitScope
import gov.nasa.jpl.pyre.spark.tasks.InitScope.Companion.subContext
import gov.nasa.jpl.pyre.spark.tasks.Reactions.every
import gov.nasa.jpl.pyre.spark.tasks.ResourceScope.Companion.now

class SystemModel(
    context: InitScope,
    config: Config,
) {
    data class Config(
        val geometryConfig: GeometryModel.Config,
        val gncConfig: GncModel.Config,
        val telecomConfig: TelecomModel.Config,
        val dataConfig: DataModel.Config,
        val powerConfig: PowerModel.Config,
        val imagerConfig: ImagerModel.Config,
    )

    val geometry: GeometryModel
    val gnc: GncModel
    val telecom: TelecomModel
    val data: DataModel
    val power: PowerModel
    val imager: ImagerModel

    init {
        with (context) {
            geometry = GeometryModel(subContext("geometry"), config.geometryConfig)

            val gncInputs = GncModel.Inputs(
                pointingTargets = geometry.pointingDirection
            )
            gnc = GncModel(subContext("gnc"), config.gncConfig, gncInputs)

            // Note that because the integration layer is a fully-fledged model, we can do ad-hoc conversion
            // logic on the resources to connect the models together. This avoids tightly coupling subsystem models.
            val telecomInputs = TelecomModel.Inputs(
                isEarthPointed = (
                        (gnc.primaryBodyAxis equals BodyAxis.HGA)
                        and (gnc.primaryPointingTarget equals PointingTarget.EARTH)
                        and gnc.isSettled
                )
            )
            telecom = TelecomModel(subContext("telecom"), config.telecomConfig, telecomInputs)

            imager = ImagerModel(subContext("imager"), config.imagerConfig)

            val dataInputs = DataModel.Inputs(
                dataRate = imager.dataRate,
                downlinkDataRate = telecom.realizedDataRate,
            )
            data = DataModel(subContext("data"), config.dataConfig, dataInputs)

            // TODO: Connect these up instead of stubbing them out
            //   At a minimum, expose them as mutables and write some activities to poke at them.
            val powerInputs = PowerModel.Inputs(
                gnc.controlMode,
                pure(OnOff.ON),
                map(telecom.radioPoweredOn) { if (it) OnOff.ON else OnOff.OFF },
                imager.mode,
                pure(OnOff.ON),
                pure(OnOff.OFF),
            )
            power = PowerModel(subContext("power"), config.powerConfig, powerInputs)
        }
    }
}