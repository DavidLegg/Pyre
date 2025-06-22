package gov.nasa.jpl.pyre.examples.telecom

import gov.nasa.jpl.pyre.ember.Duration
import gov.nasa.jpl.pyre.ember.Serializer
import gov.nasa.jpl.pyre.flame.resources.discrete.ListResourceOperations.isNotEmpty
import gov.nasa.jpl.pyre.flame.resources.discrete.ListResourceOperations.listResource
import gov.nasa.jpl.pyre.flame.resources.discrete.ListResourceOperations.plusAssign
import gov.nasa.jpl.pyre.flame.resources.discrete.ListResourceOperations.pop
import gov.nasa.jpl.pyre.flame.resources.discrete.ListResourceOperations.push
import gov.nasa.jpl.pyre.flame.resources.discrete.MutableListResource
import gov.nasa.jpl.pyre.flame.resources.unstructured.UnstructuredResource
import gov.nasa.jpl.pyre.spark.resources.FullDynamics
import gov.nasa.jpl.pyre.spark.resources.discrete.EnumResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.discrete.IntResource
import gov.nasa.jpl.pyre.spark.resources.discrete.IntResourceOperations.discreteResource
import gov.nasa.jpl.pyre.spark.resources.getValue
import gov.nasa.jpl.pyre.spark.tasks.SparkInitContext
import gov.nasa.jpl.pyre.spark.tasks.SparkTaskScope
import gov.nasa.jpl.pyre.spark.tasks.task
import gov.nasa.jpl.pyre.spark.tasks.whenever

class CommunicationNode<M>(
    context: SparkInitContext,
    val name: String,
    val messageHandler: suspend SparkTaskScope<*>.(M) -> Unit,
    messageSerializer: Serializer<M>,
) {
    class CommunicationLink(val delay: UnstructuredResource<Duration>)

    private val messagesSent: IntResource
    private val messageQueue: MutableListResource<M>
    private val links: MutableMap<CommunicationNode<*>, CommunicationLink> = mutableMapOf()

    init {
        with (context) {
            messagesSent = discreteResource("$name.messagesSent", 0)
            messageQueue = listResource("$name.messageQueue", elementSerializer = messageSerializer)

            spawn("$name.messageHandler", whenever(messageQueue.isNotEmpty()) {
                messageHandler(messageQueue.pop())
            })
        }
    }

    fun <M2> linkTo(other: CommunicationNode<M2>, link: CommunicationLink) {
        links[other] = link
    }

    context (SparkTaskScope<*>)
    suspend fun <M2> sendTo(other: CommunicationNode<M2>, message: M2) {
        val link = requireNotNull(links[other]) { "No link established from $name to ${other.name}" }
        // Spawn a task to send the message in the background while the caller may continue in the foreground
        spawn("$name -> ${other.name} [${messagesSent.getValue()}]", task {
            delay(link.delay.getValue())
            other.messageQueue += message
        })
    }
}
