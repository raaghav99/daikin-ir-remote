package com.irremote

/**
 * IR power-on codes for common AC brands.
 * Sources: IRremoteESP8266, IRDB, manufacturer documentation.
 *
 * EXACT   = protocol fully implemented, should work reliably
 * APPROX  = best-guess based on protocol spec, may need model variant
 */
object BrandCodes {

    data class AcBrand(
        val name: String,
        val confidence: String,   // "EXACT" or "APPROX"
        val freq: Int = 38000,
        val power: IntArray
    )

    // ─── NEC helper ───────────────────────────────────────────────────────────
    // Standard 32-bit NEC: addr(8) addr_inv(8) cmd(8) cmd_inv(8), LSB first
    private fun nec(addr: Int, cmd: Int): IntArray {
        val bits = (addr and 0xFF) or
                   ((addr.inv() and 0xFF) shl 8) or
                   ((cmd and 0xFF) shl 16) or
                   ((cmd.inv() and 0xFF) shl 24)
        val p = mutableListOf(9000, 4500)
        for (i in 0..31) {
            p += 560
            p += if ((bits shr i) and 1 == 1) 1690 else 560
        }
        p += 560
        return p.toIntArray()
    }

    // ─── COOLIX protocol ──────────────────────────────────────────────────────
    // Used by: Midea, AUX, Hisense, Cooper & Hunter, some Haier/generic Chinese
    // Power toggle code: 0xB27BE0 + inverted 0x4D841F  (48 bits, MSB first)
    private val COOLIX = intArrayOf(
        4416, 4416,
        // B2 = 1011 0010
        552,1656, 552,552, 552,1656, 552,1656, 552,552, 552,552, 552,1656, 552,552,
        // 7B = 0111 1011
        552,552, 552,1656, 552,1656, 552,1656, 552,1656, 552,552, 552,1656, 552,1656,
        // E0 = 1110 0000
        552,1656, 552,1656, 552,1656, 552,552, 552,552, 552,552, 552,552, 552,552,
        // 4D = 0100 1101 (complement of B2)
        552,552, 552,1656, 552,552, 552,552, 552,1656, 552,1656, 552,552, 552,1656,
        // 84 = 1000 0100 (complement of 7B)
        552,1656, 552,552, 552,552, 552,552, 552,552, 552,1656, 552,552, 552,552,
        // 1F = 0001 1111 (complement of E0)
        552,552, 552,552, 552,552, 552,1656, 552,1656, 552,1656, 552,1656, 552,1656,
        552
    )

    // ─── Gree AC protocol ─────────────────────────────────────────────────────
    // Used by: Gree, some generic/Chigo/Aux variants
    // Power on, cool 25°C, auto fan — 4 bytes + 4 check bytes
    private val GREE = intArrayOf(
        9000, 4500,
        // Byte0: 0x00 — Power ON | Cool
        620,540, 620,540, 620,540, 620,540, 620,540, 620,540, 620,540, 620,540,
        // Byte1: 0x09 — temp 25 (0x09 = 25-16=9)
        620,1600, 620,540, 620,540, 620,1600, 620,540, 620,540, 620,540, 620,540,
        // Byte2: 0x20 — auto fan
        620,540, 620,540, 620,1600, 620,540, 620,540, 620,540, 620,540, 620,540,
        // Byte3: 0x50
        620,540, 620,540, 620,540, 620,540, 620,1600, 620,540, 620,1600, 620,540,
        620, 19000,  // separator gap
        // Repeat bytes 4-7 (simplified)
        620,540, 620,1600, 620,540, 620,540, 620,540, 620,540, 620,540, 620,540,
        620,540, 620,540, 620,1600, 620,540, 620,540, 620,540, 620,540, 620,540,
        620,540, 620,540, 620,540, 620,1600, 620,540, 620,540, 620,540, 620,540,
        620,1600, 620,540, 620,540, 620,540, 620,540, 620,540, 620,540, 620,1600,
        620
    )

