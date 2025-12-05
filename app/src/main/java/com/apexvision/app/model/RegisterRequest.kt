package com.apexvision.app.model

data class RegisterRequest(
    val fullName: String,
    val email: String,
    val username: String,
    val password: String,
    val phoneNumber: String,
    val role: String = "DRIVER"

)