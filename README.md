# Parakeet

"Planning And Resource Analysis Kotlin Emulation Engineering Toolkit", or "Parakeet", is a modeling and simulation system written in Kotlin, based on [Aerie](https://github.com/NASA-AMMOS/aerie).

The motivating concerns for Parakeet are:
- Simplicity - it attempts to use a small set of base concepts to build up a rigorous, reliable simulation system.
- Rigorous save/restore - any Parakeet simulation can be saved to memory or disk and restored, with strong guarantees.
- Memory efficiency - Parakeet doesn't maintain history for the simulation, yielding constant memory use w.r.t. plan length.
  This makes Parakeet suitable for large, long-duration mission planning simulations.
- Time efficiency - Parakeet leverages coroutines to run simulation with minimal overhead.
- Modeling ergonomics - Modelers should focus on faithfully representing the system, not running the simulation.
  The resource, condition, and task system pioneered in Aerie and extended in Parakeet
  give modelers flexibility to represent the domain faithfully and efficiently.

## Getting started

To get started, I suggest looking at the [tutorials](./tutorials), and can get you up and running the simulator in minutes.

Once you're familiar with the basics, you can look at some of the more complex examples over in [examples](src/main/kotlin/gov/nasa/jpl/parakeet/examples).
At the time of writing,
[orbit](src/main/kotlin/gov/nasa/jpl/parakeet/examples/orbit) provides a very simple example,
and [scheduling](src/main/kotlin/gov/nasa/jpl/parakeet/examples/scheduling) provides the most complete example.

Finally, there are README files throughout the source code documenting various details of the system.
These are probably most valuable to developers looking to contribute to Parakeet.
