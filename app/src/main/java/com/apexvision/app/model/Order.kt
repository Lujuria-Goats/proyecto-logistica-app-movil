package com.apexvision.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "orders_table")
data class Order(
    @PrimaryKey val id: Int,
    val address: String,
    val customerName: String,

    var status: String,

    val latitude: Double,
    val longitude: Double,
    var photoPath: String? = null,
    var isSelected: Boolean = true,

    var requiresPhoto: Boolean = true,
    var incidentReport: String? = null
) : Serializable