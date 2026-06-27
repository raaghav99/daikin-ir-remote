package com.irremote

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.irremote.databinding.ActivityScanBinding

class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private var irManager: ConsumerIrManager? = null
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        irManager = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

        showBrand(0)

        binding.btnSend.setOnClickListener { sendCurrent() }

        binding.btnNext.setOnClickListener {
            if (currentIndex < BrandCodes.brands.lastIndex) {
                showBrand(currentIndex + 1)
            } else {
                Toast.makeText(this, "Tried all brands!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPrev.setOnClickListener {
            if (currentIndex > 0) showBrand(currentIndex - 1)
        }
    }

    private fun showBrand(idx: Int) {
        currentIndex = idx
        val brand = BrandCodes.brands[idx]
        binding.tvBrandName.text = brand.name
        binding.tvConfidence.text = brand.confidence
        binding.tvConfidence.setTextColor(
            if (brand.confidence == "EXACT")
                getColor(android.R.color.holo_green_light)
            else
                getColor(android.R.color.holo_orange_light)
        )
        binding.tvCounter.text = "${idx + 1} / ${BrandCodes.brands.size}"
        binding.tvFreq.text = "${brand.freq / 1000} kHz"
    }

    private fun sendCurrent() {
        val ir = irManager ?: run {
            Toast.makeText(this, "No IR blaster!", Toast.LENGTH_SHORT).show()
            return
        }
        val brand = BrandCodes.brands[currentIndex]
        try {
            ir.transmit(brand.freq, brand.power)
            Toast.makeText(this, "Sent ${brand.name} — did AC respond?", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
