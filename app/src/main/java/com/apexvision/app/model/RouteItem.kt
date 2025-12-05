package com.apexvision.app.model

data class RouteItem(
    val id: Int,
    val name: String,
    val region: String,
    val totalOrders: Int,
    val status: String
)