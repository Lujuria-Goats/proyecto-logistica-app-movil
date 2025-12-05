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

        // 1. VERIFICACIÓN DE SESIÓN
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
            finish()
        }

        binding.btnLogin.setOnClickListener {
            performLogin()
        }
    }

    private fun performLogin() {
        val identifier = binding.etIdentifier.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (identifier.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Completa los campos", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnLogin.isEnabled = false
        binding.btnLogin.text = "VERIFICANDO..."

        lifecycleScope.launch {
            try {
                // Llamada al Backend
                val response = RetrofitClient.api.login(
                    LoginRequest(identifier = identifier, password = password)
                )

                if (response.isSuccessful && response.body() != null) {
                    val authData = response.body()!!

                    // 1. MENSAJE DE BIENVENIDA (Con el USERNAME)
                    Toast.makeText(this@LoginActivity, "Bienvenido, ${authData.username}", Toast.LENGTH_SHORT).show()

                    // 2. PREPARAR EL NOMBRE PARA EL PERFIL
                    // Si el fullName existe, lo usamos. Si no, usamos el username.
                    val nameForProfile = if (!authData.fullName.isNullOrEmpty()) authData.fullName else authData.username

                    // 3. GUARDAR SESIÓN
                    val prefs = getSharedPreferences("APEX_SESSION", Context.MODE_PRIVATE)
                    prefs.edit().apply {
                        putString("USER_TOKEN", authData.token)
                        putInt("USER_ID", authData.userId)

                        // Guardamos el NOMBRE COMPLETO en la llave que lee el perfil
                        putString("USER_NAME", nameForProfile)

                        apply()
                    }

                    goToMain()
                } else {
                    Toast.makeText(this@LoginActivity, "Credenciales incorrectas", Toast.LENGTH_SHORT).show()
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
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}