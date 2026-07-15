package gov.nasa.jpl.parakeet.examples.sequencing.primeness

enum class Side {
    A,
    B;

    fun opposite(): Side = when (this) {
        A -> B
        B -> A
    }
}