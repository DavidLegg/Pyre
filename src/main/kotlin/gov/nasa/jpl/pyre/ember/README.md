# Pyre - Ember

Ember is the lowest runnable layer of Pyre.
This layer intends to define the bare minimum of types and logic to run a simulation.
This layer intends to be ergonomic to run, but not necessarily to write.

Additions intending to make these types more ergonomic to write should go in higher layers.

Modifications to this layer are highly discouraged, because of the broad ramifications of such a change.
Whenever possible, new functionality should build on top of, rather than supplant, functionality in ember.
