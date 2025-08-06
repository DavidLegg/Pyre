package gov.nasa.jpl.pyre.examples.lander

import gov.nasa.jpl.pyre.examples.lander.models.apss.APSSModel
import gov.nasa.jpl.pyre.examples.lander.models.comm.CommModel
import gov.nasa.jpl.pyre.examples.lander.models.data.DataModel
import gov.nasa.jpl.pyre.examples.lander.models.dsn.DSNModel
import gov.nasa.jpl.pyre.examples.lander.models.eng.EngModel
import gov.nasa.jpl.pyre.examples.lander.models.heatprobe.HeatProbeModel
import gov.nasa.jpl.pyre.examples.lander.models.ids.IDSModel
import gov.nasa.jpl.pyre.examples.lander.models.power.PowerModel
import gov.nasa.jpl.pyre.examples.lander.models.seis.SeisModel
import gov.nasa.jpl.pyre.examples.lander.models.time.Clocks
import gov.nasa.jpl.pyre.examples.lander.models.wake.WakeModel
import gov.nasa.jpl.pyre.flame.tasks.subContext
import gov.nasa.jpl.pyre.spark.tasks.SparkInitScope
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class Mission(context: SparkInitScope) {
    val config: Configuration
    val clocks: Clocks
    val dataModel: DataModel
    val dsnModel: DSNModel
    val wakeModel: WakeModel
    val commModel: CommModel
    val powerModel: PowerModel
    val engModel: EngModel
    val HeatProbeModel: HeatProbeModel
    val idsModel: IDSModel
    val apssModel: APSSModel
    val seisModel: SeisModel

    init {
        // TODO: Consider how to load configuration from environment with each run?
        //   Is this something that even makes sense in Pyre?
        with (context) {
            config = Configuration()
            clocks = Clocks(subContext("time"))
            dataModel = DataModel(subContext("data"))
            dsnModel = DSNModel(subContext("dsn"))
            wakeModel = WakeModel(subContext("wake"))
            commModel = CommModel(subContext("comm"))
            powerModel = PowerModel(subContext("power"))
            engModel = EngModel(subContext("eng"))
            HeatProbeModel = HeatProbeModel(subContext("HeatProbe"))
            idsModel = IDSModel(subContext("ids"))
            apssModel = APSSModel(subContext("apss"))
            seisModel = SeisModel(subContext("seis"))
        }

    }
}