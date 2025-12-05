package com.apexvision.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.apexvision.app.R
import com.apexvision.app.databinding.ActivityProfileBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadUserData()
        setupSettings()
        setupButtons()
    }

    private fun loadUserData() {
        val prefs = getSharedPreferences("APEX_SESSION", Context.MODE_PRIVATE)

        // Leemos el nombre. Si no existe, pone el texto por defecto
        val userName = prefs.getString("USER_NAME", null)

        if (userName != null) {
            binding.tvName.text = userName
            binding.tvRole.text = "Conductor Verificado" // O el rol que quieras
        } else {
            binding.tvName.text = "Usuario"
            binding.tvRole.text = "Error cargando datos"
        }
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

    private fun showLogoutLoading() {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_loading)
        dialog.setCancelable(false)

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.show()

        lifecycleScope.launch {
            delay(2000)

            // Borrar sesión
            val prefs = getSharedPreferences("APEX_SESSION", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()

            dialog.dismiss()

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