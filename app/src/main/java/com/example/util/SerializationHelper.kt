package com.example.util

import com.example.data.model.Notebook
import com.example.data.model.Page
import com.example.data.model.Stroke
import org.json.JSONArray
import org.json.JSONObject

object SerializationHelper {

    suspend fun exportNotebookToJson(
        notebook: Notebook,
        pages: List<Page>,
        strokesProvider: suspend (Long) -> List<Stroke>
    ): String {
        val root = JSONObject().apply {
            put("id", notebook.id)
            put("title", notebook.title)
            put("coverColorHex", notebook.coverColorHex)
            put("pageCount", notebook.pageCount)
            put("createdTime", notebook.createdTime)
            put("updatedTime", notebook.updatedTime)
            put("isFavorite", notebook.isFavorite)
            put("folder", notebook.folder)
            put("defaultTemplate", notebook.defaultTemplate)
            put("defaultBgColorHex", notebook.defaultBgColorHex)
            put("defaultPenColorHex", notebook.defaultPenColorHex)
            put("defaultWritingMode", notebook.defaultWritingMode)

            val pagesArray = JSONArray()
            for (page in pages) {
                val pageObj = JSONObject().apply {
                    put("id", page.id)
                    put("notebookId", page.notebookId)
                    put("pageIndex", page.pageIndex)
                    put("templateType", page.templateType)
                    put("backgroundColorHex", page.backgroundColorHex)
                    put("infiniteCanvas", page.infiniteCanvas)
                    put("width", page.width.toDouble())
                    put("height", page.height.toDouble())

                    val strokesList = strokesProvider(page.id)
                    val strokesArray = JSONArray()
                    for (stroke in strokesList) {
                        val strokeObj = JSONObject().apply {
                            put("id", stroke.id)
                            put("pageId", stroke.pageId)
                            put("toolType", stroke.toolType)
                            put("colorHex", stroke.colorHex)
                            put("thickness", stroke.thickness.toDouble())
                            put("opacity", stroke.opacity.toDouble())
                            put("pointsData", stroke.pointsData)
                        }
                        strokesArray.put(strokeObj)
                    }
                    put("strokes", strokesArray)
                }
                pagesArray.put(pageObj)
            }
            put("pages", pagesArray)
        }
        return root.toString(2)
    }

    suspend fun importNotebookFromJson(
        jsonStr: String,
        onInsertNotebook: suspend (Notebook) -> Long,
        onInsertPage: suspend (Page) -> Long,
        onInsertStroke: suspend (Stroke) -> Unit
    ): Long {
        val root = JSONObject(jsonStr)
        val notebook = Notebook(
            title = root.getString("title"),
            coverColorHex = root.optString("coverColorHex", "#3F51B5"),
            pageCount = root.optInt("pageCount", 0),
            createdTime = root.optLong("createdTime", System.currentTimeMillis()),
            updatedTime = System.currentTimeMillis(),
            isFavorite = root.optBoolean("isFavorite", false),
            folder = root.optString("folder", "General"),
            defaultTemplate = root.optString("defaultTemplate", "RULED"),
            defaultBgColorHex = root.optString("defaultBgColorHex", "#FFFFFF"),
            defaultPenColorHex = root.optString("defaultPenColorHex", "#000000"),
            defaultWritingMode = root.optString("defaultWritingMode", "STYLUS_ONLY")
        )

        val newNotebookId = onInsertNotebook(notebook)

        val pagesArray = root.optJSONArray("pages")
        if (pagesArray != null) {
            for (i in 0 until pagesArray.length()) {
                val pageObj = pagesArray.getJSONObject(i)
                val page = Page(
                    notebookId = newNotebookId,
                    pageIndex = pageObj.optInt("pageIndex", i),
                    templateType = pageObj.optString("templateType", "RULED"),
                    backgroundColorHex = pageObj.optString("backgroundColorHex", "#FFFFFF"),
                    infiniteCanvas = pageObj.optBoolean("infiniteCanvas", false),
                    width = pageObj.optDouble("width", 1000.0).toFloat(),
                    height = pageObj.optDouble("height", 1500.0).toFloat()
                )

                val newPageId = onInsertPage(page)

                val strokesArray = pageObj.optJSONArray("strokes")
                if (strokesArray != null) {
                    for (j in 0 until strokesArray.length()) {
                        val strokeObj = strokesArray.getJSONObject(j)
                        val stroke = Stroke(
                            pageId = newPageId,
                            toolType = strokeObj.getString("toolType"),
                            colorHex = strokeObj.getString("colorHex"),
                            thickness = strokeObj.optDouble("thickness", 5.0).toFloat(),
                            opacity = strokeObj.optDouble("opacity", 1.0).toFloat(),
                            pointsData = strokeObj.getString("pointsData")
                        )
                        onInsertStroke(stroke)
                    }
                }
            }
        }
        return newNotebookId
    }
}
