# Pyre

Pyre is a modeling and simulation system written in Kotlin, based on [Aerie](https://github.com/NASA-AMMOS/aerie).

The motivating concerns for Pyre are:
- Simplicity - it attempts to use a small set of base concepts to build up a rigorous, reliable simulation system.
- Rigorous save/restore - any Pyre simulation can be saved to memory or disk and restored, with strong guarantees.
- Memory efficiency - Pyre doesn't maintain history for the simulation, yielding constant memory use w.r.t. plan length.
  This makes Pyre suitable for large, long-duration mission planning simulations.
- Time efficiency - Pyre leverages coroutines to run simulation with minimal overhead.
- Modeling ergonomics - Modelers should focus on faithfully representing the system, not running the simulation.
  The resource, condition, and task system pioneered in Aerie and extended in Pyre
  give modelers flexibility to represent the domain faithfully and efficiently.

## Getting started

To get started, I suggest looking at the [examples](src/main/kotlin/gov/nasa/jpl/pyre/examples).
At the time of writing, [scheduling](src/main/kotlin/gov/nasa/jpl/pyre/examples/scheduling) provides the most complete example.
