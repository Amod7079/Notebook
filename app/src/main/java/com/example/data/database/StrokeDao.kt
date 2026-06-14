package com.example.data.database

import androidx.room.*
import com.example.data.model.Stroke
import kotlinx.coroutines.flow.Flow

@Dao
interface StrokeDao {
    @Query("SELECT * FROM drawings WHERE pageId = :pageId ORDER BY id ASC")
    fun getStrokesForPage(pageId: Long): Flow<List<Stroke>>

    @Query("SELECT * FROM drawings WHERE pageId = :pageId ORDER BY id ASC")
    suspend fun getStrokesForPageSync(pageId: Long): List<Stroke>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStroke(stroke: Stroke): Long

    @Delete
    suspend fun deleteStroke(stroke: Stroke)

    @Query("DELETE FROM drawings WHERE pageId = :pageId")
    suspend fun clearPageDrawings(pageId: Long)
}
