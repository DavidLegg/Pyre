# Scheduling Example

One of the use cases for a full system model is "scheduling" - building up a plan from a set of constraints or rules.
For example, TCM timing is determined by a navigation team, then comm passes are layered on top of that,
then science activities, etc.

This example demonstrates a few important techniques for real-world models:
- Decoupling -
  A real-world system model comprises a number of sub-models, usually one per subsystem.
  These models interact with each other, but for the sake of building and maintaining them, they need to be decoupled.
  This is achieved by having each model accept "inputs" in its constructor, usually resources.
  Crucially, subsystem models *do not* accept other models as inputs, as this would tightly couple them.

  Note that the outputs of one subsystem don't need to align perfectly with the inputs of another.
  The flexible and expressive derived resource system allows the [system model](system/model/SystemModel.kt) to
  adapt resources when connecting subsystems, acting as a buffer between subsystems.
  This reduces the "abstraction leakage" between subsystem models.
- Unit testing -
  Having decoupled subsystem models by defining their inputs as a set of resources given to their constructor,
  a subsystem model can be constructed and run in isolation in a unit test.
  The [data model unit tests](/../../test/kotlin/gov/nasa/jpl/pyre/examples/scheduling/data/model/DataModelTest.kt)
  provide a good example of this - the input data rate and downlink data rate are constructed as mutable resources in a
  test model, and are driven directly by test tasks to explore the behavior of the data model independent of the rest of the system.
- Scheduling -
  The [main](Main.kt) class for this example demonstrates what scheduling might look like.
  This class accepts a comm and science opportunity schedule ([generated randomly](schedule_gen.py) for this example),
  and uses those to build up the activity plan.

  This includes doing backtracking search to schedule turns that end at a particular time, even though the duration of
  the turn is not known until simulation.
  The rigorous save/restore functionality built into Pyre from the get-go gives us a "checkpoint" capability,
  which lets us re-simulate only a small window of time around a turn, making backtracking search fairly efficient.
- Unit-awareness -
  Finally, this model makes extensive use of the unit-awareness system, which provides efficient unit-safe computations.
  Notice how the data system can merely ask for a data rate, for example, without having to specify units.
  This reduces both the likelihood of making a unit conversion mistake, and the mental overhead on the modeler of
  remembering to check what units each state is supposed to be in.
- Simulation -
  In addition to scheduling, the [main](Main.kt) class also allows for simulation from the command line.
  This is a generally recommended pattern, and one could even extend this to running different variations of the model,
  like just running a single subsystem.
  Since simulation is just a function call, the developer is free to run simulation as a subroutine in a larger program
  as needed to meet mission needs.
