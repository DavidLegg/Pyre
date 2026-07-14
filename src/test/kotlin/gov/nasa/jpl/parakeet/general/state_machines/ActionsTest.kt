package gov.nasa.jpl.parakeet.general.state_machines

import gov.nasa.jpl.pyre.general.state_machines.Actions.stateActions
import gov.nasa.jpl.pyre.general.state_machines.Actions.transitionActions
import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test
import kotlin.test.assertSame

class ActionsTest {
    @Test
    fun `state actions returns null for states not listed`() {
        val actions = stateActions(1 to { })
        assertNull(actions(2))
    }

    @Test
    fun `state actions returns the action for listed states`() {
        val result1: Action = {}
        val result2: Action = {}
        val actions = stateActions(1 to result1, 2 to result2)
        assertSame(result1, actions(1))
        assertSame(result2, actions(2))
    }

    @Test
    fun `transition actions returns null for transitions not listed`() {
        val actions = transitionActions((1 to 2) to {})
        assertNull(actions(1, 3))
    }

    @Test
    fun `transition actions returns the action for listed transitions`() {
        val result1: Action = {}
        val result2: Action = {}
        val actions = transitionActions((1 to 2) to result1, (2 to 3) to result2)
        assertSame(result1, actions(1, 2))
        assertSame(result2, actions(2, 3))
    }
}