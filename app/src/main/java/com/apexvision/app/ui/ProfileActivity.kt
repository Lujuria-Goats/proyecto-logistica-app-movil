package com.apexvision.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.apexvision.app.databinding.ActivityProfileBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupChart()
        setupSettings()
        setupButtons()
    }

    private fun setupChart() {
        // 1. Datos Quemados (Entregas Lun-Vie)
        val entries = ArrayList<BarEntry>()
        entries.add(BarEntry(0f, 12f)) // Lunes
        entries.add(BarEntry(1f, 18f)) // Martes
        entries.add(BarEntry(2f, 15f)) // Miércoles
        entries.add(BarEntry(3f, 22f)) // Jueves
        entries.add(BarEntry(4f, 19f)) // Viernes

        // 2. Configuración del Dataset (Barras Doradas)
        val dataSet = BarDataSet(entries, "Entregas")
        dataSet.color = Color.parseColor("#D4AF37") // Dorado Apex
        dataSet.valueTextColor = Color.BLACK
        dataSet.valueTextSize = 12f

        // 3. Aplicar datos
        val barData = BarData(dataSet)
        barData.barWidth = 0.6f
        binding.barChart.data = barData

        // 4. Estilizar la Gráfica
        val chart = binding.barChart
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setDrawGridBackground(false)
        chart.axisRight.isEnabled = false
        chart.axisLeft.textColor = Color.BLACK
        chart.axisLeft.setDrawGridLines(false)

        // Eje X (Días)
        val days = arrayOf("Lun", "Mar", "Mié", "Jue", "Vie")
        val xAxis = chart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(days)
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textColor = Color.BLACK
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f

        // Animación
        chart.animateY(1500)
        chart.invalidate()
    }

    private fun setupSettings() {
        val prefs = getSharedPreferences("APEX_PREFS", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("DARK_MODE", false)

        binding.switchDarkMode.isChecked = isDarkMode

        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("DARK_MODE", isChecked).apply()
            Toast.makeText(this, "Cambio guardado.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupButtons() {
        // 1. Botón Atrás
        binding.btnBack.setOnClickListener { finish() }

        // 2. BOTÓN MIS RUTAS (ESTO ES LO QUE FALTABA)
        // Al hacer clic en el layout de "Mis Rutas", vamos a la lista
        binding.btnMyRoutes.setOnClickListener {
            val intent = Intent(this, RoutesListActivity::class.java)
            startActivity(intent)
        }

        // 3. Botón Cerrar Sesión
        binding.btnLogout.setOnClickListener {
            Toast.makeText(this, "Cerrando sesión...", Toast.LENGTH_SHORT).show()
            finishAffinity() // Cierra toda la app
        }
    }
}