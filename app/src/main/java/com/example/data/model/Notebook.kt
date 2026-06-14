package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notebooks")
data class Notebook(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val coverColorHex: String = "#3F51B5",
    val pageCount: Int = 0,
    val createdTime: Long = System.currentTimeMillis(),
    val updatedTime: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val folder: String = "General",
    val defaultTemplate: String = "RULED",
    val defaultBgColorHex: String = "#FFFFFF",
    val defaultPenColorHex: String = "#000000",
    val defaultWritingMode: String = "STYLUS_ONLY"
)
