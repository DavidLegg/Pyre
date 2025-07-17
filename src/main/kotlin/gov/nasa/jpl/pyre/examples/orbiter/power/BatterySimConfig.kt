package gov.nasa.jpl.pyre.examples.orbiter.power

data class BatterySimConfig(
    val batteryCapacity: Double = DEFAULT_BATTERY_CAPACITY,
    val busVoltage: Double = DEFAULT_BUS_VOLTAGE,
    val initialSOC: Double = DEFAULT_INITIAL_SOC) {

    companion object {
        // Battery capacity of on-board battery in (Ah)
        // See VERITAS CSR Table F.2-9 (EOL Storage Capability)
        const val DEFAULT_BATTERY_CAPACITY: Double = 94.5

        // Voltage the spacecraft uses to distribute power to its
        // various components (V). Also known as battery bus voltage.
        const val DEFAULT_BUS_VOLTAGE: Double = 28.0

        // Initial value for the state of charge of the battery
        const val DEFAULT_INITIAL_SOC: Double = 100.0
    }
}
