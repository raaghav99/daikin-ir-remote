package com.irremote

import android.content.Context
import android.content.Intent
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.irremote.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var irManager: ConsumerIrManager? = null

    private var isPowerOn = false
    private var currentMode = DaikinIR.Mode.COOL
    private var currentTemp = 24
    private var currentFan: Int = DaikinIR.Fan.AUTO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get IR manager
        irManager = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        if (irManager == null || !irManager!!.hasIrEmitter()) {
            Toast.makeText(this, "No IR blaster found on this device!", Toast.LENGTH_LONG).show()
        }

        updateUI()
        setupButtons()
    }

    private fun setupButtons() {
        // Scan brands
        binding.btnScanBrand.setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }

        // Power
        binding.btnPower.setOnClickListener {
            isPowerOn = !isPowerOn
            sendCommand()
            updateUI()
        }

        // Temperature
        binding.btnTempUp.setOnClickListener {
            if (currentTemp < 30) { currentTemp++; sendCommand(); updateUI() }
        }
        binding.btnTempDown.setOnClickListener {
            if (currentTemp > 16) { currentTemp--; sendCommand(); updateUI() }
        }

        // Modes
        binding.btnModeCool.setOnClickListener { setMode(DaikinIR.Mode.COOL) }
        binding.btnModeHeat.setOnClickListener { setMode(DaikinIR.Mode.HEAT) }
        binding.btnModeDry.setOnClickListener  { setMode(DaikinIR.Mode.DRY) }
        binding.btnModeFan.setOnClickListener  { setMode(DaikinIR.Mode.FAN) }
        binding.btnModeAuto.setOnClickListener { setMode(DaikinIR.Mode.AUTO) }

        // Fan speed
        binding.btnFanAuto.setOnClickListener   { setFan(DaikinIR.Fan.AUTO) }
        binding.btnFanLow.setOnClickListener    { setFan(DaikinIR.Fan.LOW) }
        binding.btnFanMed.setOnClickListener    { setFan(DaikinIR.Fan.MED) }
        binding.btnFanHigh.setOnClickListener   { setFan(DaikinIR.Fan.HIGH) }
        binding.btnFanSilent.setOnClickListener { setFan(DaikinIR.Fan.SILENT) }
    }

    private fun setMode(mode: Int) {
        currentMode = mode
        sendCommand()
        updateUI()
    }

    private fun setFan(fan: Int) {
        currentFan = fan
        sendCommand()
        updateUI()
    }

    private fun sendCommand() {
        val ir = irManager ?: run {
            Toast.makeText(this, "No IR blaster!", Toast.LENGTH_SHORT).show()
            return
        }
        val pattern = DaikinIR.buildCommand(
            power = isPowerOn,
            mode  = currentMode,
            temp  = currentTemp,
            fan   = currentFan
        )
        try {
            ir.transmit(DaikinIR.CARRIER_FREQ, pattern)
            Toast.makeText(this, "Signal sent!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "IR error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI() {
        binding.tvPowerStatus.text = if (isPowerOn) "ON" else "OFF"
        binding.tvTemperature.text = "${currentTemp}°C"
        val modeNames = mapOf(
            DaikinIR.Mode.AUTO to "Auto",
            DaikinIR.Mode.COOL to "Cool",
            DaikinIR.Mode.HEAT to "Heat",
            DaikinIR.Mode.DRY  to "Dry",
            DaikinIR.Mode.FAN  to "Fan"
        )
        binding.tvMode.text = modeNames[currentMode] ?: "?"
        val fanNames = mapOf(
            DaikinIR.Fan.AUTO   to "Auto",
            DaikinIR.Fan.SILENT to "Silent",
            DaikinIR.Fan.LOW    to "Low",
            DaikinIR.Fan.MED    to "Med",
            DaikinIR.Fan.HIGH   to "High"
        )
        binding.tvFanSpeed.text = fanNames[currentFan] ?: "?"
        binding.btnPower.text = if (isPowerOn) "POWER OFF" else "POWER ON"
    }
}
