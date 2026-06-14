package com.example.data.database

import androidx.room.*
import com.example.data.model.Page
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE notebookId = :notebookId ORDER BY pageIndex ASC")
    fun getPagesForNotebook(notebookId: Long): Flow<List<Page>>

    @Query("SELECT * FROM pages WHERE notebookId = :notebookId ORDER BY pageIndex ASC")
    suspend fun getPagesForNotebookSync(notebookId: Long): List<Page>

    @Query("SELECT * FROM pages WHERE id = :id LIMIT 1")
    suspend fun getPageById(id: Long): Page?

    @Query("SELECT COUNT(*) FROM pages WHERE notebookId = :notebookId")
    suspend fun getPageCount(notebookId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: Page): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<Page>)

    @Update
    suspend fun updatePage(page: Page)

    @Delete
    suspend fun deletePage(page: Page)

    @Query("DELETE FROM pages WHERE notebookId = :notebookId")
    suspend fun deletePagesForNotebook(notebookId: Long)
}
