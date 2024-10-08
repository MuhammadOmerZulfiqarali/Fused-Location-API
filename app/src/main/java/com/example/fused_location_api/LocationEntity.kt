package com.example.fused_location_api

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_table")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val gpsId: Int,
    val latitude: Double,
    val longitude: Double,
    val date: String,
    val time: String,
    val timestamp: Long
)
