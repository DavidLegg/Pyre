package gov.nasa.jpl.pyre.examples.model_interfaces.lander

import gov.nasa.jpl.pyre.ember.Serializer
import gov.nasa.jpl.pyre.examples.model_interfaces.lander.models.time.Clocks
import gov.nasa.jpl.pyre.examples.model_interfaces.lander.models.time.Time
import gov.nasa.jpl.pyre.flame.plans.ActivitySerializer
import gov.nasa.jpl.pyre.flame.plans.GroundedActivity
import gov.nasa.jpl.pyre.flame.plans.Model
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class Mission(context: SparkInitContext) : Model<Mission> {
    val config: Configuration
    val clocks: Clocks
    // val dataModel: DataModel
    // val dsnModel: DSNModel
    // val wakeModel: WakeModel
    // val commModel: CommModel
    // val powerModel: PowerModel
    // val engModel: EngModel
    // val HeatProbeModel: HeatProbeModel
    // val idsModel: IDSModel
    // val apssModel: APSSModel
    // val seisModel: SeisModel

    init {
        with (context) {
            config = Configuration()
            // Mission convention: The first simulation shall be run with this hard-coded start time.
            // The simulation clock will thereafter be relative to this epoch.
            clocks = Clocks(context, Time.fromUTC("2020-01-01T00:00:00Z"))
        }
    }

    override fun activitySerializer(): Serializer<GroundedActivity<Mission, *>> {
        return ActivitySerializer()
        // TODO: Add activities
    }
}