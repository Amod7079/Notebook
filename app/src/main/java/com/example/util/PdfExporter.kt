package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import com.example.data.model.Notebook
import com.example.data.model.Page
import com.example.data.model.Stroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

object PdfExporter {

    @Suppress("Deprecation")
    suspend fun exportToPdf(
        context: Context,
        notebook: Notebook,
        pages: List<Page>,
        strokesProvider: suspend (Long) -> List<Stroke>,
        outputStream: OutputStream,
        onProgress: (Float) -> Unit,
        // Optional custom rendering settings
        lineColorHex: String = "",
        customSpacing: Float = 32f,
        customThickness: Float = 1.5f,
        customOpacity: Float = 0.8f,
        marginEnabled: Boolean = true,
        leftMarginColorHex: String = "#FFCDD2",
        rightMarginColorHex: String = "#FFCDD2"
    ) = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()

        try {
            val totalPages = pages.size
            pages.forEachIndexed { index, page ->
                val width = if (page.width > 0) page.width.toInt() else 1000
                val height = if (page.height > 0) page.height.toInt() else 1500

                val pageInfo = PdfDocument.PageInfo.Builder(width, height, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas

                val strokes = strokesProvider(page.id)
                drawPageToCanvas(
                    canvas = canvas,
                    page = page,
                    strokes = strokes,
                    width = width.toFloat(),
                    height = height.toFloat(),
                    lineColorHex = lineColorHex,
                    customSpacing = customSpacing,
                    customThickness = customThickness,
                    customOpacity = customOpacity,
                    marginEnabled = marginEnabled,
                    leftMarginColorHex = leftMarginColorHex,
                    rightMarginColorHex = rightMarginColorHex
                )

                pdfDocument.finishPage(pdfPage)

                // Update progress callback
                onProgress((index + 1).toFloat() / totalPages.toFloat())
            }

            pdfDocument.writeTo(outputStream)
        } finally {
            pdfDocument.close()
        }
    }