    // ─── Panasonic AC (CKP series) ────────────────────────────────────────────
    // Used by: Panasonic split ACs (most common Indian models)
    // Power on, cool 24°C — simplified 2-frame protocol
    private fun panasonicAC(): IntArray {
        // Frame 1 (8 bytes fixed preamble)
        val f1 = intArrayOf(0x02, 0x20, 0xE0, 0x04, 0x00, 0x00, 0x00, 0x06)
        // Frame 2 (19 bytes — power on, cool, 24°C, auto fan)
        val f2 = intArrayOf(
            0x02, 0x20, 0xE0, 0x04, 0x00, 0x38, 0x20, 0x80,
            0xAF, 0x00, 0x00, 0x0E, 0xE0, 0x00, 0x00, 0x89, 0x00, 0x00, 0x00
        )
        // Checksum = sum of bytes 0..17 of frame2, mod 256
        var cs = 0; for (i in 0..17) cs += f2[i]; f2[18] = cs and 0xFF

        val HDR_M = 3456; val HDR_S = 1728
        val BIT_M = 432;  val ONE_S = 1296; val ZERO_S = 432
        val GAP = 10000

        fun encodeFrame(bytes: IntArray, gap: Int): List<Int> {
            val out = mutableListOf(HDR_M, HDR_S)
            for (b in bytes) for (bit in 0..7) {
                out += BIT_M
                out += if ((b shr bit) and 1 == 1) ONE_S else ZERO_S
            }
            out += BIT_M; out += gap
            return out
        }

        return (encodeFrame(f1, GAP) + encodeFrame(f2, 0)).toIntArray()
    }

    // ─── Samsung AC ───────────────────────────────────────────────────────────
    // Samsung uses a unique AC protocol distinct from NEC/Samsung TV
    // Power on, cool 24°C (approximate)
    private val SAMSUNG_AC = intArrayOf(
        690, 17844,
        3086, 8864,
        // simplified 7-byte frame
        506,436, 506,436, 506,436, 506,436, 506,436, 506,436, 506,436, 506,1342,
        506,436, 506,436, 506,436, 506,436, 506,436, 506,436, 506,1342, 506,436,
        506,1342, 506,436, 506,436, 506,436, 506,436, 506,436, 506,436, 506,436,
        506,1342, 506,1342, 506,436, 506,436, 506,436, 506,436, 506,436, 506,436,
        506,436, 506,436, 506,436, 506,436, 506,1342, 506,436, 506,436, 506,436,
        506,436, 506,436, 506,436, 506,436, 506,436, 506,436, 506,436, 506,436,
        506,436, 506,1342, 506,436, 506,436, 506,436, 506,436, 506,436, 506,1342,
        506
    )

    // ─── Hitachi AC ───────────────────────────────────────────────────────────
    // Hitachi uses a long 28-byte protocol.  Approximate power-on frame.
    private val HITACHI_AC = intArrayOf(
        3300, 1700,
        // 28 bytes — power on, cool, 24°C (simplified / approximate)
        400,1250, 400,500, 400,500, 400,500, 400,500, 400,500, 400,500, 400,500,
        400,500, 400,500, 400,500, 400,500, 400,500, 400,500, 400,500, 400,1250,
        400,500, 400,500, 400,500, 400,500, 400,1250, 400,500, 400,500, 400,500,
        400,500, 400,500, 400,500, 400,500, 400,500, 400,500, 400,500, 400,500,
        400,1250, 400,500, 400,500, 400,500, 400,500, 400,500, 400,500, 400,500,
        400,500, 400,500, 400,500, 400,500, 400,500, 400,500, 400,500, 400,500,
        400,1250, 400,1250, 400,500, 400,500, 400,500, 400,1250, 400,500, 400,500,
        400,500, 400,500, 400,500, 400,500, 400,500, 400,500, 400,500, 400,500,
        400,500, 400,500, 400,1250, 400,500, 400,500, 400,500, 400,500, 400,1250,
        400,500, 400,500, 400,500, 400,500, 400,500, 400,500, 400,500, 400,500,
        400,1250, 400,500, 400,500, 400,500, 400,500, 400,500, 400,500, 400,500,
        400,500, 400,500, 400,500, 400,500, 400,500, 400,500, 400,500, 400,500,
        400,500, 400,500, 400,1250, 400,500, 400,500, 400,500, 400,500, 400,500,
        400,500, 400,500, 400,500, 400,500, 400,500, 400,500, 400,500, 400,500,
        400
    )

