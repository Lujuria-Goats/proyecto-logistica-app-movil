package com.apexvision.app.network

import com.apexvision.app.model.AuthResponse
import com.apexvision.app.model.LoginRequest
import com.apexvision.app.model.RegisterRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    // --- AUTENTICACIÓN ---

    @POST("api/Auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    // Endpoint específico para conductores
    @POST("api/Auth/register/driver")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>
}