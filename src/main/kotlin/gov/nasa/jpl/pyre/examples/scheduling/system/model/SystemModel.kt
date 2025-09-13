package gov.nasa.jpl.pyre.examples.scheduling.system.model

import gov.nasa.jpl.pyre.examples.scheduling.data.model.BITS_PER_SECOND
import gov.nasa.jpl.pyre.examples.scheduling.data.model.DataModel
import gov.nasa.jpl.pyre.examples.scheduling.geometry.model.GeometryModel
import gov.nasa.jpl.pyre.examples.scheduling.gnc.model.GncModel
import gov.nasa.jpl.pyre.examples.scheduling.imager.model.ImagerModel
import gov.nasa.jpl.pyre.examples.scheduling.power.model.PowerModel
import gov.nasa.jpl.pyre.flame.resources.discrete.unit_aware.QuantityResourceOperations.constant
import gov.nasa.jpl.pyre.flame.units.StandardUnits.BYTE
import gov.nasa.jpl.pyre.flame.units.StandardUnits.SECOND
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.div
import gov.nasa.jpl.pyre.flame.units.UnitAware.Companion.times
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.map
import gov.nasa.jpl.pyre.spark.resources.discrete.DiscreteResourceMonad.pure
import gov.nasa.jpl.pyre.spark.tasks.InitScope
import gov.nasa.jpl.pyre.spark.tasks.InitScope.Companion.subContext

class SystemModel(
    context: InitScope,
    config: Config,
) {
    data class Config(
        val geometryConfig: GeometryModel.Config,
        val gncConfig: GncModel.Config,
        val dataConfig: DataModel.Config,
        val powerConfig: PowerModel.Config,
        val imagerConfig: ImagerModel.Config,
    )

    val geometry: GeometryModel
    val gnc: GncModel
    val data: DataModel
    val power: PowerModel
    val imager: ImagerModel

    init {
        with (context) {
            geometry = GeometryModel(subContext("geometry"), config.geometryConfig)
            val gncInputs = GncModel.Inputs(geometry.pointingDirection)
            gnc = GncModel(subContext("gnc"), config.gncConfig, gncInputs)
            imager = ImagerModel(subContext("imager"), config.imagerConfig)
            val dataInputs = DataModel.Inputs(
                imager.dataRate,
                constant(0.0 * BITS_PER_SECOND))
            data = DataModel(subContext("data"), config.dataConfig, dataInputs)
            // TODO: Connect these up instead of stubbing them out
            //   At a minimum, expose them as mutables and write some activities to poke at them.
            val powerInputs = PowerModel.Inputs(
                gnc.controlMode,
                pure(PowerModel.OnOff.ON),
                imager.mode,
                pure(PowerModel.OnOff.ON),
                pure(PowerModel.OnOff.ON),
            )
            power = PowerModel(subContext("power"), config.powerConfig, powerInputs)
        }
    }
}