    // ─── Mitsubishi AC ────────────────────────────────────────────────────────
    // 18-byte frame, 3× repeated, 38kHz
    private val MITSUBISHI_AC = intArrayOf(
        3400, 1750,
        // 18 bytes: 0x23 0xCB 0x26 0x01 0x00 0x20 0x18 0x03 (power/cool/24°)
        // + remaining bytes (simplified)
        450,420, 450,420, 450,1250, 450,420, 450,420, 450,420, 450,420, 450,420,  // 0x04
        450,1250, 450,1250, 450,420, 450,1250, 450,420, 450,420, 450,420, 450,420, // 0xC3 ~ CB
        450,420, 450,1250, 450,420, 450,420, 450,1250, 450,420, 450,420, 450,420,  // 0x26? approx
        450,1250, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420,   // 0x01
        450,420, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420,    // 0x00
        450,420, 450,420, 450,420, 450,420, 450,420, 450,1250, 450,420, 450,420,   // 0x20
        450,420, 450,420, 450,420, 450,1250, 450,1250, 450,420, 450,420, 450,420,  // 0x18
        450,1250, 450,1250, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420,  // 0x03
        450,420, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420,    // 0x00
        450,420, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420,    // 0x00
        450,420, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420,    // 0x00
        450,420, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420,    // 0x00
        450,420, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420,    // 0x00
        450,420, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420,    // 0x00
        450,420, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420,    // 0x00
        450,420, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420,    // 0x00
        450,420, 450,420, 450,1250, 450,420, 450,420, 450,420, 450,420, 450,420,   // checksum ~
        450,420, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420, 450,420,    // 0x00
        450
    )

    // ─── Fujitsu AC ───────────────────────────────────────────────────────────
    // Used by: Fujitsu, O'General
    // 16-byte payload, 38kHz
    private val FUJITSU_AC = intArrayOf(
        3324, 1574,
        // 10 bytes intro + 6 bytes command (power on, cool, 24°C)
        448,390, 448,390, 448,390, 448,390, 448,390, 448,390, 448,390, 448,390, // 0x00
        448,390, 448,390, 448,390, 448,390, 448,390, 448,390, 448,1182, 448,390, // 0x40
        448,390, 448,390, 448,390, 448,390, 448,390, 448,390, 448,1182, 448,390, // 0x40
        448,390, 448,390, 448,390, 448,390, 448,390, 448,390, 448,390, 448,390,  // 0x00
        448,390, 448,390, 448,390, 448,390, 448,390, 448,390, 448,390, 448,1182, // 0x80
        448,390, 448,1182, 448,390, 448,390, 448,390, 448,1182, 448,1182, 448,390, // 0x46 = cool/on
        448,1182, 448,1182, 448,390, 448,390, 448,390, 448,390, 448,390, 448,390, // 0x03 = 24°
        448,390, 448,390, 448,390, 448,390, 448,390, 448,390, 448,390, 448,390,  // 0x00
        448,390, 448,390, 448,390, 448,390, 448,390, 448,390, 448,390, 448,390,  // fan auto
        448
    )

    // ─── Haier AC ─────────────────────────────────────────────────────────────
    // 37-bit protocol (approximate power on)
    private val HAIER_AC = intArrayOf(
        3000, 4300,
        520,650, 520,1650, 520,650, 520,650, 520,650, 520,650, 520,650, 520,650,
        520,650, 520,650, 520,1650, 520,650, 520,1650, 520,650, 520,650, 520,650,
        520,650, 520,650, 520,650, 520,650, 520,650, 520,650, 520,650, 520,1650,
        520,650, 520,650, 520,650, 520,650, 520,650, 520,650, 520,650, 520,650,
        520,1650, 520,650,
        520
    )

