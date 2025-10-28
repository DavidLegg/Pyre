# Monte Carlo Support

(Pseudo-)Randomness is a useful property in many simulations.
Using it well poses unique challenges to the modeler.

Chief among these is the need to preserve determinism.
All tasks in the simulation are assumed to be deterministic functions of the simulation state.
We'll call this the "prime assumption" of simulation.
Violating this assumption can lead to undefined behavior.
Furthermore, finding the source of nondeterministic behavior is generally very hard,
since by definition you can't reliably reproduce the bug.

RNG's are chaotic by design - small perturbances in how they're used result in large changes in behavior.
This means that RNG's are perhaps the most dangerous way of introducing nondeterminism into the simulation,
since they're designed to amplify minute differences in behavior.

For this reason, this package introduces an RNG designed for use in simulation.
It stores its state in a resource, subject to the simulator's state-handling system.
(A general RNG would store state outside the simulation, violating the prime assumption.)
This in turn poses a new challenge: correlation.

Since the RNG is now backed by a resource, if two tasks access that RNG at the same time, they'll get the same result,
since by definition they make identical requests to an RNG in an identical state.
This would correlate the behavior of tasks that should be independent.
E.g., suppose two tasks run every 5 minutes, to simulate the possibility of an electrical fault and a computer fault.
If both tasks sample the same RNG at the same times, both get the same numbers, and you'll observe that electrical faults
and computer faults are perfectly correlated.

To avoid correlation, it's enough to build multiple RNGs, one for each task.
With each task sampling its own independent RNG, and each task's RNG seeded independently,
there should be little to no detectable correlation between independent tasks.
To build multiple independent RNGs easily, we choose to allow an RNG to "split" a child RNG off.
That is, we sample the parent RNG, and use that sample to build a child RNG.
