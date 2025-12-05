package com.apexvision.app.ui.auth

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.apexvision.app.MainActivity
import com.apexvision.app.R
import com.apexvision.app.databinding.ActivityRegisterBinding
import com.apexvision.app.model.RegisterRequest
import com.apexvision.app.network.RetrofitClient
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private var isPasswordValid = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        setupButtons()
    }

    private fun setupListeners() {
        // Escuchar cambios en la contraseña para validación en tiempo real
        binding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validatePassword(s.toString())
                checkFormValidity()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Escuchar cambios en otros campos para habilitar el botón
        val generalTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { checkFormValidity() }
            override fun afterTextChanged(s: Editable?) {}
        }

        binding.etFullName.addTextChangedListener(generalTextWatcher)
        binding.etUsername.addTextChangedListener(generalTextWatcher)
        binding.etPhone.addTextChangedListener(generalTextWatcher)
    }

    private fun validatePassword(password: String) {
        // 1. REGLAS
        val hasLength = password.length >= 8
        val hasUpper = password.any { it.isUpperCase() }
        val hasNumber = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }

        // 2. ACTUALIZAR TEXTOS (Verde si cumple, Gris si no)
        updateRuleColor(binding.tvRuleLength, hasLength)
        updateRuleColor(binding.tvRuleUpper, hasUpper)
        updateRuleColor(binding.tvRuleNumber, hasNumber)
        updateRuleColor(binding.tvRuleSpecial, hasSpecial)

        // 3. CALCULAR PUNTAJE (0 a 4)
        var score = 0
        if (hasLength) score++
        if (hasUpper) score++
        if (hasNumber) score++
        if (hasSpecial) score++

        // 4. ACTUALIZAR BARRAS DE COLOR
        updateStrengthBars(score)

        // ¿Es válida totalmente?
        isPasswordValid = (score == 4)
    }

    private fun updateRuleColor(textView: android.widget.TextView, isValid: Boolean) {
        val colorRes = if (isValid) R.color.apex_green else R.color.text_secondary // Usa tu gris
        textView.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    private fun updateStrengthBars(score: Int) {
        // COLOR BASE: Gris Claro (visible sobre fondo blanco)
        val inactiveColor = Color.parseColor("#E0E0E0")

        // Primero reseteamos todas a gris
        binding.strengthBar1.setBackgroundColor(inactiveColor)
        binding.strengthBar2.setBackgroundColor(inactiveColor)
        binding.strengthBar3.setBackgroundColor(inactiveColor)
        binding.strengthBar4.setBackgroundColor(inactiveColor)

        // Elegir color activo
        val activeColor = when (score) {
            1 -> ContextCompat.getColor(this, R.color.apex_red)
            2 -> ContextCompat.getColor(this, R.color.apex_orange)
            3 -> ContextCompat.getColor(this, R.color.apex_yellow)
            4 -> ContextCompat.getColor(this, R.color.apex_green)
            else -> inactiveColor
        }

        // Pintar las activas
        if (score >= 1) binding.strengthBar1.setBackgroundColor(activeColor)
        if (score >= 2) binding.strengthBar2.setBackgroundColor(activeColor)
        if (score >= 3) binding.strengthBar3.setBackgroundColor(activeColor)
        if (score >= 4) binding.strengthBar4.setBackgroundColor(activeColor)
    }

    private fun checkFormValidity() {
        val name = binding.etFullName.text.toString()
        val user = binding.etUsername.text.toString()
        val phone = binding.etPhone.text.toString()

        // Validar que el teléfono tenga 10 dígitos exactos
        val isPhoneValid = phone.length == 10

        // El botón solo se activa si TODO está perfecto
        binding.btnRegister.isEnabled = isPasswordValid && isPhoneValid && name.isNotEmpty() && user.isNotEmpty()

        // Cambio visual del botón (Opcional, para que se vea apagado)
        if (binding.btnRegister.isEnabled) {
            binding.btnRegister.alpha = 1.0f
        } else {
            binding.btnRegister.alpha = 0.5f
        }
    }

    private fun setupButtons() {
        // 1. Acción del Botón Registrar (Ya la tenías)
        binding.btnRegister.setOnClickListener {
            performRegister()
        }

        // 2. Acción del Texto "Inicia Sesión" (ESTA FALTABA)
        binding.tvGoToLogin.setOnClickListener {
            val intent = Intent(this, com.apexvision.app.ui.auth.LoginActivity::class.java)
            startActivity(intent)
            finish() // Cerramos la pantalla de registro
        }
    }

    private fun performRegister() {
        binding.btnRegister.isEnabled = false
        binding.btnRegister.text = "REGISTRANDO..."

        // Creamos el objeto con los datos del formulario
        val request = RegisterRequest(
            fullName = binding.etFullName.text.toString().trim(),
            email = binding.etEmail.text.toString().trim(),
            username = binding.etUsername.text.toString().trim(),
            password = binding.etPassword.text.toString().trim(),
            phoneNumber = binding.etPhone.text.toString().trim(),

            // ⚠️ CORRECCIÓN IMPORTANTE: Usar "Driver" como pide el Swagger
            role = "Driver"
        )

        lifecycleScope.launch {
            try {
                // 🚀 LLAMADA AL BACKEND
                val response = com.apexvision.app.network.RetrofitClient.api.register(request)

                if (response.isSuccessful) {
                    Toast.makeText(this@RegisterActivity, "¡Registro Exitoso!", Toast.LENGTH_LONG).show()

                    // Ir al Login para que entre con su cuenta nueva
                    startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                    finish()
                } else {
                    // Si falla (ej: usuario ya existe)
                    // Imprimimos el errorBody para saber qué dice el servidor
                    val errorMsg = response.errorBody()?.string() ?: "Error desconocido"
                    Toast.makeText(this@RegisterActivity, "Error: ${response.code()} - $errorMsg", Toast.LENGTH_LONG).show()
                    resetButton()
                }
            } catch (e: Exception) {
                Toast.makeText(this@RegisterActivity, "Sin conexión: ${e.message}", Toast.LENGTH_LONG).show()
                resetButton()
            }
        }
    }

    private fun resetButton() {
        binding.btnRegister.isEnabled = true
        binding.btnRegister.text = "REGISTRARME"
    }
}