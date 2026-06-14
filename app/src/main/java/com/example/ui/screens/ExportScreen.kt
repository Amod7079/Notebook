package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.NotebookViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    viewModel: NotebookViewModel,
    notebookId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Ensure notebook is loaded
    LaunchedEffect(notebookId) {
        viewModel.loadNotebook(notebookId)
    }

    val notebook by viewModel.activeNotebook.collectAsStateWithLifecycle()

    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }
    var exportFormat by remember { mutableStateOf<String?>(null) } // "PDF", "PNG", or ".notebook"
    var showSuccessDialog by remember { mutableStateOf(false) }
    var lastExportedUri by remember { mutableStateOf<Uri?>(null) }

    val parsedColor = remember(notebook?.coverColorHex) {
        try {
            Color(android.graphics.Color.parseColor(notebook?.coverColorHex ?: "#3F51B5"))
        } catch (e: Exception) {
            Color(0xFF3F51B5)
        }
    }

    // SAF launchers
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            exportFormat = "PDF"
            isExporting = true
            exportProgress = 0f
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        viewModel.exportSingleNotebookToPdfStream(notebookId, os) { progress ->
                            exportProgress = progress
                        }
                    }
                    lastExportedUri = uri
                    isExporting = false
                    showSuccessDialog = true
                } catch (e: Exception) {
                    isExporting = false
                    snackbarHostState.showSnackbar("Failed to export PDF: ${e.localizedMessage}")
                }
            }
        }
    }

    val pngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            exportFormat = "ZIP of PNG images"
            isExporting = true
            exportProgress = 0f
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        viewModel.exportSingleNotebookToPngZipStream(notebookId, os) { progress ->
                            exportProgress = progress
                        }
                    }
                    lastExportedUri = uri
                    isExporting = false
                    showSuccessDialog = true
                } catch (e: Exception) {
                    isExporting = false
                    snackbarHostState.showSnackbar("Failed to export PNG ZIP: ${e.localizedMessage}")
                }
            }
        }
    }

    val notebookLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            exportFormat = "LuxeNotes Raw Backup (.notebook)"
            isExporting = true
            exportProgress = 0.5f
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        viewModel.exportSingleNotebookToStream(notebookId, os)
                    }
                    exportProgress = 1f
                    lastExportedUri = uri
                    isExporting = false
                    showSuccessDialog = true
                } catch (e: Exception) {
                    isExporting = false
                    snackbarHostState.showSnackbar("Failed to export raw notebook: ${e.localizedMessage}")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export Notebook") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("export_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Document outline representation
            Card(
                modifier = Modifier
                    .width(180.dp)
                    .height(220.dp)
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(parsedColor),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(Icons.Default.Book, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = notebook?.title ?: "Notebook",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${notebook?.pageCount ?: 0} Pages",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Text(
                text = "Choose Export Format",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Render vector handwriting pages with high resolution directly into standard PDF sheets, high fidelity PNG image archives, or standalone backup files.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // PDF Option Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!isExporting) {
                            val defaultName = "${notebook?.title ?: "Notebook"}.pdf"
                            pdfLauncher.launch(defaultName)
                        }
                    }
                    .testTag("export_pdf_card"),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        }
                        Column {
                            Text("Export as PDF document", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Best for printing or sharing vector notes", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            // PNG Option Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!isExporting) {
                            val defaultName = "${notebook?.title ?: "Notebook"}_images.zip"
                            pngLauncher.launch(defaultName)
                        }
                    }
                    .testTag("export_png_card"),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Column {
                            Text("Export as PNG images", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("High resolution graphics zip folder", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            // Raw Notebook Backup Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!isExporting) {
                            val defaultName = "${notebook?.title ?: "Notebook"}.notebook"
                            notebookLauncher.launch(defaultName)
                        }
                    }
                    .testTag("export_notebook_card"),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        }
                        Column {
                            Text("Export as raw .notebook backup", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Transfer or backup drawings completely in JSON", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            // Real-time animated export process
            AnimatedVisibility(
                visible = isExporting,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Compiling and drawing vector paths in $exportFormat format...",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { exportProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }

    // EXPORT SUCCESS & INTERACTION SHARING DIALOG
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) },
            title = { Text("Export Complete", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
            text = {
                Text(
                    text = "Your notebook '${notebook?.title}' was successfully compiled and saved using Android Storage Access Framework (SAF) in $exportFormat format.",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        // Launch actual native android sharing sheet
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = when (exportFormat) {
                                "PDF" -> "application/pdf"
                                "ZIP of PNG images" -> "application/zip"
                                else -> "application/octet-stream"
                            }
                            putExtra(Intent.EXTRA_SUBJECT, "Sharing Notebook: ${notebook?.title}")
                            putExtra(Intent.EXTRA_STREAM, lastExportedUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share notes document using"))
                    },
                    modifier = Modifier.testTag("export_share_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Document")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSuccessDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}