    // ─── LG AC ────────────────────────────────────────────────────────────────
    // LG AC2 protocol — 28 bits (4-bit addr + 8-bit cmd + 4-bit checksum, x2)
    // Common power code for LG split ACs
    private val LG_AC = run {
        // LG AC protocol: header 3000/9900, bits 500/500(0) 500/1500(1), 28 bits
        val bits28 = 0x8800000 // addr=0x8, cmd=0x800, chk varies (simplified)
        val p = mutableListOf(3000, 9900)
        for (i in 27 downTo 0) {
            p += 500
            p += if ((bits28 shr i) and 1 == 1) 1500 else 500
        }
        p += 500
        p.toIntArray()
    }

    // ─── Whirlpool AC ─────────────────────────────────────────────────────────
    private val WHIRLPOOL_AC = intArrayOf(
        8950, 4484,
        597,1649, 597,597, 597,597, 597,597, 597,1649, 597,597, 597,597, 597,1649,
        597,597, 597,597, 597,597, 597,1649, 597,1649, 597,597, 597,597, 597,597,
        597,597, 597,597, 597,597, 597,597, 597,597, 597,597, 597,597, 597,597,
        597,1649, 597,597, 597,597, 597,1649, 597,597, 597,597, 597,1649, 597,597,
        597,597, 597,597, 597,1649, 597,597, 597,597, 597,597, 597,1649, 597,597,
        597,597, 597,597, 597,597, 597,597, 597,597, 597,597, 597,597, 597,597,
        597
    )

    // ─── Brand list ───────────────────────────────────────────────────────────
    val brands: List<AcBrand> by lazy {
        listOf(
            AcBrand("Daikin",           "EXACT",  power = DaikinIR.buildCommand(true, DaikinIR.Mode.COOL, 24)),
            AcBrand("Midea",            "EXACT",  power = COOLIX),
            AcBrand("AUX",              "EXACT",  power = COOLIX),
            AcBrand("Hisense",          "EXACT",  power = COOLIX),
            AcBrand("Cooper&Hunter",    "EXACT",  power = COOLIX),
            AcBrand("Panasonic",        "APPROX", power = panasonicAC()),
            AcBrand("LG",               "APPROX", power = LG_AC),
            AcBrand("Samsung",          "APPROX", power = SAMSUNG_AC),
            AcBrand("Mitsubishi",       "APPROX", power = MITSUBISHI_AC),
            AcBrand("Hitachi",          "APPROX", power = HITACHI_AC),
            AcBrand("Fujitsu/OGeneral", "APPROX", power = FUJITSU_AC),
            AcBrand("Haier",            "APPROX", power = HAIER_AC),
            AcBrand("Gree",             "APPROX", power = GREE),
            AcBrand("Whirlpool",        "APPROX", power = WHIRLPOOL_AC),
            AcBrand("Carrier (NEC)",    "APPROX", power = nec(0x04, 0x08)),
            AcBrand("Voltas (NEC)",     "APPROX", power = nec(0x10, 0x00)),
            AcBrand("Blue Star (NEC)",  "APPROX", power = nec(0x40, 0x04)),
            AcBrand("Lloyd (NEC)",      "APPROX", power = nec(0x20, 0x00)),
            AcBrand("TCL (NEC)",        "APPROX", power = nec(0x02, 0x09)),
            AcBrand("Onida (NEC)",      "APPROX", power = nec(0x03, 0x00)),
            AcBrand("Godrej (NEC)",     "APPROX", power = nec(0x10, 0xFD)),
            AcBrand("Generic NEC-1",    "APPROX", power = nec(0x00, 0x08)),
            AcBrand("Generic NEC-2",    "APPROX", power = nec(0x00, 0x20)),
            AcBrand("Generic NEC-3",    "APPROX", power = nec(0xFF, 0x00)),
        )
    }
}
