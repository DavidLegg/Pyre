package gov.nasa.jpl.parakeet.kernel.incremental

import gov.nasa.jpl.pyre.kernel.incremental.IncSimNode.AwaitCompleteNode
import gov.nasa.jpl.pyre.kernel.incremental.IncSimNode.AwaitNode
import gov.nasa.jpl.pyre.kernel.incremental.IncSimNode.TaskNode
import gov.nasa.jpl.pyre.kernel.incremental.IncSimNode.YieldingStepNode

object IncSimNodeOperations {
    fun TaskNode.thisAndPriorNodes() = generateSequence(this) { it.prior }
    fun TaskNode.thisAndNextNodes() = generateSequence(this) { it.next }
    fun TaskNode.priorNodes() = thisAndPriorNodes().drop(1)
    fun TaskNode.nextNodes() = thisAndNextNodes().drop(1)
    fun TaskNode.awaitGroup(): Sequence<YieldingStepNode> =
        (priorNodes().takeWhile { it is AwaitNode } +
                thisAndNextNodes().takeWhile { it is AwaitNode || it is AwaitCompleteNode })
            .map { it as YieldingStepNode }
}