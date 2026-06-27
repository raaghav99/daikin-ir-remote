package com.irremote

/**
 * Daikin AC IR Protocol Implementation
 * Based on IRremoteESP8266 open-source library specs.
 *
 * Carrier frequency: 38 kHz
 * Protocol: 3 frames (preamble x2 + data frame)
 */
object DaikinIR {

    const val CARRIER_FREQ = 38000

    // Timings in microseconds
    private const val HDR_MARK   = 3650
    private const val HDR_SPACE  = 1623
    private const val BIT_MARK   = 428
    private const val ONE_SPACE  = 1280
    private const val ZERO_SPACE = 428
    private const val GAP_SPACE  = 29000

    // Fixed preamble frames
    private val FRAME1 = intArrayOf(
        0x11, 0xDA, 0x27, 0x00, 0xC5, 0x00, 0x00, 0xD7
    )
    private val FRAME2 = intArrayOf(
        0x11, 0xDA, 0x27, 0x00, 0x42, 0x00, 0x00, 0x54
    )

    object Mode {
        const val AUTO = 0
        const val DRY  = 2
        const val COOL = 3
        const val HEAT = 4
        const val FAN  = 6
    }

    object Fan {
        const val AUTO   = 0xA0
        const val SILENT = 0xB0
        const val LOW    = 0x30
        const val MED    = 0x40
        const val HIGH   = 0x50
    }

    /**
     * Build raw IR pattern for Android ConsumerIrManager.
     *
     * @param power  true = ON, false = OFF
     * @param mode   Mode.COOL / HEAT / DRY / FAN / AUTO
     * @param temp   Temperature 16–30 °C
     * @param fan    Fan.AUTO / LOW / MED / HIGH / SILENT
     */
    fun buildCommand(
        power: Boolean,
        mode: Int,
        temp: Int,
        fan: Int = Fan.AUTO
    ): IntArray {
        val t = temp.coerceIn(16, 30)

        val frame3 = intArrayOf(
            0x11, 0xDA, 0x27, 0x00, 0x00,
            (if (power) 1 else 0) or (mode shl 4),  // byte 5: power | mode
            (t - 10) * 2,                            // byte 6: temperature
            0x30,                                    // byte 7: swing off
            fan,                                     // byte 8: fan speed
            0x00, 0x00, 0x00, 0x00, 0x00,
            0xC1, 0x00, 0x00, 0x00,
            0x00  // byte 18: checksum placeholder
        )

        // Checksum = sum of bytes 0-17 mod 256
        var cs = 0
        for (i in 0..17) cs += frame3[i] and 0xFF
        frame3[18] = cs and 0xFF

        return encodeFrames(FRAME1, FRAME2, frame3)
    }

    private fun encodeFrames(vararg frames: IntArray): IntArray {
        val pulses = mutableListOf<Int>()
        frames.forEachIndexed { idx, frame ->
            pulses += HDR_MARK
            pulses += HDR_SPACE
            for (byte in frame) {
                for (bit in 0..7) {
                    pulses += BIT_MARK
                    pulses += if ((byte and (1 shl bit)) != 0) ONE_SPACE else ZERO_SPACE
                }
            }
            pulses += BIT_MARK
            if (idx < frames.size - 1) pulses += GAP_SPACE
        }
        return pulses.toIntArray()
    }
}
