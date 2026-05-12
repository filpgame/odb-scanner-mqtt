package com.frodrigues.odbmqtt.obd

object PidRegistry {

    val definitions: Map<Int, PidDefinition> = mapOf(
        // ── Engine / Load ─────────────────────────────────────────────────────
        0x04 to PidDefinition(0x04, "Engine Load", "%", null) { b ->
            b.u(0) / 2.55
        },
        0x43 to PidDefinition(0x43, "Absolute Load", "%", null) { b ->
            100.0 * (b.u(0) * 256 + b.u(1)) / 255.0
        },

        // ── Temperatures ──────────────────────────────────────────────────────
        0x05 to PidDefinition(0x05, "Coolant Temp", "°C", "temperature") { b ->
            b.u(0) - 40.0
        },
        0x0F to PidDefinition(0x0F, "Intake Air Temp", "°C", "temperature") { b ->
            b.u(0) - 40.0
        },
        0x46 to PidDefinition(0x46, "Ambient Air Temp", "°C", "temperature") { b ->
            b.u(0) - 40.0
        },
        0x5C to PidDefinition(0x5C, "Oil Temp", "°C", "temperature") { b ->
            b.u(0) - 40.0
        },

        // ── Fuel Trims ────────────────────────────────────────────────────────
        0x06 to PidDefinition(0x06, "Short Fuel Trim B1", "%", null) { b ->
            b.u(0) / 1.28 - 100.0
        },
        0x07 to PidDefinition(0x07, "Long Fuel Trim B1", "%", null) { b ->
            b.u(0) / 1.28 - 100.0
        },
        0x08 to PidDefinition(0x08, "Short Fuel Trim B2", "%", null) { b ->
            b.u(0) / 1.28 - 100.0
        },
        0x09 to PidDefinition(0x09, "Long Fuel Trim B2", "%", null) { b ->
            b.u(0) / 1.28 - 100.0
        },

        // ── Pressure ──────────────────────────────────────────────────────────
        0x0A to PidDefinition(0x0A, "Fuel Pressure", "kPa", "pressure") { b ->
            3.0 * b.u(0)
        },
        0x0B to PidDefinition(0x0B, "Intake MAP", "kPa", "pressure") { b ->
            b.u(0).toDouble()
        },
        0x22 to PidDefinition(0x22, "Fuel Rail Pressure (rel)", "kPa", "pressure") { b ->
            0.079 * (b.u(0) * 256 + b.u(1))
        },
        0x23 to PidDefinition(0x23, "Fuel Rail Gauge Pressure", "kPa", "pressure") { b ->
            10.0 * (b.u(0) * 256 + b.u(1))
        },
        0x33 to PidDefinition(0x33, "Barometric Pressure", "kPa", "pressure") { b ->
            b.u(0).toDouble()
        },
        0x59 to PidDefinition(0x59, "Fuel Rail Abs Pressure", "kPa", "pressure") { b ->
            10.0 * (b.u(0) * 256 + b.u(1))
        },

        // ── Speed / RPM ───────────────────────────────────────────────────────
        0x0C to PidDefinition(0x0C, "RPM", "rpm", null) { b ->
            (b.u(0) * 256 + b.u(1)) / 4.0
        },
        0x0D to PidDefinition(0x0D, "Speed", "km/h", "speed") { b ->
            b.u(0).toDouble()
        },

        // ── Timing / Ignition ─────────────────────────────────────────────────
        0x0E to PidDefinition(0x0E, "Timing Advance", "°", null) { b ->
            b.u(0) / 2.0 - 64.0
        },
        0x5D to PidDefinition(0x5D, "Injection Timing", "°", null) { b ->
            (b.u(0) * 256 + b.u(1)) / 128.0 - 210.0
        },

        // ── Air Flow ──────────────────────────────────────────────────────────
        0x10 to PidDefinition(0x10, "MAF Rate", "g/s", null) { b ->
            (b.u(0) * 256 + b.u(1)) / 100.0
        },
        0x50 to PidDefinition(0x50, "Max MAF", "g/s", null) { b ->
            b.u(0) * 10.0
        },

        // ── Throttle ──────────────────────────────────────────────────────────
        0x11 to PidDefinition(0x11, "Throttle Position", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x45 to PidDefinition(0x45, "Relative Throttle", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x47 to PidDefinition(0x47, "Absolute Throttle B", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x48 to PidDefinition(0x48, "Absolute Throttle C", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x4C to PidDefinition(0x4C, "Commanded Throttle", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },

        // ── Accelerator Pedal ─────────────────────────────────────────────────
        0x49 to PidDefinition(0x49, "Accel Pedal D", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x4A to PidDefinition(0x4A, "Accel Pedal E", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x4B to PidDefinition(0x4B, "Accel Pedal F", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x5A to PidDefinition(0x5A, "Relative Accel Pedal", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },

        // ── Fuel ──────────────────────────────────────────────────────────────
        0x2F to PidDefinition(0x2F, "Fuel Level", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x5E to PidDefinition(0x5E, "Fuel Rate", "L/h", null) { b ->
            (b.u(0) * 256 + b.u(1)) / 20.0
        },
        0x51 to PidDefinition(0x51, "Fuel Type", "", null) { b ->
            b.u(0).toDouble()
        },
        0x52 to PidDefinition(0x52, "Ethanol Percent", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },

        // ── O2 Sensors ────────────────────────────────────────────────────────
        0x14 to PidDefinition(0x14, "O2 Sensor 1 Voltage", "V", "voltage") { b ->
            b.u(0) / 200.0
        },
        0x15 to PidDefinition(0x15, "O2 Sensor 2 Voltage", "V", "voltage") { b ->
            b.u(0) / 200.0
        },

        // ── Air-Fuel Ratio ────────────────────────────────────────────────────
        0x44 to PidDefinition(0x44, "Commanded AFR", "ratio", null) { b ->
            2.0 * (b.u(0) * 256 + b.u(1)) / 65536.0
        },

        // ── EGR / Evap ────────────────────────────────────────────────────────
        0x2C to PidDefinition(0x2C, "Commanded EGR", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },
        0x2D to PidDefinition(0x2D, "EGR Error", "%", null) { b ->
            (100.0 * b.u(0) / 128.0) - 100.0
        },
        0x2E to PidDefinition(0x2E, "Commanded Evap Purge", "%", null) { b ->
            100.0 * b.u(0) / 255.0
        },

        // ── Voltage ───────────────────────────────────────────────────────────
        0x42 to PidDefinition(0x42, "Control Module Voltage", "V", "voltage") { b ->
            (b.u(0) * 256 + b.u(1)) / 1000.0
        },

        // ── Counters / Distance ───────────────────────────────────────────────
        0x1F to PidDefinition(0x1F, "Run Time", "s", "duration") { b ->
            (b.u(0) * 256 + b.u(1)).toDouble()
        },
        0x21 to PidDefinition(0x21, "Distance w/ MIL", "km", "distance") { b ->
            (b.u(0) * 256 + b.u(1)).toDouble()
        },
        0x30 to PidDefinition(0x30, "Warm-ups Since Cleared", "", null) { b ->
            b.u(0).toDouble()
        },
        0x31 to PidDefinition(0x31, "Distance Since Cleared", "km", "distance") { b ->
            (b.u(0) * 256 + b.u(1)).toDouble()
        },
        0x4D to PidDefinition(0x4D, "Time w/ MIL", "min", "duration") { b ->
            (b.u(0) * 256 + b.u(1)).toDouble()
        },
        0x4E to PidDefinition(0x4E, "Time Since Cleared", "min", "duration") { b ->
            (b.u(0) * 256 + b.u(1)).toDouble()
        },
        0xA6 to PidDefinition(0xA6, "Odometer", "km", "distance") { b ->
            (b.u(0) * 16777216L + b.u(1) * 65536L + b.u(2) * 256L + b.u(3)) / 10.0
        },

        // ── Torque ────────────────────────────────────────────────────────────
        0x61 to PidDefinition(0x61, "Driver Demand Torque", "%", null) { b ->
            b.u(0) - 125.0
        },
        0x62 to PidDefinition(0x62, "Actual Torque", "%", null) { b ->
            b.u(0) - 125.0
        },
        0x63 to PidDefinition(0x63, "Reference Torque", "N·m", null) { b ->
            (b.u(0) * 256 + b.u(1)).toDouble()
        },

        // ── Hybrid / Battery ──────────────────────────────────────────────────
        0x5B to PidDefinition(0x5B, "Hybrid Battery", "%", "battery") { b ->
            100.0 * b.u(0) / 255.0
        },

        // ── Transmission ──────────────────────────────────────────────────────
        0xA4 to PidDefinition(0xA4, "Transmission Gear", "ratio", null) { b ->
            (b.u(2) * 256 + b.u(3)) / 1000.0
        }
    )

    /**
     * Returns a known definition or creates a generic one for unknown PIDs.
     * Generic formula: first 2 bytes as unsigned integer, or first byte if only 1 byte.
     */
    fun getOrUnknown(pid: Int): PidDefinition = definitions[pid] ?: PidDefinition(
        pid = pid,
        name = "PID 0x${pid.toString(16).padStart(2, '0').uppercase()}",
        unit = "",
        haDeviceClass = null,
        formula = { b ->
            when {
                b.size >= 2 -> (b.u(0) * 256 + b.u(1)).toDouble()
                b.isNotEmpty() -> b.u(0).toDouble()
                else -> 0.0
            }
        }
    )
}
