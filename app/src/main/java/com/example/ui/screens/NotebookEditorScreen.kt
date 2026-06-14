package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as CanvasStroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Notebook
import com.example.data.model.Page
import com.example.data.model.Stroke
import com.example.ui.viewmodel.NotebookViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import android.view.MotionEvent

data class StrokePoint(val x: Float, val y: Float, val pressure: Float = 1.0f)


@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun NotebookEditorScreen(
    viewModel: NotebookViewModel,
    notebookId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToExport: (Long) -> Unit
) {
    // Load notebook details on enter
    LaunchedEffect(notebookId) {
        viewModel.loadNotebook(notebookId)
    }

    val notebook by viewModel.activeNotebook.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val currentPageIndex by viewModel.currentPageIndex.collectAsStateWithLifecycle()
    val page by viewModel.currentPage.collectAsStateWithLifecycle()
    val strokes by viewModel.strokes.collectAsStateWithLifecycle()

    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()

    val activeTool by viewModel.toolType.collectAsStateWithLifecycle()
    val activeColorHex by viewModel.colorHex.collectAsStateWithLifecycle()
    val activeThickness by viewModel.thickness.collectAsStateWithLifecycle()
    val activeOpacity by viewModel.opacity.collectAsStateWithLifecycle()

    val eraserMode by viewModel.eraserMode.collectAsStateWithLifecycle()
    val laserMode by viewModel.laserMode.collectAsStateWithLifecycle()
    val laserPoints by viewModel.laserPoints.collectAsStateWithLifecycle()
    val presentationMode by viewModel.presentationMode.collectAsStateWithLifecycle()
    val writingMode by viewModel.writingMode.collectAsStateWithLifecycle()

    // Laser pointer customization parameters
    val laserFadeSpeed by viewModel.laserFadeSpeed.collectAsStateWithLifecycle()
    val laserTrailLength by viewModel.laserTrailLength.collectAsStateWithLifecycle()
    val laserGlowIntensity by viewModel.laserGlowIntensity.collectAsStateWithLifecycle()

    // Canvas customizing parameters
    val rulingLineColorHex by viewModel.rulingLineColorHex.collectAsStateWithLifecycle()
    val rulingThickness by viewModel.rulingThickness.collectAsStateWithLifecycle()
    val rulingSpacing by viewModel.rulingSpacing.collectAsStateWithLifecycle()
    val showMarginLine by viewModel.showMarginLine.collectAsStateWithLifecycle()
    val leftMarginColorHex by viewModel.leftMarginColorHex.collectAsStateWithLifecycle()
    val rightMarginColorHex by viewModel.rightMarginColorHex.collectAsStateWithLifecycle()
    val rulingOpacity by viewModel.rulingOpacity.collectAsStateWithLifecycle()
    val zoomPercentage by viewModel.zoomPercentage.collectAsStateWithLifecycle()

    // Sheet and dialog states
    var showPenSettingsSheet by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showPagesListDialog by remember { mutableStateOf(false) }
    var showPageStyleDialog by remember { mutableStateOf(false) }
    var activeSettingsTab by remember { mutableStateOf(0) }

    // Canvas navigation and toggles
    var scale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var writeModeLocked by remember { mutableStateOf(true) } // True = Write, False = Workspace drag
    var isToolbarVisible by remember { mutableStateOf(true) } // Full screen mode gesture

    // Active stroke path capture
    val currentStrokePoints = remember { mutableStateListOf<StrokePoint>() }

    // Bi-directional zoom sync
    LaunchedEffect(scale) {
        viewModel.setZoomPercentage((scale * 100).toInt())
    }
    LaunchedEffect(zoomPercentage) {
        val targetScale = zoomPercentage / 100f
        if ((scale * 100).toInt() != zoomPercentage) {
            scale = targetScale.coerceIn(0.25f, 5.0f)
        }
    }

    // Dynamic laser pointer pulsing ring
    val infiniteTransition = rememberInfiniteTransition(label = "laser_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Laser trail decay loop
    LaunchedEffect(laserPoints, laserFadeSpeed, activeTool) {
        if (activeTool == "LASER" && laserPoints.isNotEmpty()) {
            val totalFadeMs = when (laserFadeSpeed) {
                "FAST" -> 300L
                "SLOW" -> 800L
                else -> 500L
            }
            val stepDelay = (totalFadeMs / maxOf(1, laserPoints.size)).coerceIn(8L, 40L)
            delay(stepDelay)
            if (activeTool == "LASER" && laserPoints.isNotEmpty()) {
                viewModel.setLaserPoints(laserPoints.drop(1))
            }
        }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = isToolbarVisible,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = notebook?.title ?: "Loading...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF2ECC71), CircleShape)
                                )
                                Text(
                                    text = "Folder: ${notebook?.folder ?: "General"} • Page ${if (pages.isNotEmpty()) currentPageIndex + 1 else 0} of ${pages.size}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("editor_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Undo & Redo tools
                        IconButton(
                            onClick = { viewModel.performUndo() },
                            enabled = canUndo,
                            modifier = Modifier.testTag("action_undo")
                        ) {
                            Icon(
                                Icons.Default.Undo,
                                contentDescription = "Undo",
                                tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.performRedo() },
                            enabled = canRedo,
                            modifier = Modifier.testTag("action_redo")
                        ) {
                            Icon(
                                Icons.Default.Redo,
                                contentDescription = "Redo",
                                tint = if (canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                        }

                        // Reset viewport layout
                        IconButton(onClick = {
                            scale = 1f
                            panOffset = Offset.Zero
                        }) {
                            Icon(Icons.Default.AspectRatio, contentDescription = "Reset viewport", tint = MaterialTheme.colorScheme.primary)
                        }

                        // Clear Board Action
                        IconButton(onClick = { viewModel.clearPage() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Current Page", tint = MaterialTheme.colorScheme.error)
                        }

                        // Export and Share
                        IconButton(onClick = { onNavigateToExport(notebookId) }, modifier = Modifier.testTag("action_export_notebook")) {
                            Icon(Icons.Default.Share, contentDescription = "Export notes")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
    ) { innerPadding ->
        val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
            if (!writeModeLocked) {
                scale = (scale * zoomChange).coerceIn(0.5f, 4f)
                panOffset += offsetChange
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isToolbarVisible) innerPadding else PaddingValues(0.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .transformable(state = transformState)
        ) {
            // Infinite Viewport Canvas Card Wrapper
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = panOffset.x,
                        translationY = panOffset.y
                    )
            ) {
                if (page != null) {
                    val pageBgHex = page!!.backgroundColorHex ?: notebook?.defaultBgColorHex ?: "#FFFFFF"
                    Card(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(width = page!!.width.dp, height = page!!.height.dp)
                            .shadow(6.dp, RoundedCornerShape(2.dp))
                            .border(1.dp, Color.LightGray.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
                        shape = RoundedCornerShape(2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(android.graphics.Color.parseColor(pageBgHex))
                        )
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(writingMode, activeTool, eraserMode, laserMode, laserPoints, scale, panOffset) {
                                    awaitPointerEventScope {
                                        var isZooming = false
                                        var initialPinchDistance = 0f
                                        var initialScale = 1f
                                        var initialPinchCenter = Offset.Zero
                                        var initialPanOffset = Offset.Zero

                                        while (true) {
                                            val pointerEvent = awaitPointerEvent()
                                            if (pointerEvent.changes.isEmpty()) continue

                                            val allChanges = pointerEvent.changes
                                            val pressedChanges = allChanges.filter { it.pressed }
                                            val numPressed = pressedChanges.size
                                            val totalPointers = allChanges.size

                                            // Accidental stroke prevention: if multi-finger touch, scrap visual stroke immediately!
                                            if (totalPointers >= 2) {
                                                currentStrokePoints.clear()
                                            }

                                            val firstChange = allChanges.first()
                                            val toolType = firstChange.type

                                            val isStylusOnlyMode = writingMode == "STYLUS_ONLY"
                                            val isFingerOnlyMode = writingMode == "FINGER_ONLY"

                                            // Decide if gesture is zoom/pan:
                                            // 1) In Stylus Only, any finger (Touch) does pan/zoom
                                            // 2) In other modes, multi-touch (total pointers >= 2) does pan/zoom
                                            val isZoomOrPan = if (isStylusOnlyMode) {
                                                toolType == PointerType.Touch
                                            } else {
                                                totalPointers >= 2
                                            }

                                            if (isZoomOrPan) {
                                                if (numPressed >= 2) {
                                                    val p1 = pressedChanges[0]
                                                    val p2 = pressedChanges[1]
                                                    val currentDistance = (p1.position - p2.position).getDistance()
                                                    val currentCenter = (p1.position + p2.position) / 2f

                                                    if (!isZooming) {
                                                        isZooming = true
                                                        initialPinchDistance = currentDistance
                                                        initialScale = scale
                                                        initialPinchCenter = currentCenter
                                                        initialPanOffset = panOffset
                                                    } else {
                                                        if (initialPinchDistance > 10f) {
                                                            val zoomRatio = currentDistance / initialPinchDistance
                                                            // Minimum Zoom: 25%, Maximum Zoom: 500%
                                                            val newScale = (initialScale * zoomRatio).coerceIn(0.25f, 5.0f)
                                                            scale = newScale

                                                            // Smooth pan offset relative to zoom pivot point to prevent jumping
                                                            val deltaCenter = currentCenter - initialPinchCenter
                                                            panOffset = initialPanOffset + deltaCenter * scale
                                                        }
                                                    }
                                                } else if (numPressed == 1) {
                                                    // Single finger panning gesture
                                                    val change = pressedChanges[0]
                                                    if (change.previousPressed) {
                                                        val delta = change.position - change.previousPosition
                                                        panOffset = panOffset + delta * scale
                                                    }
                                                    isZooming = false
                                                }
                                                
                                                allChanges.forEach { it.consume() }
                                                continue
                                            }

                                            // If no active multi-finger touch is zooming, abort zoom state
                                            if (numPressed < 2) {
                                                isZooming = false
                                            }

                                            // Standard single interaction path (Drawing, Erasing, Laser)
                                            if (totalPointers == 1) {
                                                val change = allChanges[0]
                                                val localPos = change.position
                                                val pressure = change.pressure.coerceIn(0.1f, 3.0f)

                                                // Filter tool interaction
                                                val isBlocked = (isStylusOnlyMode && toolType == PointerType.Touch) ||
                                                                (isFingerOnlyMode && toolType == PointerType.Stylus)
                                                if (isBlocked) {
                                                    continue
                                                }

                                                if (change.pressed) {
                                                    val isInitialTouch = !change.previousPressed
                                                    change.consume()
                                                    if (isInitialTouch) {
                                                        if (activeTool == "LASER") {
                                                            viewModel.setLaserPoints(listOf(localPos.x to localPos.y))
                                                        } else if (activeTool == "ERASER") {
                                                            handleEraserEvent(localPos, eraserMode, strokes, viewModel)
                                                        } else {
                                                            currentStrokePoints.clear()
                                                            currentStrokePoints.add(StrokePoint(localPos.x, localPos.y, pressure))
                                                        }
                                                    } else {
                                                        if (activeTool == "LASER") {
                                                            viewModel.setLaserPoints(laserPoints + (localPos.x to localPos.y))
                                                        } else if (activeTool == "ERASER") {
                                                            handleEraserEvent(localPos, eraserMode, strokes, viewModel)
                                                        } else {
                                                            currentStrokePoints.add(StrokePoint(localPos.x, localPos.y, pressure))
                                                        }
                                                    }
                                                } else {
                                                    if (change.previousPressed) {
                                                        change.consume()
                                                        if (activeTool == "LASER") {
                                                            // Fades out naturally using LaunchEffect timer loop
                                                        } else if (activeTool != "ERASER") {
                                                            val pStr = currentStrokePoints.joinToString(" ") { "${it.x},${it.y},${it.pressure}" }
                                                            viewModel.saveStrokeWithPressure(pStr)
                                                            currentStrokePoints.clear()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                        ) {
                            // 1. Draw grid template backdrop
                            drawPageBgTemplate(
                                type = page!!.templateType,
                                pageBgHex = pageBgHex,
                                customColorHex = rulingLineColorHex,
                                customThickness = rulingThickness,
                                customSpacing = rulingSpacing,
                                marginEnabled = showMarginLine,
                                leftMarginColorHex = leftMarginColorHex,
                                rightMarginColorHex = rightMarginColorHex,
                                customOpacity = rulingOpacity
                            )

                            // 2. Draw completed vector strokes
                            for (stroke in strokes) {
                                val strokePoints = parsePoints(stroke.pointsData)
                                if (strokePoints.isEmpty()) continue

                                val strokeColor = try {
                                    Color(android.graphics.Color.parseColor(stroke.colorHex)).copy(alpha = stroke.opacity)
                                } catch (e: Exception) {
                                    Color.Black.copy(alpha = stroke.opacity)
                                }

                                if (stroke.toolType == "HIGHLIGHTER") {
                                    val drawPath = Path().apply {
                                        val first = strokePoints.first()
                                        moveTo(first.x, first.y)
                                        for (i in 1 until strokePoints.size) {
                                            val p = strokePoints[i]
                                            lineTo(p.x, p.y)
                                        }
                                    }
                                    drawPath(
                                        path = drawPath,
                                        color = strokeColor,
                                        style = CanvasStroke(
                                            width = stroke.thickness,
                                            cap = StrokeCap.Square,
                                            join = StrokeJoin.Miter
                                        ),
                                        blendMode = BlendMode.Multiply
                                    )
                                } else {
                                    // Pen, Pencil, Fountain with pressure variable thickness rendering
                                    val len = strokePoints.size
                                    if (len > 1) {
                                        for (i in 1 until len) {
                                            val p1 = strokePoints[i - 1]
                                            val p2 = strokePoints[i]
                                            val pointPressure = (p1.pressure + p2.pressure) / 2f
                                            val computedWidth = stroke.thickness * pointPressure
                                            
                                            drawLine(
                                                color = strokeColor,
                                                start = Offset(p1.x, p1.y),
                                                end = Offset(p2.x, p2.y),
                                                strokeWidth = computedWidth,
                                                cap = StrokeCap.Round
                                            )
                                        }
                                    } else if (len == 1) {
                                        val single = strokePoints.first()
                                        drawCircle(
                                            color = strokeColor,
                                            radius = stroke.thickness * single.pressure / 2f,
                                            center = Offset(single.x, single.y)
                                        )
                                    }
                                }
                            }

                            // 3. Draw active hand stroke (under drawing)
                            if (currentStrokePoints.isNotEmpty()) {
                                val activeColor = try {
                                    Color(android.graphics.Color.parseColor(activeColorHex)).copy(alpha = activeOpacity)
                                } catch (e: Exception) {
                                    Color.Black.copy(alpha = activeOpacity)
                                }

                                if (activeTool == "HIGHLIGHTER") {
                                    val activePath = Path().apply {
                                        val first = currentStrokePoints.first()
                                        moveTo(first.x, first.y)
                                        for (i in 1 until currentStrokePoints.size) {
                                            val p = currentStrokePoints[i]
                                            lineTo(p.x, p.y)
                                        }
                                    }
                                    drawPath(
                                        path = activePath,
                                        color = activeColor,
                                        style = CanvasStroke(
                                            width = activeThickness,
                                            cap = StrokeCap.Square,
                                            join = StrokeJoin.Miter
                                        ),
                                        blendMode = BlendMode.Multiply
                                    )
                                } else {
                                    val activeLen = currentStrokePoints.size
                                    if (activeLen > 1) {
                                        for (i in 1 until activeLen) {
                                            val p1 = currentStrokePoints[i - 1]
                                            val p2 = currentStrokePoints[i]
                                            val pointPressure = (p1.pressure + p2.pressure) / 2f
                                            val computedWidth = activeThickness * pointPressure
                                            
                                            drawLine(
                                                color = activeColor,
                                                start = Offset(p1.x, p1.y),
                                                end = Offset(p2.x, p2.y),
                                                strokeWidth = computedWidth,
                                                cap = StrokeCap.Round
                                            )
                                        }
                                    } else if (activeLen == 1) {
                                        val single = currentStrokePoints.first()
                                        drawCircle(
                                            color = activeColor,
                                            radius = activeThickness * single.pressure / 2f,
                                            center = Offset(single.x, single.y)
                                        )
                                    }
                                }
                            }

                             // 4. Draw Presentation laser pointers trail
                             if (activeTool == "LASER" && laserPoints.isNotEmpty()) {
                                 val pointerColor = when (laserMode) {
                                     "GREEN" -> Color(0xFF2ECC71)
                                     "BLUE" -> Color(0xFF3498DB)
                                     else -> Color(0xFFE74C3C)
                                 }
                                 
                                 val len = laserPoints.size
                                 val intensity = laserGlowIntensity.coerceIn(0.1f, 1.0f)

                                 // Draw beautifully tapering neon segments with dynamic coordinate fade progression
                                 if (len >= 2) {
                                     for (i in 1 until len) {
                                         val p1 = laserPoints[i - 1]
                                         val p2 = laserPoints[i]
                                         val progress = i.toFloat() / len

                                         // 1) Outer soft glow
                                         drawLine(
                                             color = pointerColor.copy(alpha = progress * 0.18f * intensity),
                                             start = Offset(p1.first, p1.second),
                                             end = Offset(p2.first, p2.second),
                                             strokeWidth = 28f,
                                             cap = StrokeCap.Round
                                         )

                                         // 2) Middle glowing halo
                                         drawLine(
                                             color = pointerColor.copy(alpha = progress * 0.45f * intensity),
                                             start = Offset(p1.first, p1.second),
                                             end = Offset(p2.first, p2.second),
                                             strokeWidth = 14f,
                                             cap = StrokeCap.Round
                                         )

                                         // 3) Solid bright white center core
                                         drawLine(
                                             color = Color.White.copy(alpha = progress * 0.95f),
                                             start = Offset(p1.first, p1.second),
                                             end = Offset(p2.first, p2.second),
                                             strokeWidth = 6f,
                                             cap = StrokeCap.Round
                                         )
                                     }
                                 }

                                 val head = laserPoints.last()
                                 val pulsingExtra = 8f * pulseScale

                                 // Beautiful glowing multi-ring laser head core with anti-aliasing
                                 drawCircle(
                                     color = pointerColor.copy(alpha = 0.22f * intensity),
                                     radius = 26f + pulsingExtra,
                                     center = Offset(head.first, head.second)
                                 )
                                 drawCircle(
                                     color = pointerColor.copy(alpha = 0.45f * intensity),
                                     radius = 16f + pulsingExtra * 0.5f,
                                     center = Offset(head.first, head.second)
                                 )
                                 drawCircle(
                                     color = Color.White,
                                     radius = 7f,
                                     center = Offset(head.first, head.second)
                                 )
                             }
                        }
                    }
                }
            }

            // FLOATING GLASSMORPHIC TOOL DOCK (Centered at Bottom)
            AnimatedVisibility(
                visible = isToolbarVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            ) {
                Card(
                    modifier = Modifier
                        .wrapContentWidth()
                        .shadow(16.dp, RoundedCornerShape(32.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(32.dp)),
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Page Control and Guide presets row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.selectPageIndex(currentPageIndex - 1) },
                                enabled = currentPageIndex > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ChevronLeft, "Prev Page", modifier = Modifier.size(20.dp))
                            }

                            Card(
                                onClick = { showPagesListDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.GridOn, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = "Page ${if (pages.isNotEmpty()) currentPageIndex + 1 else 0} / ${pages.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.selectPageIndex(currentPageIndex + 1) },
                                enabled = currentPageIndex < pages.size - 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ChevronRight, "Next Page", modifier = Modifier.size(20.dp))
                            }

                            VerticalDivider(modifier = Modifier.height(16.dp))

                            IconButton(onClick = { viewModel.addNewPage() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.AutoMirrored.Filled.NoteAdd, "Add Page", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }

                            IconButton(
                                onClick = { 
                                    activeSettingsTab = 0
                                    showPageStyleDialog = true 
                                }, 
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Outlined.Layers, "Templates", modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = { viewModel.deleteCurrentPage() },
                                enabled = pages.size > 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.DeleteForever, "Delete current", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                            }
                        }

                        // Ink styling tools row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Focus hand/write mode
                            var showWritingModeMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { showWritingModeMenu = true },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    val icon = when (writingMode) {
                                        "STYLUS_ONLY" -> Icons.Default.Palette
                                        "FINGER_ONLY" -> Icons.Default.Gesture
                                        else -> Icons.Default.Create
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = "Writing Mode Setting"
                                    )
                                }

                                DropdownMenu(
                                    expanded = showWritingModeMenu,
                                    onDismissRequest = { showWritingModeMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("✍️ Stylus Only (Palm Reject)") },
                                        onClick = {
                                            viewModel.setWritingMode("STYLUS_ONLY")
                                            showWritingModeMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("👆 Finger Only") },
                                        onClick = {
                                            viewModel.setWritingMode("FINGER_ONLY")
                                            showWritingModeMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("🤝 Finger + Stylus") },
                                        onClick = {
                                            viewModel.setWritingMode("FINGER_STYLUS")
                                            showWritingModeMenu = false
                                        }
                                    )
                                }
                            }

                            VerticalDivider(modifier = Modifier.height(20.dp))

                            // Pen
                            ToolButton(
                                icon = Icons.Outlined.Gesture,
                                label = "Pen",
                                active = activeTool == "BALL_PEN",
                                testTag = "tool_ball_pen",
                                onClick = {
                                    viewModel.setToolType("BALL_PEN")
                                    showPenSettingsSheet = true
                                }
                            )

                            // Highlighter
                            ToolButton(
                                icon = Icons.Default.BorderColor,
                                label = "Highlight",
                                active = activeTool == "HIGHLIGHTER",
                                testTag = "tool_highlighter",
                                onClick = {
                                    viewModel.setToolType("HIGHLIGHTER")
                                    showPenSettingsSheet = true
                                }
                            )

                            // Eraser
                            ToolButton(
                                icon = Icons.Default.AutoFixNormal,
                                label = "Eraser",
                                active = activeTool == "ERASER",
                                testTag = "tool_eraser_select",
                                onClick = {
                                    if (activeTool == "ERASER") {
                                        showPenSettingsSheet = true
                                    } else {
                                        viewModel.setToolType("ERASER")
                                    }
                                }
                            )

                            // Presentation Laser
                            ToolButton(
                                icon = Icons.Default.Adjust,
                                label = "Laser",
                                active = activeTool == "LASER",
                                testTag = "tool_laser",
                                onClick = {
                                    if (activeTool == "LASER") {
                                        activeSettingsTab = 1
                                        showPageStyleDialog = true
                                    } else {
                                        viewModel.setToolType("LASER")
                                        if (laserMode == null) {
                                            viewModel.setLaserPointerMode("RED")
                                        }
                                    }
                                }
                            )

                            VerticalDivider(modifier = Modifier.height(20.dp))

                            // Custom properties shortcut pill
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { showPenSettingsSheet = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(activeColorHex)))
                                        .border(1.dp, Color.White, CircleShape)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Floating Toolbar Minimizer/Focus mode floating trigger
            IconButton(
                onClick = { isToolbarVisible = !isToolbarVisible },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .shadow(6.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = if (isToolbarVisible) Icons.Default.Fullscreen else Icons.Default.FullscreenExit,
                    contentDescription = "Expand/Collapse Toolbar Tools",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    // MODAL PEN CUSTOMIZATION BOTTOM SHEET
    if (showPenSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPenSettingsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${activeTool.replace("_", " ")} Studio Style",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    IconButton(
                        onClick = { showPenSettingsSheet = false },
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    }
                }

                // BRUSH SIGNATURE WAVE PREVIEW CANVAS
                Text("Cursive Ink Preview", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)), RoundedCornerShape(12.dp))
                ) {
                    val previewColor = try {
                        Color(android.graphics.Color.parseColor(activeColorHex)).copy(alpha = activeOpacity)
                    } catch (e: Exception) {
                        Color.Black.copy(alpha = activeOpacity)
                    }

                    val midY = size.height / 2
                    val signaturePath = Path().apply {
                        moveTo(40F, midY)
                        cubicTo(
                            120F, midY - 30F,
                            180F, midY + 40F,
                            240F, midY - 20F
                        )
                        cubicTo(
                            300F, midY - 60F,
                            360F, midY + 50F,
                            420F, midY
                        )
                        quadraticTo(
                            size.width - 120F, midY - 25F,
                            size.width - 40F, midY
                        )
                    }

                    if (activeTool != "ERASER") {
                        drawPath(
                            path = signaturePath,
                            color = previewColor,
                            style = CanvasStroke(
                                width = activeThickness,
                                cap = if (activeTool == "HIGHLIGHTER") StrokeCap.Square else StrokeCap.Round,
                                join = StrokeJoin.Round
                            ),
                            blendMode = if (activeTool == "HIGHLIGHTER") BlendMode.Multiply else BlendMode.SrcOver
                        )
                    } else {
                        // Drawing eraser guide layout inside signature
                        drawCircle(
                            color = Color.LightGray,
                            radius = activeThickness + 10f,
                            center = Offset(size.width / 2, midY),
                            style = CanvasStroke(2F, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                        )
                        drawCircle(
                            color = Color.LightGray.copy(alpha = 0.3f),
                            radius = activeThickness + 10f,
                            center = Offset(size.width / 2, midY)
                        )
                    }
                }

                // SPEED BRUSH PRESETS SECTION
                if (activeTool != "ERASER" && activeTool != "LASER") {
                    Text("Brush Preset Shortcuts", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PresetButton(
                            label = "0.5mm Pencil",
                            colorHex = "#2C3E50",
                            onClick = {
                                viewModel.setToolType("PENCIL")
                                viewModel.setThickness(4f)
                                viewModel.setOpacity(0.7f)
                                viewModel.selectColor("#4F4F4F")
                            },
                            modifier = Modifier.weight(1f)
                        )
                        PresetButton(
                            label = "Cursive Ink",
                            colorHex = "#1D4ED8",
                            onClick = {
                                viewModel.setToolType("FOUNTAIN_PEN")
                                viewModel.setThickness(12f)
                                viewModel.setOpacity(1f)
                                viewModel.selectColor("#1D4ED8")
                            },
                            modifier = Modifier.weight(1f)
                        )
                        PresetButton(
                            label = "Neon Pen",
                            colorHex = "#FFC107",
                            onClick = {
                                viewModel.setToolType("HIGHLIGHTER")
                                viewModel.setThickness(28f)
                                viewModel.setOpacity(0.55f)
                                viewModel.selectColor("#FFEB3B")
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Sizing parameters
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Ink Thickness: ${activeThickness.toInt()}px",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = activeThickness,
                        onValueChange = { viewModel.setThickness(it) },
                        valueRange = 1f..60f,
                        modifier = Modifier.testTag("thickness_slider")
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Eraser Opacity: ${(activeOpacity * 100).toInt()}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = activeOpacity,
                        onValueChange = { viewModel.setOpacity(it) },
                        valueRange = 0.1f..1f,
                        modifier = Modifier.testTag("opacity_slider")
                    )
                }

                // Ink palette settings
                if (activeTool != "ERASER") {
                    Text(text = "Studio Color Spectrum", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    val studioInkSpectrum = listOf(
                        "#000000", "#1A1A1A", "#4A4A4A", "#1D4ED8",
                        "#2ECC71", "#FFC107", "#E74C3C", "#9B59B6",
                        "#1ABC9C", "#E67E22", "#FF6B6B", "#FFD200",
                        "#BDC3C7", "#D35400", "#16A085", "#2980B9"
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        modifier = Modifier.height(100.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(studioInkSpectrum) { _, hex ->
                            val color = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (activeColorHex == hex) 3.dp else 1.dp,
                                        color = if (activeColorHex == hex) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.selectColor(hex) }
                            )
                        }
                    }
                }

                // Eraser specific controls
                if (activeTool == "ERASER") {
                    Text(text = "Eraser Technique", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = eraserMode == "NORMAL",
                            onClick = { viewModel.setEraserMode("NORMAL") },
                            label = { Text("Normal Rubber") }
                        )
                        FilterChip(
                            selected = eraserMode == "STROKE",
                            onClick = { viewModel.setEraserMode("STROKE") },
                            label = { Text("Whole Stroke Eraser") }
                        )
                    }
                }

                // Presents laser specific controls
                if (activeTool == "LASER") {
                    Text(text = "Presenter Laser Pointer options", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = laserMode == "RED",
                            onClick = { viewModel.setLaserPointerMode("RED") },
                            label = { Text("Red Pointer") }
                        )
                        FilterChip(
                            selected = laserMode == "GREEN",
                            onClick = { viewModel.setLaserPointerMode("GREEN") },
                            label = { Text("Green Pointer") }
                        )
                        FilterChip(
                            selected = presentationMode,
                            onClick = { viewModel.togglePresentationMode() },
                            label = { Text("Long Trail mode") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { showPenSettingsSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Confirm Selection")
                }
            }
        }
    }

    // PAGE STYLE GUIDELINES TEMPLATE & COLOR DIALOG
    if (showPageStyleDialog) {
        PageStyleDialog(
            viewModel = viewModel,
            currentTemplate = page?.templateType ?: "RULED",
            currentBgColorHex = page?.backgroundColorHex ?: notebook?.defaultBgColorHex ?: "#FFFFFF",
            onDismiss = { showPageStyleDialog = false },
            onConfirmTemplate = { activeTemplate ->
                viewModel.changeCurrentPageTemplate(activeTemplate)
            },
            onConfirmBgColor = { colorHex ->
                viewModel.changeCurrentPageBackgroundColor(colorHex)
            },
            initialTab = activeSettingsTab
        )
    }

    // ALL PAGES OVERVIEW GRID DIALOG (GoodNotes Style)
    if (showPagesListDialog) {
        Dialog(onDismissRequest = { showPagesListDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Notebook Grid Page Overview",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        IconButton(onClick = { showPagesListDialog = false }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }

                    // Grid layouts of pages
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.height(260.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(pages) { idx, p ->
                            val isCurrentPage = currentPageIndex == idx
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clickable {
                                        viewModel.selectPageIndex(idx)
                                        showPagesListDialog = false
                                    },
                                border = BorderStroke(
                                    width = if (isCurrentPage) 3.dp else 1.dp,
                                    color = if (isCurrentPage) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.4f)
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCurrentPage) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(10.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Receipt,
                                        contentDescription = null,
                                        tint = if (isCurrentPage) MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Page ${idx + 1}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    Text(p.templateType, fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showPagesListDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close Organizer")
                    }
                }
            }
        }
    }
}

@Composable
fun ToolButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PresetButton(
    label: String,
    colorHex: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chipColor = Color(android.graphics.Color.parseColor(colorHex))
    Card(
        modifier = modifier
            .height(38.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(chipColor)
            )
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// Logic evaluating the eraser brush colliding with vectors
private fun handleEraserEvent(
    touchOffset: Offset,
    eraserMode: String,
    strokes: List<Stroke>,
    viewModel: NotebookViewModel
) {
    val eraserRadius = 30f // Collision bounding box pixels
    val touchX = touchOffset.x
    val touchY = touchOffset.y

    for (stroke in strokes) {
        val points = parsePoints(stroke.pointsData)
        val collides = points.any { p ->
            hypot(touchX - p.x, touchY - p.y) < eraserRadius
        }

        if (collides) {
            if (eraserMode == "STROKE") {
                // Erase entire line stroke
                viewModel.deleteStrokeLocally(stroke)
            } else {
                // For simplified Normal eraser simulation, we also erase the stroke
                viewModel.deleteStrokeLocally(stroke)
            }
        }
    }
}

private fun isDarkColor(hex: String): Boolean {
    return try {
        val colorInt = android.graphics.Color.parseColor(hex)
        val red = android.graphics.Color.red(colorInt)
        val green = android.graphics.Color.green(colorInt)
        val blue = android.graphics.Color.blue(colorInt)
        // YIQ formula for perceived luminance
        val brightness = (red * 299 + green * 587 + blue * 114) / 1000
        brightness < 128
    } catch (e: Exception) {
        false
    }
}

// Draw page layout guidelines according to specified styles
private fun DrawScope.drawPageBgTemplate(
    type: String,
    pageBgHex: String,
    customColorHex: String = "",
    customThickness: Float = 1.5f,
    customSpacing: Float = 32f,
    marginEnabled: Boolean = true,
    leftMarginColorHex: String = "#FFCDD2",
    rightMarginColorHex: String = "#FFCDD2",
    customOpacity: Float = 0.8f
) {
    val canvasSize = size
    val isDark = isDarkColor(pageBgHex)

    // Resolve general styling
    val autoColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFF4A5568)
    val baseLineColor = if (customColorHex.isNotEmpty()) {
        try { Color(android.graphics.Color.parseColor(customColorHex)) } catch (e: Exception) { autoColor }
    } else {
        autoColor
    }
    val lineColor = baseLineColor.copy(alpha = customOpacity)

    val autoLeftCol = if (isDark) Color(0xFFE53E3E).copy(alpha = 0.8f) else Color(0xFFFFCDD2)
    val leftMarginColor = try {
        Color(android.graphics.Color.parseColor(leftMarginColorHex))
    } catch (e: Exception) {
        autoLeftCol
    }

    val autoRightCol = if (isDark) Color(0xFF3182CE).copy(alpha = 0.7f) else Color(0xFFE2E8F0)
    val rightMarginColor = try {
        Color(android.graphics.Color.parseColor(rightMarginColorHex))
    } catch (e: Exception) {
        autoRightCol
    }

    val finalSpacing = customSpacing.coerceIn(16f, 150f)

    when (type) {
        "RULED", "RULED_NOTEBOOK", "NARROW_RULED", "WIDE_RULED", "COLLEGE_RULED" -> {
            val spacing = when (type) {
                "NARROW_RULED" -> finalSpacing * 0.75f
                "WIDE_RULED" -> finalSpacing * 1.35f
                "COLLEGE_RULED" -> finalSpacing * 0.9f
                else -> finalSpacing
            }

            var currentY = 120f
            while (currentY < canvasSize.height) {
                drawLine(
                    color = lineColor,
                    start = Offset(0f, currentY),
                    end = Offset(canvasSize.width, currentY),
                    strokeWidth = customThickness
                )
                currentY += spacing
            }

            if (marginEnabled) {
                // Vertical Left Pink Margin
                drawLine(
                    color = leftMarginColor,
                    start = Offset(130f, 0f),
                    end = Offset(130f, canvasSize.height),
                    strokeWidth = customThickness * 1.5f
                )
                // Optional Vertical Right Light Blue Margin
                drawLine(
                    color = rightMarginColor,
                    start = Offset(canvasSize.width - 100f, 0f),
                    end = Offset(canvasSize.width - 100f, canvasSize.height),
                    strokeWidth = customThickness
                )
            }
        }

        "HANDWRITING_PRACTICE" -> {
            val spacing = finalSpacing * 1.2f
            var currentY = 100f
            while (currentY + spacing < canvasSize.height) {
                // Top solid line
                drawLine(
                    color = lineColor,
                    start = Offset(0f, currentY),
                    end = Offset(canvasSize.width, currentY),
                    strokeWidth = customThickness
                )
                // Middle dashed practice guideway
                val dashWidth = 15f
                val dashGap = 10f
                var x = 0f
                while (x < canvasSize.width) {
                    drawLine(
                        color = lineColor.copy(alpha = customOpacity * 0.5f),
                        start = Offset(x, currentY + spacing * 0.5f),
                        end = Offset((x + dashWidth).coerceAtMost(canvasSize.width), currentY + spacing * 0.5f),
                        strokeWidth = customThickness * 0.8f
                    )
                    x += dashWidth + dashGap
                }
                // Bottom solid line
                drawLine(
                    color = lineColor,
                    start = Offset(0f, currentY + spacing),
                    end = Offset(canvasSize.width, currentY + spacing),
                    strokeWidth = customThickness
                )

                currentY += spacing * 1.6f
            }

            if (marginEnabled) {
                drawLine(
                    color = leftMarginColor,
                    start = Offset(130f, 0f),
                    end = Offset(130f, canvasSize.height),
                    strokeWidth = customThickness * 1.5f
                )
            }
        }

        "GRAPH", "GRID_PAPER", "GRAPH_PAPER", "GRAPH_GRID" -> {
            var currentX = 0f
            while (currentX < canvasSize.width) {
                drawLine(
                    color = lineColor,
                    start = Offset(currentX, 0f),
                    end = Offset(currentX, canvasSize.height),
                    strokeWidth = customThickness
                )
                currentX += finalSpacing
            }
            var currentY = 0f
            while (currentY < canvasSize.height) {
                drawLine(
                    color = lineColor,
                    start = Offset(0f, currentY),
                    end = Offset(canvasSize.width, currentY),
                    strokeWidth = customThickness
                )
                currentY += finalSpacing
            }
        }

        "ENGINEERING_GRID" -> {
            val majorInterval = 5
            var indexX = 0
            var currentX = 0f
            while (currentX < canvasSize.width) {
                val isMajor = indexX % majorInterval == 0
                drawLine(
                    color = if (isMajor) lineColor else lineColor.copy(alpha = customOpacity * 0.35f),
                    start = Offset(currentX, 0f),
                    end = Offset(currentX, canvasSize.height),
                    strokeWidth = if (isMajor) customThickness * 1.8f else customThickness
                )
                currentX += finalSpacing * 0.5f
                indexX++
            }

            var indexY = 0
            var currentY = 0f
            while (currentY < canvasSize.height) {
                val isMajor = indexY % majorInterval == 0
                drawLine(
                    color = if (isMajor) lineColor else lineColor.copy(alpha = customOpacity * 0.35f),
                    start = Offset(0f, currentY),
                    end = Offset(canvasSize.width, currentY),
                    strokeWidth = if (isMajor) customThickness * 1.8f else customThickness
                )
                currentY += finalSpacing * 0.5f
                indexY++
            }
        }

        "DOT_GRID", "DOT_GRID_PAPER" -> {
            var x = finalSpacing
            while (x < canvasSize.width) {
                var y = finalSpacing
                while (y < canvasSize.height) {
                    drawCircle(
                        color = lineColor,
                        radius = (customThickness * 1.2f).coerceAtLeast(1.5f),
                        center = Offset(x, y)
                    )
                    y += finalSpacing
                }
                x += finalSpacing
            }
        }

        "MUSIC_SHEET" -> {
            val spacing = finalSpacing * 0.4f
            val staffGap = finalSpacing * 1.5f
            var currentY = 100f
            while (currentY + (spacing * 4) < canvasSize.height) {
                for (i in 0..4) {
                    val y = currentY + (i * spacing)
                    drawLine(
                        color = lineColor,
                        start = Offset(50f, y),
                        end = Offset(canvasSize.width - 50f, y),
                        strokeWidth = customThickness
                    )
                }
                currentY += (spacing * 4) + staffGap
            }
        }

        "CORNELL" -> {
            drawLine(
                color = leftMarginColor,
                start = Offset(250f, 0f),
                end = Offset(250f, canvasSize.height - 250f),
                strokeWidth = customThickness * 2.2f
            )
            drawLine(
                color = lineColor,
                start = Offset(0f, canvasSize.height - 250f),
                end = Offset(canvasSize.width, canvasSize.height - 250f),
                strokeWidth = customThickness * 2f
            )
            var currentY = 120f
            while (currentY < canvasSize.height - 250f) {
                drawLine(
                    color = lineColor,
                    start = Offset(254f, currentY),
                    end = Offset(canvasSize.width, currentY),
                    strokeWidth = customThickness
                )
                currentY += finalSpacing
            }
        }

        else -> {
            // "PLAIN" or Blank - Pure sheet rendering
        }
    }
}

fun parsePoints(data: String): List<StrokePoint> {
    if (data.isBlank()) return emptyList()
    return try {
        data.split(" ").map {
            val coords = it.split(",")
            if (coords.size >= 3) {
                StrokePoint(coords[0].toFloat(), coords[1].toFloat(), coords[2].toFloat())
            } else if (coords.size == 2) {
                StrokePoint(coords[0].toFloat(), coords[1].toFloat(), 1.0f)
            } else {
                StrokePoint(0f, 0f, 1.0f)
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}

// Page Template and Custom Settings Tabbed Dialog
@Composable
fun PageStyleDialog(
    viewModel: NotebookViewModel,
    currentTemplate: String,
    currentBgColorHex: String,
    onDismiss: () -> Unit,
    onConfirmTemplate: (String) -> Unit,
    onConfirmBgColor: (String) -> Unit,
    initialTab: Int = 0
) {
    var activeTab by remember { mutableStateOf(initialTab) }

    // Collect configurations
    val laserMode by viewModel.laserMode.collectAsStateWithLifecycle()
    val laserFadeSpeed by viewModel.laserFadeSpeed.collectAsStateWithLifecycle()
    val laserTrailLength by viewModel.laserTrailLength.collectAsStateWithLifecycle()
    val laserGlowIntensity by viewModel.laserGlowIntensity.collectAsStateWithLifecycle()

    val rulingLineColorHex by viewModel.rulingLineColorHex.collectAsStateWithLifecycle()
    val rulingThickness by viewModel.rulingThickness.collectAsStateWithLifecycle()
    val rulingSpacing by viewModel.rulingSpacing.collectAsStateWithLifecycle()
    val showMarginLine by viewModel.showMarginLine.collectAsStateWithLifecycle()
    val leftMarginColorHex by viewModel.leftMarginColorHex.collectAsStateWithLifecycle()
    val rightMarginColorHex by viewModel.rightMarginColorHex.collectAsStateWithLifecycle()
    val rulingOpacity by viewModel.rulingOpacity.collectAsStateWithLifecycle()
    val zoomPercentage by viewModel.zoomPercentage.collectAsStateWithLifecycle()

    val templates = listOf(
        "PLAIN" to "Plain Paper Blank Canvas",
        "RULED" to "Standard Ruled Layout",
        "NARROW_RULED" to "Narrow Ruled Book Guide",
        "WIDE_RULED" to "Wide Ruled Journal Grid",
        "COLLEGE_RULED" to "College Ruled Notebook",
        "HANDWRITING_PRACTICE" to "Dashed Handwriting Guideway",
        "GRAPH" to "Standard Box Grid Graph Board",
        "ENGINEERING_GRID" to "Major-Minor Science Quad Grid",
        "DOT_GRID" to "Dot Matrix Bullet Draft",
        "MUSIC_SHEET" to "Staff Lines Clef Sheet",
        "CORNELL" to "Cornell Lecture Outline Format"
    )

    val paperColors = listOf(
        "#FFFFFF" to "White",
        "#121212" to "Black",
        "#2D3748" to "Dark Gray",
        "#EDF2F7" to "Light Gray",
        "#FDF6E3" to "Cream",
        "#FEFCBF" to "Yellow",
        "#EBF8FF" to "Blue",
        "#F0FFF4" to "Green",
        "#FFF5F5" to "Pink"
    )

    val customLineColors = listOf(
        "" to "Automatic",
        "#4A5568" to "Slate Blue",
        "#718096" to "Cool Gray",
        "#64748B" to "Stone Slate",
        "#0284C7" to "Ocean Blue",
        "#0891B2" to "Pacific Cyan",
        "#16A34A" to "Forest Green",
        "#D97706" to "Sunset Amber",
        "#E53E3E" to "Rose Red"
    )

    val marginColors = listOf(
        "#FFCDD2" to "Soft Pink",
        "#E53E3E" to "Rose Red",
        "#BEE3F8" to "Soft Blue",
        "#C6F6D5" to "Soft Green",
        "#FEFCBF" to "Soft Yellow",
        "#E2E8F0" to "Soft Gray"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Unified header
                Text(
                    text = "Application Settings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(14.dp))

                // Custom elegant tab selection pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("📝 Canvas Layout", "🔦 Laser Pointer").forEachIndexed { index, title ->
                        val selected = activeTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { activeTab = index }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Scrollable container
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (activeTab == 0) {
                        // CANVAS SETTINGS TAB
                        item {
                            Text(
                                text = "Ruling Line Color",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // Row of custom colors
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(customLineColors.size) { idx ->
                                    val (hex, name) = customLineColors[idx]
                                    val isSelected = rulingLineColorHex == hex
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (hex.isEmpty()) {
                                                    if (isDarkColor(currentBgColorHex)) Color.White else Color.Black
                                                } else {
                                                    Color(android.graphics.Color.parseColor(hex))
                                                }
                                            )
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                                shape = CircleShape
                                            )
                                            .clickable { viewModel.setRulingLineColorHex(hex) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = if (hex == "#FFFFFF") Color.Black else Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Line Custom Spacing (${rulingSpacing.toInt()} dp)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = rulingSpacing,
                                onValueChange = { viewModel.setRulingSpacing(it) },
                                valueRange = 16f..120f
                            )
                        }

                        item {
                            Text(
                                text = "Line Thickness (${String.format("%.1f", rulingThickness)} px)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = rulingThickness,
                                onValueChange = { viewModel.setRulingThickness(it) },
                                valueRange = 0.5f..5f
                            )
                        }

                        item {
                            Text(
                                text = "Line Opacity (${(rulingOpacity * 100).toInt()}%)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = rulingOpacity,
                                onValueChange = { viewModel.setRulingOpacity(it) },
                                valueRange = 0.1f..1.0f
                            )
                        }

                        item {
                            Text(
                                text = "Manual Zoom Override ($zoomPercentage%)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = zoomPercentage.toFloat(),
                                onValueChange = { viewModel.setZoomPercentage(it.toInt()) },
                                valueRange = 25f..500f
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val isChecked = showMarginLine
                                Text(
                                    text = "Show Vertical Margin Lines",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                                Switch(
                                    checked = isChecked,
                                    onCheckedChange = { viewModel.setShowMarginLine(it) }
                                )
                            }
                        }

                        if (showMarginLine) {
                            item {
                                Text(
                                    text = "Left Margin Guideline Color",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(marginColors.size) { idx ->
                                        val (hex, name) = marginColors[idx]
                                        val isSelected = leftMarginColorHex.equals(hex, ignoreCase = true)
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(android.graphics.Color.parseColor(hex)))
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                                    shape = CircleShape
                                                )
                                                .clickable { viewModel.setLeftMarginColorHex(hex) }
                                        )
                                    }
                                }
                            }

                            item {
                                Text(
                                    text = "Right Margin Guideline Color",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(marginColors.size) { idx ->
                                        val (hex, name) = marginColors[idx]
                                        val isSelected = rightMarginColorHex.equals(hex, ignoreCase = true)
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(android.graphics.Color.parseColor(hex)))
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                                    shape = CircleShape
                                                )
                                                .clickable { viewModel.setRightMarginColorHex(hex) }
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Background Paper Color",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Color preset grid
                            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                columns = GridCells.Fixed(5),
                                modifier = Modifier.height(110.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(paperColors.size) { index ->
                                    val (colorHex, name) = paperColors[index]
                                    val isSelected = currentBgColorHex.equals(colorHex, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(CircleShape)
                                            .background(Color(android.graphics.Color.parseColor(colorHex)))
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                                shape = CircleShape
                                            )
                                            .clickable { onConfirmBgColor(colorHex) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = if (colorHex == "#FFFFFF" || colorHex == "#FEFCBF" || colorHex == "#FDF6E3" || colorHex == "#EDF2F7") Color.Black else Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Ruling Template Types",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        items(templates.size) { index ->
                            val (type, description) = templates[index]
                            val isSelected = currentTemplate == type
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable { onConfirmTemplate(type) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = type,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = description,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    } else {
                        // LASER POINTER SETTINGS TAB
                        item {
                            Text(
                                text = "Active Laser Color",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf(
                                    "RED" to Color(0xFFE74C3C),
                                    "GREEN" to Color(0xFF2ECC71),
                                    "BLUE" to Color(0xFF3498DB)
                                ).forEach { (colorName, colorVal) ->
                                    val isSel = laserMode == colorName
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSel) colorVal else colorVal.copy(alpha = 0.22f))
                                            .border(
                                                width = if (isSel) 3.dp else 1.dp,
                                                color = if (isSel) Color.White else Color.Transparent,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { viewModel.setLaserPointerMode(colorName) }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = colorName,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) Color.White else colorVal,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Presenter Fade Speed Preset",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf(
                                    "FAST" to "Fast (300ms)",
                                    "MEDIUM" to "Medium (500ms)",
                                    "SLOW" to "Slow (800ms)"
                                ).forEach { (speed, label) ->
                                    val isSel = laserFadeSpeed == speed
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSel) MaterialTheme.colorScheme.primaryContainer 
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { viewModel.setLaserFadeSpeed(speed) }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Presenter Trail Max Length ($laserTrailLength segments)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = laserTrailLength.toFloat(),
                                onValueChange = { viewModel.setLaserTrailLength(it.toInt()) },
                                valueRange = 10f..100f
                            )
                        }

                        item {
                            Text(
                                text = "Presenter Outer Glow Halo (${(laserGlowIntensity * 100).toInt()}%)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = laserGlowIntensity,
                                onValueChange = { viewModel.setLaserGlowIntensity(it) },
                                valueRange = 0.1f..1.0f
                            )
                        }

                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Adjust,
                                        contentDescription = "Laser Tip",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = "Presentation Guidelines",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Presentation laser sweeps are visual-only transients and are automatically discarded from local files. They are never written nor saved as notebook marks.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Apply & Close")
                }
            }
        }
    }
}
