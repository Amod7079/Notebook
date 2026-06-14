package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = Notebook::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["notebookId"])]
)
data class Page(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val pageIndex: Int,
    val templateType: String = "RULED", // BLANK, RULED, GRAPH, DOT_GRID, CORNELL
    val backgroundColorHex: String = "#FFFFFF",
    val infiniteCanvas: Boolean = false,
    val width: Float = 1000f,
    val height: Float = 1500f
)
