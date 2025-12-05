package com.apexvision.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jobs_table")
data class Job(
    @PrimaryKey val id: Int,
    val companyName: String,
    val description: String,
    val salary: String,
    var isApplied: Boolean = false // <-- Este es el campo que guardará el estado
)