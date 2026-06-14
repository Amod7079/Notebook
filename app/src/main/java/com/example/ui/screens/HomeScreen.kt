package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import com.example.data.model.Notebook
import com.example.ui.viewmodel.NotebookViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: NotebookViewModel,
    onNavigateToEditor: (Long) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val notebooks by viewModel.notebooks.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val currentFolder by viewModel.currentFolder.collectAsStateWithLifecycle()
    val foldersList by viewModel.foldersList.collectAsStateWithLifecycle()

    var isGridView by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var notebookToRename by remember { mutableStateOf<Notebook?>(null) }
    var notebookToDelete by remember { mutableStateOf<Notebook?>(null) }
    var notebookToMove by remember { mutableStateOf<Notebook?>(null) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var notebookForExternalMove by remember { mutableStateOf<Notebook?>(null) }
    var notebookToConfirmDeleteAfterMove by remember { mutableStateOf<Notebook?>(null) }

    val externalMoveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val book = notebookForExternalMove
        if (uri != null && book != null) {
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        viewModel.exportSingleNotebookToStream(book.id, os)
                    }
                    notebookToConfirmDeleteAfterMove = book
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val configuration = LocalConfiguration.current
    val gridColumns = when {
        configuration.screenWidthDp < 400 -> 2
        configuration.screenWidthDp < 600 -> 2
        configuration.screenWidthDp < 840 -> 3
        else -> 5
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Draw,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "LuxeNotes",
                                fontWeight = FontWeight.Bold,
                                fontSize = 21.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                text = "Premium Ink Workspace",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.testTag("toggle_layout_button")
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Outlined.GridView else Icons.Outlined.FormatListBulleted,
                            contentDescription = "Toggle Grid/List layout"
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("home_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Launch settings menu"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .testTag("create_notebook_fab")
                    .padding(8.dp)
                    .shadow(12.dp, shape = FloatingActionButtonDefaults.largeShape),
                shape = FloatingActionButtonDefaults.largeShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Notebook", modifier = Modifier.size(20.dp))
                    Text("New Notebook", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Elegant Glassmorphic Search Bar Widget
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_notebooks_input"),
                placeholder = { Text("Search title, tag, or content...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search icon", tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search query", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                )
            )

            // Dynamic Scrolling Folders Menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Horizontal scrolling chip row
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    foldersList.forEach { folderName ->
                        val isSelected = currentFolder == folderName
                        val chipIcon: ImageVector = when (folderName) {
                            "All" -> Icons.Outlined.CollectionsBookmark
                            "Starred" -> Icons.Outlined.Grade
                            else -> Icons.Outlined.FolderOpen
                        }

                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setFolderFilter(folderName) },
                            label = { Text(folderName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                            leadingIcon = { Icon(chipIcon, null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                selectedBorderColor = Color.Transparent,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                enabled = true,
                                selected = isSelected
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Plus icon chip to add new folder category
                    IconButton(
                        onClick = { showNewFolderDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Add category folder", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Quick stats block displaying totals
            Text(
                text = "${notebooks.size} notebooks found in ${if (currentFolder == "Starred") "Favorites" else "Folder '$currentFolder'"}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
            )

            // Content Area setup
            if (notebooks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(32.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (searchQuery.isNotEmpty()) Icons.Outlined.ContentPasteSearch else Icons.Outlined.FolderOff,
                                contentDescription = "Empty notebooks visual",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "Your Canvas Awaits" else "Nothing matches your ink",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) {
                                "Create a polished notebook with customizable templates inside '$currentFolder' to capture ideas beautifully."
                            } else {
                                "Double check your spelling, clear the search terms, or explore a different folder filter."
                            },
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.widthIn(max = 280.dp)
                        )
                        if (searchQuery.isEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { showCreateDialog = true },
                                modifier = Modifier.testTag("empty_create_button"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("New Notebook")
                            }
                        }
                    }
                }
            } else {
                if (isGridView) {
                    // Grid Arrangement
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .testTag("notebooks_grid"),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(notebooks, key = { it.id }) { notebook ->
                            NotebookGridItem(
                                notebook = notebook,
                                onClick = { onNavigateToEditor(notebook.id) },
                                onFavoriteToggle = { viewModel.toggleFavorite(notebook) },
                                onRename = { notebookToRename = notebook },
                                onDelete = { notebookToDelete = notebook },
                                onMove = { notebookToMove = notebook },
                                onMoveToExternal = {
                                    notebookForExternalMove = notebook
                                    externalMoveLauncher.launch("${notebook.title}.notebook")
                                }
                            )
                        }
                    }
                } else {
                    // List Arrangement
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .testTag("notebooks_list"),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(notebooks, key = { it.id }) { notebook ->
                            NotebookListItem(
                                notebook = notebook,
                                onClick = { onNavigateToEditor(notebook.id) },
                                onFavoriteToggle = { viewModel.toggleFavorite(notebook) },
                                onRename = { notebookToRename = notebook },
                                onDelete = { notebookToDelete = notebook },
                                onMove = { notebookToMove = notebook },
                                onMoveToExternal = {
                                    notebookForExternalMove = notebook
                                    externalMoveLauncher.launch("${notebook.title}.notebook")
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // CREATE NOTEBOOK DIALOG
    if (showCreateDialog) {
        CreateNotebookDialog(
            currentFolder = if (currentFolder == "All" || currentFolder == "Starred") "General" else currentFolder,
            foldersList = foldersList.filter { it != "All" && it != "Starred" },
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, coverColorHex, folderSelected, defaultTemplate, defaultBgColorHex, defaultPenColorHex, defaultWritingMode ->
                viewModel.createNotebook(
                    title = title,
                    coverColorHex = coverColorHex,
                    folder = folderSelected,
                    defaultTemplate = defaultTemplate,
                    defaultBgColorHex = defaultBgColorHex,
                    defaultPenColorHex = defaultPenColorHex,
                    defaultWritingMode = defaultWritingMode
                ) { newId ->
                    onNavigateToEditor(newId)
                }
                showCreateDialog = false
            }
        )
    }

    // NEW FOLDER DIALOG
    if (showNewFolderDialog) {
        NewFolderDialog(
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { folderName ->
                viewModel.createNotebook("First Book in $folderName", "#3F51B5", folderName)
                viewModel.setFolderFilter(folderName)
                showNewFolderDialog = false
            }
        )
    }

    // RENAME DIALOG
    notebookToRename?.let { notebook ->
        RenameNotebookDialog(
            notebook = notebook,
            onDismiss = { notebookToRename = null },
            onConfirm = { newName ->
                viewModel.renameNotebook(notebook, newName)
                notebookToRename = null
            }
        )
    }

    // MOVE TO FOLDER DIALOG
    notebookToMove?.let { notebook ->
        MoveFolderDialog(
            notebook = notebook,
            foldersList = foldersList.filter { it != "All" && it != "Starred" },
            onDismiss = { notebookToMove = null },
            onConfirm = { destFolder ->
                viewModel.changeNotebookFolder(notebook, destFolder)
                notebookToMove = null
            }
        )
    }

    // DELETE CONFIRMATION DIALOG
    notebookToDelete?.let { notebook ->
        AlertDialog(
            onDismissRequest = { notebookToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Notebook?") },
            text = { Text("Are you sure you want to delete '${notebook.title}'? This action cannot be undone and will permanently delete all notes, pages, and drawings included.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteNotebook(notebook)
                        notebookToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { notebookToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DISK MOVE CONFIRMATION DIALOG
    notebookToConfirmDeleteAfterMove?.let { notebook ->
        AlertDialog(
            onDismissRequest = { notebookToConfirmDeleteAfterMove = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Transfer Verified Securely") },
            text = { Text("Your notebook '${notebook.title}' has been successfully copied and verified at your chosen external location/directory.\n\nSince this manual backup is complete, would you like to delete the local Room database copy of this notebook now? (Recommended to save space and avoid duplicate clutter)") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteNotebook(notebook)
                        notebookToConfirmDeleteAfterMove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_after_move_button")
                ) {
                    Text("Delete Local Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { notebookToConfirmDeleteAfterMove = null }) {
                    Text("Decline (Keep Both)")
                }
            }
        )
    }
}

@Composable
fun NotebookGridItem(
    notebook: Notebook,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onMoveToExternal: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val format = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val dateString = remember(notebook.updatedTime) { format.format(Date(notebook.updatedTime)) }

    val parsedColor = remember(notebook.coverColorHex) {
        try {
            Color(android.graphics.Color.parseColor(notebook.coverColorHex))
        } catch (e: Exception) {
            Color(0xFF3F51B5)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notebook_card_${notebook.id}")
            .clickable(onClick = onClick)
            .shadow(4.dp, shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column {
            // Notebook Cover visual representation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                parsedColor,
                                parsedColor.copy(alpha = 0.82f),
                                parsedColor.copy(alpha = 0.7f)
                            )
                        )
                    )
                    .padding(12.dp)
            ) {
                // Spinal accord line mimicking book spines
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(10.dp)
                        .align(Alignment.CenterStart)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent)
                            )
                        )
                )

                // Binder spiral steel rings overlay
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(16.dp)
                        .align(Alignment.CenterStart)
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    repeat(8) {
                        Box(
                            modifier = Modifier
                                .size(width = 11.dp, height = 3.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(Color.White.copy(alpha = 0.5f))
                        )
                    }
                }

                // Bookmark star button top-right and ribbon banner hanging inside
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (notebook.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Toggle favorite notebook",
                        tint = if (notebook.isFavorite) Color(0xFFF1C40F) else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Folder category label pill (anchored at center bottom of cover)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 14.dp, y = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = notebook.folder,
                        fontSize = 9.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Centered Journal Label Placard
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .border(1.dp, parsedColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = notebook.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF2C3E50),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Outlined.Collections, null, tint = Color.Gray, modifier = Modifier.size(10.dp))
                            Text(
                                text = "${notebook.pageCount} Pages",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // Footer metadata row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notebook.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Ink'd $dateString",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                showMenu = false
                                onRename()
                            },
                            leadingIcon = { Icon(Icons.Outlined.Edit, "Rename", modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Move to Folder") },
                            onClick = {
                                showMenu = false
                                onMove()
                            },
                            leadingIcon = { Icon(Icons.Outlined.Folder, "Move", modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Move Storage (SAF)") },
                            onClick = {
                                showMenu = false
                                onMoveToExternal()
                            },
                            leadingIcon = { Icon(Icons.Outlined.Save, "Move Storage", modifier = Modifier.size(18.dp)) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotebookListItem(
    notebook: Notebook,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onMoveToExternal: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val format = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val dateString = remember(notebook.updatedTime) { format.format(Date(notebook.updatedTime)) }

    val parsedColor = remember(notebook.coverColorHex) {
        try {
            Color(android.graphics.Color.parseColor(notebook.coverColorHex))
        } catch (e: Exception) {
            Color(0xFF3F51B5)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notebook_list_item_${notebook.id}")
            .clickable(onClick = onClick)
            .shadow(2.dp, shape = RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Small leatherette Notebook Indicator Left
            Box(
                modifier = Modifier
                    .size(width = 54.dp, height = 70.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(parsedColor, parsedColor.copy(alpha = 0.8f))
                        )
                    )
            ) {
                // Spine line
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(Color.Black.copy(alpha = 0.2f))
                )
                // Spiral links
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(6.dp)
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    repeat(6) {
                        Box(
                            modifier = Modifier
                                .size(width = 4.dp, height = 2.dp)
                                .background(Color.White.copy(alpha = 0.4f))
                        )
                    }
                }
            }

            // Info center
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = notebook.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (notebook.isFavorite) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFF1C40F), modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(notebook.folder, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            labelColor = MaterialTheme.colorScheme.primary
                        ),
                        border = null,
                        modifier = Modifier.height(20.dp)
                    )
                    Text(
                        text = "${notebook.pageCount} pages",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Ink'd $dateString",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            // Actions column
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        imageVector = if (notebook.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Toggle favorite state",
                        tint = if (notebook.isFavorite) Color(0xFFF1C40F) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Show options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                showMenu = false
                                onRename()
                            },
                            leadingIcon = { Icon(Icons.Outlined.Edit, "Rename", modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Move to Folder") },
                            onClick = {
                                showMenu = false
                                onMove()
                            },
                            leadingIcon = { Icon(Icons.Outlined.Folder, "Move", modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Move Storage (SAF)") },
                            onClick = {
                                showMenu = false
                                onMoveToExternal()
                            },
                            leadingIcon = { Icon(Icons.Outlined.Save, "Move Storage", modifier = Modifier.size(18.dp)) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNotebookDialog(
    currentFolder: String,
    foldersList: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf("#3F51B5") }
    var destinationFolder by remember { mutableStateOf(currentFolder) }
    var showDropdownFolders by remember { mutableStateOf(false) }

    // Custom defaults
    var selectedTemplate by remember { mutableStateOf("RULED_NOTEBOOK") }
    var selectedPageBgHex by remember { mutableStateOf("#FFFFFF") }
    var selectedPenColorHex by remember { mutableStateOf("#000000") }
    var selectedWritingMode by remember { mutableStateOf("STYLUS_ONLY") }

    val presetColors = listOf(
        "#1E293B", // Obsidian Slate
        "#8E44AD", // Premium Purple
        "#27AE60", // Jade Emerald
        "#D35400", // Autumn Auburn
        "#2980B9", // Cool Sapphire
        "#D4AF37", // Elegant Gold/Brass
        "#E74C3C", // Coral Velvet
        "#1B4F72"  // Navy Royal
    )

    val templates = listOf(
        "BLANK" to "Plain Paper",
        "RULED_NOTEBOOK" to "Ruled Notebook",
        "WIDE_RULED" to "Wide Ruled",
        "COLLEGE_RULED" to "College Ruled",
        "GRID_PAPER" to "Grid Paper",
        "DOT_GRID_PAPER" to "Dot Grid",
        "GRAPH_PAPER" to "Graph Paper",
        "ENGINEERING_GRID" to "Engineering",
        "MUSIC_SHEET" to "Music Sheet"
    )

    val pageBgColors = listOf(
        "#FFFFFF" to "White",
        "#FDFBF7" to "Cream",
        "#FFFDE7" to "Yellow",
        "#E3F2FD" to "Blue",
        "#E8F5E9" to "Green",
        "#FCE4EC" to "Pink",
        "#2C3E50" to "Dark Gray",
        "#121212" to "Black"
    )

    val stylusWritingModes = listOf(
        "FINGER_ONLY" to "Finger Only",
        "STYLUS_ONLY" to "Stylus Only",
        "FINGER_STYLUS" to "Finger + Stylus"
    )

    val defaultPenColors = listOf(
        "#000000" to "Black",
        "#1E3A8A" to "Navy",
        "#EF4444" to "Red",
        "#10B981" to "Green",
        "#7C3AED" to "Purple",
        "#FFC107" to "Gold"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 4.dp)
                .testTag("create_notebook_dialog"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "New Notebook",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, false)
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Notebook Title") },
                        placeholder = { Text("Lectures, Sketches, Journal...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("notebook_title_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Folder destination selection
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = destinationFolder,
                            onValueChange = { destinationFolder = it },
                            label = { Text("Destination Folder") },
                            trailingIcon = {
                                IconButton(onClick = { showDropdownFolders = true }) {
                                    Icon(Icons.Default.ArrowDropDown, "Select folder")
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        DropdownMenu(
                            expanded = showDropdownFolders,
                            onDismissRequest = { showDropdownFolders = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            foldersList.forEach { folderItem ->
                                DropdownMenuItem(
                                    text = { Text(folderItem) },
                                    onClick = {
                                        destinationFolder = folderItem
                                        showDropdownFolders = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Leather Cover Finish",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Color preset selector row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetColors.take(4).forEach { hex ->
                            val color = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(color)
                                    .border(
                                        border = if (selectedColorHex == hex) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f)),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedColorHex = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColorHex == hex) {
                                    Icon(Icons.Default.Check, "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        presetColors.drop(4).forEach { hex ->
                            val color = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(color)
                                    .border(
                                        border = if (selectedColorHex == hex) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f)),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedColorHex = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColorHex == hex) {
                                    Icon(Icons.Default.Check, "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Page Background Paper",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(pageBgColors) { (hex, name) ->
                            val color = Color(android.graphics.Color.parseColor(hex))
                            FilterChip(
                                selected = selectedPageBgHex == hex,
                                onClick = { selectedPageBgHex = hex },
                                label = { Text(name) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(1.dp, Color.Gray, CircleShape)
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Page Base Template",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(templates) { (id, label) ->
                            FilterChip(
                                selected = selectedTemplate == id,
                                onClick = { selectedTemplate = id },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Redmi Pen writing Mode",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(stylusWritingModes) { (id, label) ->
                            FilterChip(
                                selected = selectedWritingMode == id,
                                onClick = { selectedWritingMode = id },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Default Pen Ink Color",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(defaultPenColors) { (hex, name) ->
                            val color = Color(android.graphics.Color.parseColor(hex))
                            FilterChip(
                                selected = selectedPenColorHex == hex,
                                onClick = { selectedPenColorHex = hex },
                                label = { Text(name) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.trim().isNotEmpty()) {
                                onConfirm(
                                    title.trim(),
                                    selectedColorHex,
                                    destinationFolder.trim().ifEmpty { "General" },
                                    selectedTemplate,
                                    selectedPageBgHex,
                                    selectedPenColorHex,
                                    selectedWritingMode
                                )
                            }
                        },
                        enabled = title.trim().isNotEmpty(),
                        modifier = Modifier.testTag("create_notebook_confirm_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}

@Composable
fun NewFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Create Collection Folder",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Folder Name") },
                    placeholder = { Text("E.g. Philosophy, Astronomy, Finances...") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.trim().isNotEmpty()) {
                                onConfirm(title.trim())
                            }
                        },
                        enabled = title.trim().isNotEmpty(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Add Folder")
                    }
                }
            }
        }
    }
}

@Composable
fun MoveFolderDialog(
    notebook: Notebook,
    foldersList: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var folderName by remember { mutableStateOf(notebook.folder) }
    var showDropdownFolders by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Move Notebook",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Transfer '${notebook.title}' into another dynamic category.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        label = { Text("Destination Folder") },
                        trailingIcon = {
                            IconButton(onClick = { showDropdownFolders = true }) {
                                Icon(Icons.Default.ArrowDropDown, "Select folder")
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    DropdownMenu(
                        expanded = showDropdownFolders,
                        onDismissRequest = { showDropdownFolders = false },
                        modifier = Modifier.fillMaxWidth(0.7f)
                    ) {
                        foldersList.forEach { folderItem ->
                            DropdownMenuItem(
                                text = { Text(folderItem) },
                                onClick = {
                                    folderName = folderItem
                                    showDropdownFolders = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (folderName.trim().isNotEmpty()) {
                                onConfirm(folderName.trim())
                            }
                        },
                        enabled = folderName.trim().isNotEmpty(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Move")
                    }
                }
            }
        }
    }
}

@Composable
fun RenameNotebookDialog(
    notebook: Notebook,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember { mutableStateOf(notebook.title) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("rename_notebook_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Rename Notebook",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("New Title") },
                    placeholder = { Text("E.g. Biology, Personal Journal...") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_notebook_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.trim().isNotEmpty()) {
                                onConfirm(title.trim())
                            }
                        },
                        enabled = title.trim().isNotEmpty(),
                        modifier = Modifier.testTag("rename_notebook_confirm_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Rename")
                    }
                }
            }
        }
    }
}
