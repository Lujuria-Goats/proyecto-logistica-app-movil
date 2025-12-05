package com.apexvision.app.model

data class AuthResponse(
    val token: String,
    val userId: Int,
    val username: String,
    val email: String,
    val fullName: String?
)