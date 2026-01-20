# Orbit Example

This small example model simulates the motion of the Earth and Moon due to gravity, using a Verlet numerical integrator.
Note that this is *not* meant to be a high-accuracy orbital simulation.
It was written to do benchmarking against Aerie, as a nearly identical model can be written in Aerie, and both can be run indefinitely without generating a plan.
Numerical integration provides constant and nontrivial work-per-unit-simulated-time, making it suitable for measuring performance.

As an example to learn from, this model demonstrates a few features of Pyre, like the use of daemon tasks, resources, unit-awareness, and how to efficiently direct output to file.
