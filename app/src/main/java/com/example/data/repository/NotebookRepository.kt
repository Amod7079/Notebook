package com.example.data.repository

import com.example.data.database.NotebookDao
import com.example.data.database.PageDao
import com.example.data.database.StrokeDao
import com.example.data.database.SettingDao
import com.example.data.model.Notebook
import com.example.data.model.Page
import com.example.data.model.Stroke
import com.example.data.model.AppSetting
import kotlinx.coroutines.flow.Flow

class NotebookRepository(
    private val notebookDao: NotebookDao,
    private val pageDao: PageDao,
    private val strokeDao: StrokeDao,
    private val settingDao: SettingDao
) {
    val allNotebooks: Flow<List<Notebook>> = notebookDao.getAllNotebooks()

    suspend fun getAllNotebooksSync(): List<Notebook> {
        return notebookDao.getAllNotebooksSync()
    }

    suspend fun getPagesForNotebookSync(notebookId: Long): List<Page> {
        return pageDao.getPagesForNotebookSync(notebookId)
    }

    suspend fun getStrokesForPageSync(pageId: Long): List<Stroke> {
        return strokeDao.getStrokesForPageSync(pageId)
    }

    suspend fun importNotebook(notebook: Notebook): Long {
        return notebookDao.insertNotebook(notebook)
    }

    suspend fun importPage(page: Page): Long {
        return pageDao.insertPage(page)
    }

    suspend fun importStroke(stroke: Stroke): Long {
        return strokeDao.insertStroke(stroke)
    }

    fun searchNotebooks(query: String): Flow<List<Notebook>> {
        return notebookDao.searchNotebooks("%$query%")
    }

    suspend fun getNotebookById(id: Long): Notebook? {
        return notebookDao.getNotebookById(id)
    }

    suspend fun createNotebook(
        title: String,
        coverColorHex: String,
        folder: String = "General",
        defaultTemplate: String = "RULED",
        defaultBgColorHex: String = "#FFFFFF",
        defaultPenColorHex: String = "#000000",
        defaultWritingMode: String = "STYLUS_ONLY"
    ): Long {
        val notebookId = notebookDao.insertNotebook(
            Notebook(
                title = title,
                coverColorHex = coverColorHex,
                pageCount = 1,
                folder = folder,
                defaultTemplate = defaultTemplate,
                defaultBgColorHex = defaultBgColorHex,
                defaultPenColorHex = defaultPenColorHex,
                defaultWritingMode = defaultWritingMode
            )
        )
        // Auto-create the first page
        pageDao.insertPage(
            Page(
                notebookId = notebookId,
                pageIndex = 0,
                templateType = defaultTemplate,
                backgroundColorHex = defaultBgColorHex
            )
        )
        return notebookId
    }

    suspend fun updateNotebook(notebook: Notebook) {
        notebookDao.updateNotebook(notebook.copy(updatedTime = System.currentTimeMillis()))
    }

    suspend fun deleteNotebook(notebook: Notebook) {
        notebookDao.deleteNotebook(notebook)
    }

    // Page Management
    fun getPagesForNotebook(notebookId: Long): Flow<List<Page>> {
        return pageDao.getPagesForNotebook(notebookId)
    }

    suspend fun addPage(notebookId: Long, templateType: String = "RULED", backgroundColorHex: String = "#FFFFFF"): Page {
        val pages = pageDao.getPagesForNotebookSync(notebookId)
        val newIndex = pages.size
        val newPage = Page(
            notebookId = notebookId,
            pageIndex = newIndex,
            templateType = templateType,
            backgroundColorHex = backgroundColorHex
        )
        val pageId = pageDao.insertPage(newPage)
        val count = pageDao.getPageCount(notebookId)
        getNotebookById(notebookId)?.let {
            notebookDao.updateNotebook(it.copy(pageCount = count, updatedTime = System.currentTimeMillis()))
        }
        return newPage.copy(id = pageId)
    }

    suspend fun deletePage(page: Page) {
        pageDao.deletePage(page)
        // Re-index remaining pages
        val pages = pageDao.getPagesForNotebookSync(page.notebookId)
        val updatedPages = pages.mapIndexed { index, p ->
            p.copy(pageIndex = index)
        }
        pageDao.insertPages(updatedPages)

        // Update book page count
        val count = pageDao.getPageCount(page.notebookId)
        getNotebookById(page.notebookId)?.let {
            notebookDao.updateNotebook(it.copy(pageCount = count, updatedTime = System.currentTimeMillis()))
        }
    }

    suspend fun duplicatePage(page: Page) {
        val pId = page.id
        val strokes = strokeDao.getStrokesForPageSync(pId)

        val pages = pageDao.getPagesForNotebookSync(page.notebookId)
        val targetIndex = page.pageIndex + 1

        // Bump indices of subsequent pages
        val updatedPages = pages.map { p ->
            if (p.pageIndex >= targetIndex) {
                p.copy(pageIndex = p.pageIndex + 1)
            } else {
                p
            }
        }
        pageDao.insertPages(updatedPages)

        // Insert new duplicated page
        val newPageId = pageDao.insertPage(
            Page(
                notebookId = page.notebookId,
                pageIndex = targetIndex,
                templateType = page.templateType,
                infiniteCanvas = page.infiniteCanvas,
                width = page.width,
                height = page.height
            )
        )

        // Copy and insert strokes
        val duplicatedStrokes = strokes.map { s ->
            s.copy(id = 0, pageId = newPageId)
        }
        duplicatedStrokes.forEach { strokeDao.insertStroke(it) }

        // Update book page count
        val count = pageDao.getPageCount(page.notebookId)
        getNotebookById(page.notebookId)?.let {
            notebookDao.updateNotebook(it.copy(pageCount = count, updatedTime = System.currentTimeMillis()))
        }
    }

    suspend fun changePageTemplate(pageId: Long, templateType: String) {
        pageDao.getPageById(pageId)?.let {
            pageDao.updatePage(it.copy(templateType = templateType))
        }
    }

    suspend fun changePageBackgroundColor(pageId: Long, backgroundColorHex: String) {
        pageDao.getPageById(pageId)?.let {
            pageDao.updatePage(it.copy(backgroundColorHex = backgroundColorHex))
        }
    }

    suspend fun reorderPages(notebookId: Long, pagesList: List<Page>) {
        val updatedPages = pagesList.mapIndexed { index, page ->
            page.copy(pageIndex = index)
        }
        pageDao.insertPages(updatedPages)
        getNotebookById(notebookId)?.let {
            notebookDao.updateNotebook(it.copy(updatedTime = System.currentTimeMillis()))
        }
    }

    // Drawing Strokes
    fun getStrokesForPage(pageId: Long): Flow<List<Stroke>> {
        return strokeDao.getStrokesForPage(pageId)
    }

    suspend fun addStroke(stroke: Stroke): Long {
        return strokeDao.insertStroke(stroke)
    }

    suspend fun deleteStroke(stroke: Stroke) {
        strokeDao.deleteStroke(stroke)
    }

    suspend fun clearPageDrawings(pageId: Long) {
        strokeDao.clearPageDrawings(pageId)
    }

    // Settings
    suspend fun getThemeSetting(): String {
        return settingDao.getSetting("theme_mode")?.value ?: "SYSTEM"
    }

    suspend fun saveThemeSetting(mode: String) {
        settingDao.saveSetting(AppSetting("theme_mode", mode))
    }
}
