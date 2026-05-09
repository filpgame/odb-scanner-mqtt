package com.frodrigues.jaecoo.obd

object PidRegistry {
    val definitions: Map<Int, PidDefinition> = mapOf(
        0x04 to PidDefinition(0x04, "Engine Load", "%", null) { b ->
            b.u(0) / 2.55
        },
        0x05 to PidDefinition(0x05, "Coolant Temp", "°C", "temperature") { b ->
            b.u(0) - 40.0
        },
        0x06 to PidDefinition(0x06, "Short Fuel Trim B1", "%", null) { b ->
            b.u(0) / 1.28 - 100.0
        },
        0x07 to PidDefinition(0x07, "Long Fuel Trim B1", "%", null) { b ->
            b.u(0) / 1.28 - 100.0
        },
        0x0A to PidDefinition(0x0A, "Fuel Pressure", "kPa", "pressure") { b ->
            3.0 * b.u(0)
        },
        0x0B to PidDefinition(0x0B, "Intake MAP", "kPa", "pressure") { b ->
            b.u(0).toDouble()
        },
        0x0C to PidDefinition(0x0C, "RPM", "rpm", null) { b ->
            (b.u(0) * 256 + b.u(1)) / 4.0
        },
        0x0D to PidDefinition(0x0D, "Speed", "km/h", "speed") { b ->
            b.u(0).toDouble()
        },
        0x0E to PidDefinition(0x0E, "Timing Advance", "°", null) { b ->
            b.u(0) / 2.0 - 64.0
        },
        0x0F to PidDefinition(0x0F, "Intake Air Temp", "°C", "temperature") { b ->
            b.u(0) - 40.0
        },
        0x10 to PidDefinition(0x10, "MAF Rate", "g/s", null) { b ->
            (b.u(0) * 256 + b.u(1)) / 100.0
        },
        0x11 to PidDefinition(0x11, "Throttle Position", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x1F to PidDefinition(0x1F, "Run Time", "s", "duration") { b ->
            (b.u(0) * 256 + b.u(1)).toDouble()
        },
        0x21 to PidDefinition(0x21, "Distance w/ MIL", "km", "distance") { b ->
            (b.u(0) * 256 + b.u(1)).toDouble()
        },
        0x22 to PidDefinition(0x22, "Fuel Rail Pressure", "kPa", "pressure") { b ->
            0.079 * (b.u(0) * 256 + b.u(1))
        },
        0x2C to PidDefinition(0x2C, "Commanded EGR", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x2F to PidDefinition(0x2F, "Fuel Level", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x31 to PidDefinition(0x31, "Distance Since Cleared", "km", "distance") { b ->
            (b.u(0) * 256 + b.u(1)).toDouble()
        },
        0x33 to PidDefinition(0x33, "Barometric Pressure", "kPa", "pressure") { b ->
            b.u(0).toDouble()
        },
        0x42 to PidDefinition(0x42, "Control Module Voltage", "V", "voltage") { b ->
            (b.u(0) * 256 + b.u(1)) / 1000.0
        },
        0x43 to PidDefinition(0x43, "Absolute Load", "%", null) { b ->
            100.0 * (b.u(0) * 256 + b.u(1)) / 255.0
        },
        0x45 to PidDefinition(0x45, "Relative Throttle", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x46 to PidDefinition(0x46, "Ambient Air Temp", "°C", "temperature") { b ->
            b.u(0) - 40.0
        },
        0x49 to PidDefinition(0x49, "Accel Pedal D", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x4A to PidDefinition(0x4A, "Accel Pedal E", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x4C to PidDefinition(0x4C, "Commanded Throttle", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x4D to PidDefinition(0x4D, "Time w/ MIL", "min", "duration") { b ->
            (b.u(0) * 256 + b.u(1)).toDouble()
        },
        0x4E to PidDefinition(0x4E, "Time Since Cleared", "min", "duration") { b ->
            (b.u(0) * 256 + b.u(1)).toDouble()
        },
        0x52 to PidDefinition(0x52, "Ethanol Percent", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x5A to PidDefinition(0x5A, "Relative Accel Pedal", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x5B to PidDefinition(0x5B, "Hybrid Battery", "%", "battery") { b ->
            100.0 * b.u(0) / 255.0
        },
        0x5C to PidDefinition(0x5C, "Oil Temp", "°C", "temperature") { b ->
            b.u(0) - 40.0
        },
        0x5D to PidDefinition(0x5D, "Injection Timing", "°", null) { b ->
            (b.u(0) * 256 + b.u(1)) / 128.0 - 210.0
        },
        0x5E to PidDefinition(0x5E, "Fuel Rate", "L/h", null) { b ->
            (b.u(0) * 256 + b.u(1)) / 20.0
        },
        0x61 to PidDefinition(0x61, "Driver Demand Torque", "%", null) { b ->
            b.u(0) - 125.0
        },
        0x62 to PidDefinition(0x62, "Actual Torque", "%", null) { b ->
            b.u(0) - 125.0
        },
        0x63 to PidDefinition(0x63, "Reference Torque", "N·m", null) { b ->
            (b.u(0) * 256 + b.u(1)).toDouble()
        },
        0xA4 to PidDefinition(0xA4, "Transmission Gear", "ratio", null) { b ->
            (b.u(2) * 256 + b.u(3)) / 1000.0
        },
        0xA6 to PidDefinition(0xA6, "Odometer", "km", "distance") { b ->
            (b.u(0) * 16777216L + b.u(1) * 65536L + b.u(2) * 256L + b.u(3)) / 10.0
        }
    )
}
