package com.apexvision.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.apexvision.app.R
import com.apexvision.app.databinding.ActivityProfileBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cargar el diseño
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar
        loadUserData()
        setupSettings()
        setupButtons()
    }

    private fun loadUserData() {
        val prefs = getSharedPreferences("APEX_SESSION", Context.MODE_PRIVATE)
        val userName = prefs.getString("USER_NAME", null)

        if (userName != null) {
            binding.tvName.text = userName
            binding.tvRole.text = "Conductor Verificado"
        } else {
            binding.tvName.text = "Usuario"
            binding.tvRole.text = "Sesión de invitado"
        }
    }

    private fun setupSettings() {
        val prefs = getSharedPreferences("APEX_PREFS", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("DARK_MODE", false) // false = Día por defecto

        // Poner el switch en la posición correcta al abrir
        binding.switchDarkMode.isChecked = isDarkMode

        // Escuchar cambios
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            // 1. Guardar
            prefs.edit().putBoolean("DARK_MODE", isChecked).apply()

            // 2. Aplicar
            val mode = if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    private fun showLogoutLoading() {
        // Dialogo de carga simple
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_loading)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()

        lifecycleScope.launch {
            delay(1500)

            // Borrar sesión
            val prefsSession = getSharedPreferences("APEX_SESSION", Context.MODE_PRIVATE)
            prefsSession.edit().clear().apply()

            dialog.dismiss()

            // Ir al Login y borrar historial
            val intent = Intent(this@ProfileActivity, com.apexvision.app.ui.auth.LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnMyRoutes.setOnClickListener {
            val intent = Intent(this, com.apexvision.app.ui.RoutesListActivity::class.java)
            startActivity(intent)
        }

        binding.btnLogout.setOnClickListener {
            showLogoutLoading()
        }
    }
}