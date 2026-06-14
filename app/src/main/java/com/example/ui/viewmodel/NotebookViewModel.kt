package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.Notebook
import com.example.data.model.Page
import com.example.data.model.Stroke
import com.example.data.repository.NotebookRepository
import com.example.util.SerializationHelper
import com.example.util.PdfExporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.io.BufferedOutputStream
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry

class NotebookViewModel(
    application: Application,
    private val repository: NotebookRepository
) : AndroidViewModel(application) {

    // Notebook search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Folder and starred filters
    private val _currentFolder = MutableStateFlow("All")
    val currentFolder: StateFlow<String> = _currentFolder

    // Dynamic folder list collected from notebooks
    val foldersList: StateFlow<List<String>> = repository.allNotebooks
        .map { list ->
            val set = mutableSetOf("All", "Starred")
            set.addAll(list.map { it.folder }.filter { it.isNotEmpty() })
            set.toList()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf("All", "Starred")
        )

    // All or filtered notebooks combined instantly in-memory
    val notebooks: StateFlow<List<Notebook>> = combine(
        repository.allNotebooks,
        _searchQuery,
        _currentFolder
    ) { all, query, folder ->
        var filtered = all

        // 1. Filter by Folder
        filtered = when (folder) {
            "All" -> filtered
            "Starred" -> filtered.filter { it.isFavorite }
            else -> filtered.filter { it.folder.equals(folder, ignoreCase = true) }
        }

        // 2. Filter by Search Query
        if (query.trim().isNotEmpty()) {
            filtered = filtered.filter { it.title.contains(query, ignoreCase = true) }
        }

        filtered
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Toggle favorite state of a notebook
    fun toggleFavorite(notebook: Notebook) {
        viewModelScope.launch {
            repository.updateNotebook(notebook.copy(isFavorite = !notebook.isFavorite))
        }
    }

    // Move a notebook to a folder
    fun changeNotebookFolder(notebook: Notebook, newFolder: String) {
        viewModelScope.launch {
            repository.updateNotebook(notebook.copy(folder = newFolder))
        }
    }

    // Update active folder filter
    fun setFolderFilter(folder: String) {
        _currentFolder.value = folder
    }

    // Create Notebook inside a specific folder with custom defaults
    fun createNotebook(
        title: String,
        coverColorHex: String,
        folder: String = "General",
        defaultTemplate: String = "RULED",
        defaultBgColorHex: String = "#FFFFFF",
        defaultPenColorHex: String = "#000000",
        defaultWritingMode: String = "STYLUS_ONLY",
        onComplete: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val notebookId = repository.createNotebook(
                title = title,
                coverColorHex = coverColorHex,
                folder = folder,
                defaultTemplate = defaultTemplate,
                defaultBgColorHex = defaultBgColorHex,
                defaultPenColorHex = defaultPenColorHex,
                defaultWritingMode = defaultWritingMode
            )
            onComplete(notebookId)
        }
    }

    // Editor State
    private val _activeNotebook = MutableStateFlow<Notebook?>(null)
    val activeNotebook: StateFlow<Notebook?> = _activeNotebook

    private val _pages = MutableStateFlow<List<Page>>(emptyList())
    val pages: StateFlow<List<Page>> = _pages

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex

    val currentPage: StateFlow<Page?> = combine(_pages, _currentPageIndex) { pageList, index ->
        if (pageList.isNotEmpty() && index in pageList.indices) {
            pageList[index]
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Strokes for the active page
    private val _strokes = MutableStateFlow<List<Stroke>>(emptyList())
    val strokes: StateFlow<List<Stroke>> = _strokes

    // Redo stack for drawing undo/redo actions
    private val _redoStack = MutableStateFlow<List<Stroke>>(emptyList())
    val canUndo: StateFlow<Boolean> = _strokes.map { it.isNotEmpty() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
    val canRedo: StateFlow<Boolean> = _redoStack.map { it.isNotEmpty() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    // Pen Tools and settings
    private val _toolType = MutableStateFlow("BALL_PEN") // BALL_PEN, FOUNTAIN_PEN, PENCIL, MARKER, HIGHLIGHTER, CALLIGRAPHY_PEN
    val toolType: StateFlow<String> = _toolType

    private val _colorHex = MutableStateFlow("#3F51B5") // Primary standard color
    val colorHex: StateFlow<String> = _colorHex

    private val _thickness = MutableStateFlow(5f) // Pen thickness (1f to 100f)
    val thickness: StateFlow<Float> = _thickness

    private val _opacity = MutableStateFlow(1f) // Opacity (0.1f to 1f)
    val opacity: StateFlow<Float> = _opacity

    private val _recentColors = MutableStateFlow(listOf("#000000", "#3F51B5", "#4CAF50", "#FFC107", "#E91E63", "#9C27B0"))
    val recentColors: StateFlow<List<String>> = _recentColors

    // Eraser configuration
    private val _eraserMode = MutableStateFlow("NORMAL") // NORMAL or STROKE
    val eraserMode: StateFlow<String> = _eraserMode

    // Laser Pointer Trail configuration
    private val _laserMode = MutableStateFlow<String?>(null) // null, "RED", "GREEN", "BLUE"
    val laserMode: StateFlow<String?> = _laserMode

    private val _laserPoints = MutableStateFlow<List<Pair<Float, Float>>>(emptyList())
    val laserPoints: StateFlow<List<Pair<Float, Float>>> = _laserPoints

    // Advanced Laser Custom Configurations State
    private val _laserFadeSpeed = MutableStateFlow("MEDIUM") // FAST (300ms), MEDIUM (500ms), SLOW (800ms)
    val laserFadeSpeed: StateFlow<String> = _laserFadeSpeed

    private val _laserTrailLength = MutableStateFlow(35) // Range 10 .. 100 points
    val laserTrailLength: StateFlow<Int> = _laserTrailLength

    private val _laserGlowIntensity = MutableStateFlow(0.6f) // Range 0.1f .. 1.0f
    val laserGlowIntensity: StateFlow<Float> = _laserGlowIntensity

    // Canvas & Ruling Lines Custom Options
    private val _rulingLineColorHex = MutableStateFlow("") // Empty triggers automatic dark/light contrast
    val rulingLineColorHex: StateFlow<String> = _rulingLineColorHex

    private val _rulingThickness = MutableStateFlow(1.5f) // Range 0.5f to 5.0f
    val rulingThickness: StateFlow<Float> = _rulingThickness

    private val _rulingSpacing = MutableStateFlow(32f) // Range 16f to 100f
    val rulingSpacing: StateFlow<Float> = _rulingSpacing

    private val _showMarginLine = MutableStateFlow(true)
    val showMarginLine: StateFlow<Boolean> = _showMarginLine

    private val _leftMarginColorHex = MutableStateFlow("#FFCDD2")
    val leftMarginColorHex: StateFlow<String> = _leftMarginColorHex

    private val _rightMarginColorHex = MutableStateFlow("#FFCDD2")
    val rightMarginColorHex: StateFlow<String> = _rightMarginColorHex

    private val _rulingOpacity = MutableStateFlow(0.8f) // Range 0.1f to 1.0f
    val rulingOpacity: StateFlow<Float> = _rulingOpacity

    // Canvas Zoom Slider Percentage
    private val _zoomPercentage = MutableStateFlow(100)
    val zoomPercentage: StateFlow<Int> = _zoomPercentage

    // Presenter trail presentation mode enabled
    private val _presentationMode = MutableStateFlow(false)
    val presentationMode: StateFlow<Boolean> = _presentationMode

    // Touch / Stylus Writing Mode: FINGER_ONLY, STYLUS_ONLY, FINGER_STYLUS
    private val _writingMode = MutableStateFlow("STYLUS_ONLY")
    val writingMode: StateFlow<String> = _writingMode

    // App theme mode setting
    private val _themeMode = MutableStateFlow("SYSTEM") // SYSTEM, LIGHT, DARK
    val themeMode: StateFlow<String> = _themeMode

    init {
        viewModelScope.launch {
            _themeMode.value = repository.getThemeSetting()
        }
    }

    // Search query update
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Rename Notebook
    fun renameNotebook(notebook: Notebook, newTitle: String) {
        viewModelScope.launch {
            repository.updateNotebook(notebook.copy(title = newTitle))
            if (_activeNotebook.value?.id == notebook.id) {
                _activeNotebook.value = _activeNotebook.value?.copy(title = newTitle)
            }
        }
    }

    // Delete Notebook
    fun deleteNotebook(notebook: Notebook) {
        viewModelScope.launch {
            repository.deleteNotebook(notebook)
            if (_activeNotebook.value?.id == notebook.id) {
                _activeNotebook.value = null
                _pages.value = emptyList()
                _strokes.value = emptyList()
            }
        }
    }

    // Set Active Notebook & Navigation to Editor
    fun loadNotebook(id: Long) {
        viewModelScope.launch {
            val notebook = repository.getNotebookById(id)
            if (notebook != null) {
                _activeNotebook.value = notebook
                _colorHex.value = notebook.defaultPenColorHex
                _writingMode.value = notebook.defaultWritingMode
                // Collect pages in editor scope
                repository.getPagesForNotebook(id).collectLatest { pageList ->
                    _pages.value = pageList
                    // Ensure current index is safe
                    if (_currentPageIndex.value >= pageList.size) {
                        _currentPageIndex.value = maxOf(0, pageList.size - 1)
                    }
                    loadCurrentPageStrokes()
                }
            }
        }
    }

    private var strokesJob: kotlinx.coroutines.Job? = null

    private fun loadCurrentPageStrokes() {
        strokesJob?.cancel()
        val page = currentPage.value ?: return
        strokesJob = viewModelScope.launch {
            repository.getStrokesForPage(page.id).collectLatest { strokeList ->
                _strokes.value = strokeList
            }
        }
    }

    fun selectPageIndex(index: Int) {
        if (index in _pages.value.indices) {
            _currentPageIndex.value = index
            _redoStack.value = emptyList() // clear redo stack on page change
            loadCurrentPageStrokes()
        }
    }

    // Page Management operations
    fun addNewPage() {
        val notebook = _activeNotebook.value ?: return
        viewModelScope.launch {
            val current = currentPage.value
            val template = current?.templateType ?: notebook.defaultTemplate
            val bgColor = current?.backgroundColorHex ?: notebook.defaultBgColorHex
            val newPage = repository.addPage(notebook.id, template, bgColor)
            // After inserting, find its index and switch to it
            val updatedPages = _pages.value
            val idx = updatedPages.indexOfFirst { it.id == newPage.id }
            if (idx != -1) {
                selectPageIndex(idx)
            } else {
                selectPageIndex(updatedPages.size - 1)
            }
        }
    }

    fun deleteCurrentPage() {
        val page = currentPage.value ?: return
        val currentIdx = _currentPageIndex.value
        viewModelScope.launch {
            repository.deletePage(page)
            val newIdx = if (currentIdx > 0) currentIdx - 1 else 0
            _currentPageIndex.value = newIdx
            loadCurrentPageStrokes()
        }
    }

    fun duplicateCurrentPage() {
        val page = currentPage.value ?: return
        viewModelScope.launch {
            repository.duplicatePage(page)
        }
    }

    fun changeCurrentPageTemplate(templateType: String) {
        val page = currentPage.value ?: return
        viewModelScope.launch {
            repository.changePageTemplate(page.id, templateType)
            // Refresh local state object reference
            val updatedPages = _pages.value.map {
                if (it.id == page.id) it.copy(templateType = templateType) else it
            }
            _pages.value = updatedPages
        }
    }

    // Drawing Operations
    fun saveStrokeWithPressure(pointsStr: String) {
        val page = currentPage.value ?: return
        if (pointsStr.isEmpty()) return
        val stroke = Stroke(
            pageId = page.id,
            toolType = _toolType.value,
            colorHex = _colorHex.value,
            thickness = _thickness.value,
            opacity = _opacity.value,
            pointsData = pointsStr
        )
        viewModelScope.launch {
            repository.addStroke(stroke)
            _redoStack.value = emptyList()
        }
    }

    fun saveStroke(points: List<Pair<Float, Float>>) {
        val page = currentPage.value ?: return
        if (points.isEmpty()) return

        // Format points as string: "x1,y1 x2,y2..."
        val pointsStr = points.joinToString(separator = " ") { "${it.first},${it.second}" }
        val stroke = Stroke(
            pageId = page.id,
            toolType = _toolType.value,
            colorHex = _colorHex.value,
            thickness = _thickness.value,
            opacity = _opacity.value,
            pointsData = pointsStr
        )

        viewModelScope.launch {
            repository.addStroke(stroke)
            _redoStack.value = emptyList() // Clear redo stack on manual draw
        }
    }

    fun deleteStrokeLocally(stroke: Stroke) {
        viewModelScope.launch {
            repository.deleteStroke(stroke)
        }
    }

    fun performUndo() {
        val lastStroke = _strokes.value.lastOrNull() ?: return
        viewModelScope.launch {
            repository.deleteStroke(lastStroke)
            _redoStack.value = _redoStack.value + lastStroke
        }
    }

    fun performRedo() {
        val lastUndone = _redoStack.value.lastOrNull() ?: return
        viewModelScope.launch {
            repository.addStroke(lastUndone)
            _redoStack.value = _redoStack.value.dropLast(1)
        }
    }

    fun clearPage() {
        val page = currentPage.value ?: return
        viewModelScope.launch {
            repository.clearPageDrawings(page.id)
            _redoStack.value = emptyList()
        }
    }

    // Set active pen settings
    fun setToolType(type: String) {
        _toolType.value = type
        _laserMode.value = null // deactivate laser
    }

    fun selectColor(hex: String) {
        _colorHex.value = hex
        addRecentColor(hex)
    }

    fun setThickness(value: Float) {
        _thickness.value = value
    }

    fun setOpacity(value: Float) {
        _opacity.value = value
    }

    fun setEraserMode(mode: String) {
        _eraserMode.value = mode // "NORMAL" or "STROKE"
        _toolType.value = "ERASER"
        _laserMode.value = null
    }

    fun setLaserPointerMode(color: String?) {
        _laserMode.value = color // "RED", "GREEN", "BLUE" or null
        if (color != null) {
            _toolType.value = "LASER"
        }
    }

    fun setLaserPoints(points: List<Pair<Float, Float>>) {
        val maxLength = _laserTrailLength.value
        _laserPoints.value = if (points.size > maxLength) points.takeLast(maxLength) else points
    }

    fun setLaserFadeSpeed(speed: String) {
        _laserFadeSpeed.value = speed
    }

    fun setLaserTrailLength(length: Int) {
        _laserTrailLength.value = length
    }

    fun setLaserGlowIntensity(intensity: Float) {
        _laserGlowIntensity.value = intensity
    }

    fun setRulingLineColorHex(hex: String) {
        _rulingLineColorHex.value = hex
    }

    fun setRulingThickness(value: Float) {
        _rulingThickness.value = value
    }

    fun setRulingSpacing(value: Float) {
        _rulingSpacing.value = value
    }

    fun setShowMarginLine(show: Boolean) {
        _showMarginLine.value = show
    }

    fun setLeftMarginColorHex(hex: String) {
        _leftMarginColorHex.value = hex
    }

    fun setRightMarginColorHex(hex: String) {
        _rightMarginColorHex.value = hex
    }

    fun setRulingOpacity(value: Float) {
        _rulingOpacity.value = value
    }

    fun setZoomPercentage(percentage: Int) {
        _zoomPercentage.value = percentage
    }

    fun clearLaserPoints() {
        _laserPoints.value = emptyList()
    }

    fun togglePresentationMode() {
        _presentationMode.value = !_presentationMode.value
    }

    fun setWritingMode(mode: String) {
        _writingMode.value = mode
    }

    fun changeCurrentPageBackgroundColor(colorHex: String) {
        val page = currentPage.value ?: return
        viewModelScope.launch {
            repository.changePageBackgroundColor(page.id, colorHex)
            val updatedPages = _pages.value.map {
                if (it.id == page.id) it.copy(backgroundColorHex = colorHex) else it
            }
            _pages.value = updatedPages
        }
    }

    private fun addRecentColor(hex: String) {
        val current = _recentColors.value.toMutableList()
        current.remove(hex)
        current.add(0, hex)
        if (current.size > 8) {
            _recentColors.value = current.take(8)
        } else {
            _recentColors.value = current
        }
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        viewModelScope.launch {
            repository.saveThemeSetting(mode)
        }
    }

    // --- REAL PERSISTENCE, BACKUP, EXPORT & RESTORE ACTIONS ---

    suspend fun exportSingleNotebookToStream(notebookId: Long, outputStream: OutputStream) = withContext(Dispatchers.IO) {
        val notebook = repository.getNotebookById(notebookId) ?: return@withContext
        val pages = repository.getPagesForNotebookSync(notebookId)
        val json = SerializationHelper.exportNotebookToJson(notebook, pages) { pageId ->
            repository.getStrokesForPageSync(pageId)
        }
        outputStream.write(json.toByteArray(StandardCharsets.UTF_8))
        outputStream.flush()
    }

    suspend fun importSingleNotebookFromStream(inputStream: InputStream): Long = withContext(Dispatchers.IO) {
        val bytes = inputStream.readBytes()
        val jsonStr = String(bytes, StandardCharsets.UTF_8)
        val newNotebookId = SerializationHelper.importNotebookFromJson(
            jsonStr = jsonStr,
            onInsertNotebook = { repository.importNotebook(it) },
            onInsertPage = { repository.importPage(it) },
            onInsertStroke = { repository.importStroke(it) }
        )
        newNotebookId
    }

    suspend fun exportSingleNotebookToPdfStream(
        notebookId: Long,
        outputStream: OutputStream,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val notebook = repository.getNotebookById(notebookId) ?: return@withContext
        val pages = repository.getPagesForNotebookSync(notebookId)
        PdfExporter.exportToPdf(
            context = getApplication(),
            notebook = notebook,
            pages = pages,
            strokesProvider = { pageId -> repository.getStrokesForPageSync(pageId) },
            outputStream = outputStream,
            onProgress = onProgress
        )
    }

    suspend fun exportSingleNotebookToPngZipStream(
        notebookId: Long,
        outputStream: OutputStream,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val notebook = repository.getNotebookById(notebookId) ?: return@withContext
        val pages = repository.getPagesForNotebookSync(notebookId)
        PdfExporter.exportToPngZip(
            context = getApplication(),
            notebook = notebook,
            pages = pages,
            strokesProvider = { pageId -> repository.getStrokesForPageSync(pageId) },
            outputStream = outputStream,
            onProgress = onProgress
        )
    }

    suspend fun backupAllNotebooksToZip(outputStream: OutputStream) = withContext(Dispatchers.IO) {
        val allBooks = repository.getAllNotebooksSync()
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
            for (book in allBooks) {
                val pages = repository.getPagesForNotebookSync(book.id)
                val json = SerializationHelper.exportNotebookToJson(book, pages) { pageId ->
                    repository.getStrokesForPageSync(pageId)
                }
                
                // Safe filename
                val safeTitle = book.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                val filename = "${book.id}_${safeTitle}.notebook"
                
                zos.putNextEntry(ZipEntry(filename))
                zos.write(json.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }
            zos.flush()
        }
    }

    suspend fun restoreAllNotebooksFromZip(inputStream: InputStream): Int = withContext(Dispatchers.IO) {
        var count = 0
        ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".notebook")) {
                    val baos = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    var len: Int
                    while (zis.read(buffer).also { len = it } != -1) {
                        baos.write(buffer, 0, len)
                    }
                    val jsonStr = baos.toString("UTF-8")
                    try {
                        SerializationHelper.importNotebookFromJson(
                            jsonStr = jsonStr,
                            onInsertNotebook = { repository.importNotebook(it) },
                            onInsertPage = { repository.importPage(it) },
                            onInsertStroke = { repository.importStroke(it) }
                        )
                        count++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        count
    }
}

class ViewModelFactory(
    private val application: Application,
    private val repository: NotebookRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NotebookViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NotebookViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
