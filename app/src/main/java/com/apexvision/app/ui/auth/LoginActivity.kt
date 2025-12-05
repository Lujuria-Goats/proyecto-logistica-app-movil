package com.apexvision.app.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.apexvision.app.MainActivity
import com.apexvision.app.databinding.ActivityLoginBinding
import com.apexvision.app.model.LoginRequest
import com.apexvision.app.network.RetrofitClient
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar si ya inició sesión antes (Sesión persistente)
        val prefs = getSharedPreferences("APEX_SESSION", Context.MODE_PRIVATE)
        if (prefs.contains("USER_TOKEN")) {
            goToMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
    }

    private fun setupButtons() {
        binding.tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish() // Cerramos Login para ir a Register
        }

        binding.btnLogin.setOnClickListener {
            performLogin()
        }
    }

    private fun performLogin() {
        val identifier = binding.etIdentifier.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (identifier.isEmpty() || password.isEmpty()) return

        binding.btnLogin.isEnabled = false
        binding.btnLogin.text = "VERIFICANDO..."

        lifecycleScope.launch {
            try {
                // 🚀 LLAMADA AL BACKEND
                // Nota: Tu modelo LoginRequest debe tener 'identifier' o 'username' según lo definimos antes
                val response = com.apexvision.app.network.RetrofitClient.api.login(
                    com.apexvision.app.model.LoginRequest(identifier, password)
                )

                if (response.isSuccessful && response.body() != null) {
                    val authData = response.body()!!

                    // 💾 GUARDAR SESIÓN (SharedPreferences)
                    getSharedPreferences("APEX_SESSION", Context.MODE_PRIVATE).edit().apply {
                        putString("TOKEN", authData.token)
                        putInt("USER_ID", authData.userId)
                        putString("USER_NAME", authData.fullName ?: authData.username)
                        apply()
                    }

                    Toast.makeText(this@LoginActivity, "Bienvenido, ${authData.username}", Toast.LENGTH_SHORT).show()

                    // Ir al Mapa
                    startActivity(Intent(this@LoginActivity, com.apexvision.app.MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@LoginActivity, "Credenciales Incorrectas", Toast.LENGTH_SHORT).show()
                    resetButton()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
                resetButton()
            }
        }
    }

    private fun resetButton() {
        binding.btnLogin.isEnabled = true
        binding.btnLogin.text = "INICIAR SESIÓN"
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        // Limpiamos el historial para que no pueda volver al login con "Atrás"
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}