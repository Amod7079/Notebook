package com.example.data.database

import androidx.room.*
import com.example.data.model.Notebook
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks ORDER BY updatedTime DESC")
    fun getAllNotebooks(): Flow<List<Notebook>>

    @Query("SELECT * FROM notebooks ORDER BY updatedTime DESC")
    suspend fun getAllNotebooksSync(): List<Notebook>

    @Query("SELECT * FROM notebooks WHERE title LIKE :query ORDER BY updatedTime DESC")
    fun searchNotebooks(query: String): Flow<List<Notebook>>

    @Query("SELECT * FROM notebooks WHERE id = :id LIMIT 1")
    suspend fun getNotebookById(id: Long): Notebook?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotebook(notebook: Notebook): Long

    @Update
    suspend fun updateNotebook(notebook: Notebook)

    @Delete
    suspend fun deleteNotebook(notebook: Notebook)
}
