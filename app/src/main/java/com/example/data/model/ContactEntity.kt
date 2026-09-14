package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val secondaryPhone: String? = null,
    val relationship: String = "", // e.g. "Mom", "Dad", "Work"
    val isFavorite: Boolean = false,
    val isDeviceContact: Boolean = false
)
