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
    private val FRAME1 = byteArrayOf(
        0x11, 0xDA.toByte(), 0x27, 0x00,
        0xC5.toByte(), 0x00, 0x00, 0xD7.toByte()
    )
    private val FRAME2 = byteArrayOf(
        0x11, 0xDA.toByte(), 0x27, 0x00,
        0x42, 0x00, 0x00, 0x54
    )

    object Mode {
        const val AUTO = 0
        const val DRY  = 2
        const val COOL = 3
        const val HEAT = 4
        const val FAN  = 6
    }

    object Fan {
        const val AUTO   = 0xA0.toByte()
        const val SILENT = 0xB0.toByte()
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
        fan: Byte = Fan.AUTO
    ): IntArray {
        val t = temp.coerceIn(16, 30)

        val frame3 = ByteArray(19)
        frame3[0] = 0x11
        frame3[1] = 0xDA.toByte()
        frame3[2] = 0x27
        frame3[3] = 0x00
        frame3[4] = 0x00
        // Byte 5: power bit (0) | mode (bits 4-2)
        frame3[5] = ((if (power) 1 else 0) or (mode shl 4)).toByte()
        // Byte 6: temperature ((temp - 10) * 2)
        frame3[6] = ((t - 10) * 2).toByte()
        // Byte 7: swing off
        frame3[7] = 0x30
        // Byte 8: fan speed
        frame3[8] = fan
        frame3[9]  = 0x00
        frame3[10] = 0x00
        frame3[11] = 0x00
        frame3[12] = 0x00
        frame3[13] = 0x00
        frame3[14] = 0xC1.toByte()
        frame3[15] = 0x00
        frame3[16] = 0x00
        frame3[17] = 0x00
        // Checksum = sum of bytes 0-17 mod 256
        var cs = 0
        for (i in 0..17) cs += frame3[i].toInt() and 0xFF
        frame3[18] = (cs and 0xFF).toByte()

        return encodeFrames(FRAME1, FRAME2, frame3)
    }

    private fun encodeFrames(vararg frames: ByteArray): IntArray {
        val pulses = mutableListOf<Int>()
        frames.forEachIndexed { idx, frame ->
            pulses += HDR_MARK
            pulses += HDR_SPACE
            for (byte in frame) {
                for (bit in 0..7) {
                    pulses += BIT_MARK
                    pulses += if ((byte.toInt() and (1 shl bit)) != 0) ONE_SPACE else ZERO_SPACE
                }
            }
            pulses += BIT_MARK
            if (idx < frames.size - 1) pulses += GAP_SPACE
        }
        return pulses.toIntArray()
    }
}
