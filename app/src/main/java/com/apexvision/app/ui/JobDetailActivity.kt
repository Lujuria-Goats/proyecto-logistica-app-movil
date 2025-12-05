package com.apexvision.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.apexvision.app.databinding.ActivityJobDetailBinding
import com.apexvision.app.db.AppDatabase
import com.apexvision.app.model.Job
import kotlinx.coroutines.launch

class JobDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJobDetailBinding
    private lateinit var database: AppDatabase
    private var currentJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJobDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)

        // 1. Recibir datos
        val jobId = intent.getIntExtra("JOB_ID", -1)
        val company = intent.getStringExtra("COMPANY")
        val title = intent.getStringExtra("TITLE")
        val salary = intent.getStringExtra("SALARY")

        // 2. Llenar UI visual (mientras carga la DB)
        binding.tvDetailCompany.text = company
        binding.tvDetailTitle.text = title
        binding.tvDetailSalary.text = salary

        binding.btnBack.setOnClickListener { finish() }

        // 3. CARGAR ESTADO DESDE BD
        if (jobId != -1) {
            lifecycleScope.launch {
                currentJob = database.jobDao().getJobById(jobId)

                // Si ya aplicó, mostrar estado final directamente
                if (currentJob?.isApplied == true) {
                    showAppliedState()
                }
            }
        }

        // 4. Lógica del Botón
        binding.btnApplyFinal.setOnClickListener {
            performApplication()
        }
    }

    private fun performApplication() {
        binding.btnApplyFinal.text = "ENVIANDO..."
        binding.btnApplyFinal.isEnabled = false

        Handler(Looper.getMainLooper()).postDelayed({
            // Guardar en BD
            currentJob?.let { job ->
                job.isApplied = true
                lifecycleScope.launch {
                    database.jobDao().updateJob(job)
                }
            }
            showAppliedState()
        }, 1500)
    }

    private fun showAppliedState() {
        binding.layoutSuccessMsg.visibility = View.VISIBLE
        binding.btnApplyFinal.text = "APLICADO"
        binding.btnApplyFinal.alpha = 0.5f
        binding.btnApplyFinal.isEnabled = false // Ya no puede volver a aplicar
    }
}