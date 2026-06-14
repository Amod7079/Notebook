package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "drawings",
    foreignKeys = [
        ForeignKey(
            entity = Page::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["pageId"])]
)
data class Stroke(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pageId: Long,
    val toolType: String, // BALL_PEN, FOUNTAIN_PEN, PENCIL, MARKER, HIGHLIGHTER, CALLIGRAPHY_PEN
    val colorHex: String,
    val thickness: Float,
    val opacity: Float,
    val pointsData: String // Coordinates serialized as space-separated x,y pairs: "x1,y1 x2,y2..."
)