    suspend fun exportToPngZip(
        context: Context,
        notebook: Notebook,
        pages: List<Page>,
        strokesProvider: suspend (Long) -> List<Stroke>,
        outputStream: OutputStream,
        onProgress: (Float) -> Unit,
        // Optional custom rendering settings
        lineColorHex: String = "",
        customSpacing: Float = 32f,
        customThickness: Float = 1.5f,
        customOpacity: Float = 0.8f,
        marginEnabled: Boolean = true,
        leftMarginColorHex: String = "#FFCDD2",
        rightMarginColorHex: String = "#FFCDD2"
    ) = withContext(Dispatchers.IO) {
        val totalPages = pages.size
        java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(outputStream)).use { zos ->
            pages.forEachIndexed { index, page ->
                val width = if (page.width > 0) page.width.toInt() else 1000
                val height = if (page.height > 0) page.height.toInt() else 1500

                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                val strokes = strokesProvider(page.id)
                drawPageToCanvas(
                    canvas = canvas,
                    page = page,
                    strokes = strokes,
                    width = width.toFloat(),
                    height = height.toFloat(),
                    lineColorHex = lineColorHex,
                    customSpacing = customSpacing,
                    customThickness = customThickness,
                    customOpacity = customOpacity,
                    marginEnabled = marginEnabled,
                    leftMarginColorHex = leftMarginColorHex,
                    rightMarginColorHex = rightMarginColorHex
                )

                zos.putNextEntry(java.util.zip.ZipEntry("page_${index + 1}.png"))
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, zos)
                zos.closeEntry()
                bitmap.recycle()

                // Update progress callback
                onProgress((index + 1).toFloat() / totalPages.toFloat())
            }
            zos.flush()
        }
    }

    fun drawPageToCanvas(
        canvas: Canvas,
        page: Page,
        strokes: List<Stroke>,
        width: Float,
        height: Float,
        lineColorHex: String = "",
        customSpacing: Float = 32f,
        customThickness: Float = 1.5f,
        customOpacity: Float = 0.8f,
        marginEnabled: Boolean = true,
        leftMarginColorHex: String = "#FFCDD2",
        rightMarginColorHex: String = "#FFCDD2"
    ) {
        // 1. Draw Page Background
        val bgPaint = Paint().apply {
            style = Paint.Style.FILL
            try {
                color = Color.parseColor(page.backgroundColorHex)
            } catch (e: Exception) {
                color = Color.WHITE
            }
        }
        canvas.drawRect(0f, 0f, width, height, bgPaint)

        // 2. Draw Page Template Background
        drawPageTemplate(
            canvas = canvas,
            type = page.templateType,
            width = width,
            height = height,
            pageBgColorHex = page.backgroundColorHex,
            lineColorHex = lineColorHex,
            customSpacing = customSpacing,
            customThickness = customThickness,
            customOpacity = customOpacity,
            marginEnabled = marginEnabled,
            leftMarginColorHex = leftMarginColorHex,
            rightMarginColorHex = rightMarginColorHex
        )

        // 3. Draw Vector Strokes on top
        drawStrokes(canvas, strokes)
    }

    private fun drawPageTemplate(
        canvas: Canvas,
        type: String,
        width: Float,
        height: Float,
        pageBgColorHex: String,
        lineColorHex: String,
        customSpacing: Float,
        customThickness: Float,
        customOpacity: Float,
        marginEnabled: Boolean,
        leftMarginColorHex: String,
        rightMarginColorHex: String
    ) {
        val isDarkBg = isDarkColor(pageBgColorHex)
        val defaultLineColor = if (isDarkBg) "#FFFFFF" else "#000000"
        val resolvedLineHex = if (lineColorHex.isNotEmpty()) lineColorHex else defaultLineColor

        val templateLinePaint = Paint().apply {
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeWidth = customThickness
            try {
                color = Color.parseColor(resolvedLineHex)
            } catch (e: Exception) {
                color = if (isDarkBg) Color.WHITE else Color.GRAY
            }
            alpha = (customOpacity * 255).toInt().coerceIn(0, 255)
        }

        val autoLeftCol = if (isDarkBg) Color.parseColor("#E53E3E") else Color.parseColor("#FFCDD2")
        val leftMarginPaint = Paint().apply {
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeWidth = customThickness * 1.5f
            try {
                color = Color.parseColor(leftMarginColorHex)
            } catch (e: Exception) {
                color = autoLeftCol
            }
        }

        val autoRightCol = if (isDarkBg) Color.parseColor("#3182CE") else Color.parseColor("#E2E8F0")
        val rightMarginPaint = Paint().apply {
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeWidth = customThickness
            try {
                color = Color.parseColor(rightMarginColorHex)
            } catch (e: Exception) {
                color = autoRightCol
            }
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
                while (currentY < height) {
                    canvas.drawLine(0f, currentY, width, currentY, templateLinePaint)
                    currentY += spacing
                }

                if (marginEnabled) {
                    canvas.drawLine(130f, 0f, 130f, height, leftMarginPaint)
                    canvas.drawLine(width - 100f, 0f, width - 100f, height, rightMarginPaint)
                }
            }

            "HANDWRITING_PRACTICE" -> {
                val spacing = finalSpacing * 1.2f
                var currentY = 100f
                while (currentY + spacing < height) {
                    // Top line
                    canvas.drawLine(0f, currentY, width, currentY, templateLinePaint)

                    // Dashed guidance middle line
                    val dashPaint = Paint(templateLinePaint).apply {
                        alpha = (customOpacity * 0.5f * 255).toInt().coerceIn(0, 255)
                    }
                    val dashWidth = 15f
                    val dashGap = 10f
                    var x = 0f
                    val halfY = currentY + spacing * 0.5f
                    while (x < width) {
                        canvas.drawLine(x, halfY, (x + dashWidth).coerceAtMost(width), halfY, dashPaint)
                        x += dashWidth + dashGap
                    }

                    // Bottom line
                    canvas.drawLine(0f, currentY + spacing, width, currentY + spacing, templateLinePaint)

                    currentY += spacing * 1.6f
                }

                if (marginEnabled) {
                    canvas.drawLine(130f, 0f, 130f, height, leftMarginPaint)
                }
            }

            "GRAPH", "GRID_PAPER", "GRAPH_PAPER", "GRAPH_GRID" -> {
                var currentX = 0f
                while (currentX < width) {
                    canvas.drawLine(currentX, 0f, currentX, height, templateLinePaint)
                    currentX += finalSpacing
                }
                var currentY = 0f
                while (currentY < height) {
                    canvas.drawLine(0f, currentY, width, currentY, templateLinePaint)
                    currentY += finalSpacing
                }
            }

            "ENGINEERING_GRID" -> {
                val majorInterval = 5
                var indexX = 0
                var currentX = 0f
                while (currentX < width) {
                    val isMajor = indexX % majorInterval == 0
                    val ep = Paint(templateLinePaint).apply {
                        strokeWidth = if (isMajor) customThickness * 1.8f else customThickness
                        alpha = if (isMajor) (customOpacity * 255).toInt() else (customOpacity * 0.35f * 255).toInt()
                    }
                    canvas.drawLine(currentX, 0f, currentX, height, ep)
                    currentX += finalSpacing * 0.5f
                    indexX++
                }

                var indexY = 0
                var currentY = 0f
                while (currentY < height) {
                    val isMajor = indexY % majorInterval == 0
                    val ep = Paint(templateLinePaint).apply {
                        strokeWidth = if (isMajor) customThickness * 1.8f else customThickness
                        alpha = if (isMajor) (customOpacity * 255).toInt() else (customOpacity * 0.35f * 255).toInt()
                    }
                    canvas.drawLine(0f, currentY, width, currentY, ep)
                    currentY += finalSpacing * 0.5f
                    indexY++
                }
            }

            "DOT_GRID", "DOT_GRID_PAPER" -> {
                val dotPaint = Paint().apply {
                    style = Paint.Style.FILL
                    isAntiAlias = true
                    try {
                        color = Color.parseColor(resolvedLineHex)
                    } catch (e: Exception) {
                        color = if (isDarkBg) Color.WHITE else Color.GRAY
                    }
                    alpha = (customOpacity * 255).toInt().coerceIn(0, 255)
                }
                val radius = (customThickness * 1.2f).coerceAtLeast(1.5f)
                var x = finalSpacing
                while (x < width) {
                    var y = finalSpacing
                    while (y < height) {
                        canvas.drawCircle(x, y, radius, dotPaint)
                        y += finalSpacing
                    }
                    x += finalSpacing
                }
            }

            "MUSIC_SHEET" -> {
                val spacing = finalSpacing * 0.4f
                val staffGap = finalSpacing * 1.5f
                var currentY = 100f
                while (currentY + (spacing * 4) < height) {
                    for (i in 0..4) {
                        val y = currentY + (i * spacing)
                        canvas.drawLine(50f, y, width - 50f, y, templateLinePaint)
                    }
                    currentY += (spacing * 4) + staffGap
                }
            }

            "CORNELL" -> {
                canvas.drawLine(250f, 0f, 250f, height - 250f, leftMarginPaint)
                canvas.drawLine(0f, height - 250f, width, height - 250f, templateLinePaint)

                var currentY = 120f
                while (currentY < height - 250f) {
                    canvas.drawLine(254f, currentY, width, currentY, templateLinePaint)
                    currentY += finalSpacing
                }
            }
        }
    }

    private fun drawStrokes(canvas: Canvas, strokes: List<Stroke>) {
        for (stroke in strokes) {
            val pointsStr = stroke.pointsData
            if (pointsStr.isBlank()) continue

            val points = try {
                pointsStr.split(" ").map {
                    val coords = it.split(",")
                    if (coords.size >= 2) {
                        Pair(coords[0].toFloat(), coords[1].toFloat())
                    } else {
                        Pair(0f, 0f)
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }

            if (points.size < 2) continue

            val paint = Paint().apply {
                style = Paint.Style.STROKE
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = stroke.thickness
                try {
                    color = Color.parseColor(stroke.colorHex)
                } catch (e: Exception) {
                    color = Color.BLACK
                }
                alpha = (stroke.opacity * 255f).toInt().coerceIn(0, 255)
            }

            // Highlighters are transparent and can use color filter blend mode if supported,
            // or just transparency. Standard alpha setting handles this beautifully.

            val path = Path()
            path.moveTo(points[0].first, points[0].second)
            for (i in 1 until points.size) {
                path.lineTo(points[i].first, points[i].second)
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun isDarkColor(hex: String): Boolean {
        return try {
            val color = Color.parseColor(hex)
            val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
            darkness >= 0.5
        } catch (e: Exception) {
            false
        }
    }
}
