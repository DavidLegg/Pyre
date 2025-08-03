package gov.nasa.jpl.pyre.examples.sequencing.primeness

enum class SideIndicator {
    A,
    B,
    PRIME,
    BACKUP;

    fun resolve(primeSide: Side): Side = when (this) {
        A -> Side.A
        B -> Side.B
        PRIME -> primeSide
        BACKUP -> primeSide.opposite()
    }
}