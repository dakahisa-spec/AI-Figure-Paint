package com.aifigurepaint.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs

enum class DuplicatePolicy { KEEP_EXISTING, UPDATE_FROM_EXCEL, SKIP }

data class ExcelIssue(val sheet: String, val row: Int, val message: String)

data class ExcelImportPreview(
    val newPaints: Int,
    val duplicatePaints: Int,
    val newRecipes: Int,
    val newProjects: Int,
    val testResults: Int,
    val errors: List<ExcelIssue>,
    val workbook: ImportedWorkbook,
)

data class ExcelExportSummary(val paints: Int, val recipes: Int, val projects: Int, val testResults: Int)
data class ExcelImportSummary(val paints: Int, val recipes: Int, val projects: Int, val testResults: Int)

data class ImportedWorkbook(
    val paints: List<ImportedPaint>,
    val projects: List<ImportedProject>,
    val recipes: List<ImportedRecipe>,
    val versions: List<ImportedVersion>,
    val items: List<ImportedItem>,
    val tests: List<ImportedTest>,
)

data class ImportedPaint(
    val sourceId: Long,
    val brand: String,
    val series: String,
    val code: String?,
    val name: String,
    val koreanName: String,
    val colorValue: Int,
    val owned: Boolean,
    val stockLevel: Int,
    val favorite: Boolean,
    val memo: String,
)

data class ImportedProject(
    val sourceId: Long,
    val name: String,
    val modelName: String,
    val status: String,
    val startDate: Long,
    val memo: String,
    val updatedAt: Long,
    val photoPaths: List<String>,
)

data class ImportedRecipe(
    val sourceId: Long,
    val name: String,
    val baseTotalMl: Double,
    val projectIds: List<Long>,
    val memo: String,
    val createdAt: Long,
    val updatedAt: Long,
    val colorValue: Int,
    val favorite: Boolean,
    val photoPaths: List<String>,
)

data class ImportedVersion(
    val sourceId: Long,
    val recipeSourceId: Long,
    val number: Int,
    val label: String,
    val name: String,
    val colorValue: Int,
    val totalMl: Double,
    val aiGenerated: Boolean,
    val prompt: String,
    val createdAt: Long,
)

data class ImportedItem(
    val recipeSourceId: Long,
    val versionSourceId: Long,
    val paintSourceId: Long,
    val ratioPercent: Double,
    val amountMl: Double,
    val sortOrder: Int,
)

data class ImportedTest(
    val sourceId: Long,
    val recipeSourceId: Long,
    val versionSourceId: Long,
    val date: Long,
    val evaluations: String,
    val memo: String,
    val photoPaths: List<String>,
)

class ExcelBackupService(private val context: Context, private val db: AppDatabase) {
    suspend fun exportTo(uri: Uri): ExcelExportSummary {
        val paints = db.paintDao().allOnce()
        val projects = db.projectDao().allOnce()
        val recipes = db.recipeDao().allRecipesOnce()
        val items = db.recipeDao().allItemsOnce()
        val refs = db.recipeDao().allProjectRefsOnce()
        val versions = db.versionDao().allOnce()
        val tests = db.testResultDao().allOnce()
        val photos = db.photoDao().allOnce()
        val paintById = paints.associateBy { it.id }

        val sheets = linkedMapOf<String, List<List<Any?>>>(
            "Paints" to buildList {
                add(listOf("ID", "Brand", "Series", "Product Code", "Name", "Korean Name", "HEX", "Owned", "Stock", "Stock Level", "Favorite", "Memo", "Created", "Updated"))
                paints.forEach { p -> add(listOf(p.id, p.brand, p.series, p.productCode.orEmpty(), p.name, p.koreanName, hex(p.colorValue), p.owned, StockLevel.label(p.stockLevel), p.stockLevel, p.favorite, p.memo, instant(p.createdAt), instant(p.updatedAt))) }
            },
            "Projects" to buildList {
                add(listOf("Project ID", "Project Name", "Model Name", "Status", "Start Date", "Memo", "Updated", "Photo Paths"))
                projects.forEach { p -> add(listOf(p.id, p.name, p.modelName, p.status, instantOrBlank(p.startDate), p.memo, instant(p.updatedAt), photoPaths(photos, PhotoOwner.PROJECT, p.id))) }
            },
            "Recipes" to buildList {
                add(listOf("Recipe ID", "Recipe Name", "Version", "Base Total ml", "Project IDs", "Memo", "Created", "Updated", "HEX", "Favorite", "Photo Paths"))
                recipes.forEach { r ->
                    val latest = versions.filter { it.recipeId == r.id }.maxOfOrNull { it.versionNumber } ?: 0
                    val projectIds = (refs.filter { it.recipeId == r.id }.map { it.projectId } + listOfNotNull(r.projectId)).distinct().joinToString("|")
                    add(listOf(r.id, r.name, latest, r.baseTotalMl, projectIds, r.memo, instant(r.createdAt), instant(r.updatedAt), hex(r.resultColorValue), r.favorite, photoPaths(photos, PhotoOwner.RECIPE, r.id)))
                }
            },
            "RecipeVersions" to buildList {
                add(listOf("Version ID", "Recipe ID", "Version", "Label", "Name", "HEX", "Total ml", "AI Generated", "Source Prompt", "Created"))
                versions.forEach { v -> add(listOf(v.id, v.recipeId, v.versionNumber, v.label, v.snapshotName, hex(v.snapshotColorValue), v.snapshotTotalMl, v.aiGenerated, v.sourcePrompt, instant(v.createdAt))) }
            },
            "RecipeItems" to buildList {
                add(listOf("Recipe ID", "Version ID", "Paint ID", "Paint Name", "Ratio %", "Base ml", "Sort Order"))
                items.forEach { item ->
                    val total = recipes.firstOrNull { it.id == item.recipeId }?.baseTotalMl ?: 0.0
                    add(listOf(item.recipeId, 0, item.paintId, paintById[item.paintId]?.name.orEmpty(), ratio(item.baseAmountMl, total), item.baseAmountMl, item.sortOrder))
                }
                versions.forEach { version ->
                    val rows = runCatching { JSONArray(version.ingredientSnapshot) }.getOrNull() ?: JSONArray()
                    for (index in 0 until rows.length()) {
                        val row = rows.optJSONObject(index) ?: continue
                        val paintId = row.optLong("paintId")
                        val amount = row.optDouble("amountMl")
                        add(listOf(version.recipeId, version.id, paintId, paintById[paintId]?.name.orEmpty(), ratio(amount, version.snapshotTotalMl), amount, index))
                    }
                }
            },
            "TestResults" to buildList {
                add(listOf("Test ID", "Recipe ID", "Recipe Version ID", "Test Date", "Evaluations", "Memo", "Photo Paths"))
                tests.forEach { t -> add(listOf(t.id, t.recipeId, t.recipeVersionId, instant(t.testDate), t.evaluations, t.memo, photoPaths(photos, PhotoOwner.TEST_RESULT, t.id))) }
            },
        )
        context.contentResolver.openOutputStream(uri, "w")?.use { output -> Xlsx.write(output, sheets) }
            ?: error("선택한 위치에 파일을 만들 수 없습니다.")
        return ExcelExportSummary(paints.size, recipes.size, projects.size, tests.size)
    }

    suspend fun preview(uri: Uri): ExcelImportPreview {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Excel 파일을 읽을 수 없습니다.")
        val table = Xlsx.read(ByteArrayInputStream(bytes))
        val issues = mutableListOf<ExcelIssue>()
        listOf("Paints", "Recipes", "RecipeVersions", "RecipeItems", "Projects", "TestResults").forEach {
            if (table[it] == null) issues += ExcelIssue(it, 0, "필수 Sheet가 없습니다.")
        }
        val requiredColumns = mapOf(
            "Paints" to listOf("ID", "Brand", "Name", "HEX", "Owned", "Stock", "Favorite"),
            "Projects" to listOf("Project ID", "Project Name", "Status", "Start Date", "Memo", "Updated"),
            "Recipes" to listOf("Recipe ID", "Recipe Name", "Version", "Base Total ml", "Project IDs", "Memo", "Created", "Updated"),
            "RecipeVersions" to listOf("Version ID", "Recipe ID", "Version", "Label", "Name", "HEX", "Total ml"),
            "RecipeItems" to listOf("Recipe ID", "Version ID", "Paint ID", "Ratio %", "Base ml", "Sort Order"),
            "TestResults" to listOf("Test ID", "Recipe ID", "Recipe Version ID", "Test Date", "Evaluations", "Memo"),
        )
        requiredColumns.forEach { (sheet, required) ->
            val headers = table[sheet]?.firstOrNull().orEmpty().map { it.trim() }.toSet()
            required.filterNot { it in headers }.forEach { issues += ExcelIssue(sheet, 1, "필수 Column '$it'이 없습니다.") }
        }
        val workbook = ImportedWorkbook(
            paints = parseRows(table["Paints"], "Paints", issues, ::parsePaint),
            projects = parseRows(table["Projects"], "Projects", issues, ::parseProject),
            recipes = parseRows(table["Recipes"], "Recipes", issues, ::parseRecipe),
            versions = parseRows(table["RecipeVersions"], "RecipeVersions", issues, ::parseVersion),
            items = parseRows(table["RecipeItems"], "RecipeItems", issues, ::parseItem),
            tests = parseRows(table["TestResults"], "TestResults", issues, ::parseTest),
        )
        validateRelations(workbook, issues)
        val currentPaints = db.paintDao().allOnce()
        val duplicatePaints = workbook.paints.count { incoming -> currentPaints.any { samePaint(it, incoming) } }
        val currentProjects = db.projectDao().allOnce()
        val currentRecipes = db.recipeDao().allRecipesOnce()
        return ExcelImportPreview(
            newPaints = workbook.paints.size - duplicatePaints,
            duplicatePaints = duplicatePaints,
            newRecipes = workbook.recipes.count { incoming -> currentRecipes.none { it.name.equals(incoming.name, true) } },
            newProjects = workbook.projects.count { incoming -> currentProjects.none { it.name.equals(incoming.name, true) && it.modelName.equals(incoming.modelName, true) } },
            testResults = workbook.tests.size,
            errors = issues,
            workbook = workbook,
        )
    }

    suspend fun importData(preview: ExcelImportPreview, policy: DuplicatePolicy): ExcelImportSummary {
        require(preview.errors.isEmpty()) { "오류가 있는 Excel 파일은 가져올 수 없습니다." }
        val source = preview.workbook
        var paintCount = 0
        var projectCount = 0
        var recipeCount = 0
        var testCount = 0
        db.withTransaction {
            val paintMap = mutableMapOf<Long, Long>()
            val existingPaints = db.paintDao().allOnce().toMutableList()
            source.paints.forEach { row ->
                val duplicate = existingPaints.firstOrNull { samePaint(it, row) }
                if (duplicate == null) {
                    val id = db.paintDao().insert(row.toEntity())
                    paintMap[row.sourceId] = id
                    existingPaints += row.toEntity().copy(id = id)
                    paintCount++
                } else {
                    paintMap[row.sourceId] = duplicate.id
                    if (policy == DuplicatePolicy.UPDATE_FROM_EXCEL) {
                        db.paintDao().update(row.toEntity().copy(id = duplicate.id, createdAt = duplicate.createdAt))
                        paintCount++
                    }
                }
            }

            val projectMap = mutableMapOf<Long, Long>()
            val existingProjects = db.projectDao().allOnce()
            source.projects.forEach { row ->
                val existing = existingProjects.firstOrNull { it.name.equals(row.name, true) && it.modelName.equals(row.modelName, true) }
                val id = existing?.id ?: db.projectDao().insert(row.toEntity()).also { projectCount++ }
                projectMap[row.sourceId] = id
                if (existing == null) savePhotos(PhotoOwner.PROJECT, id, row.photoPaths)
            }

            val recipeMap = mutableMapOf<Long, Long>()
            val existingRecipes = db.recipeDao().allRecipesOnce()
            source.recipes.forEach { row ->
                val existing = existingRecipes.firstOrNull { it.name.equals(row.name, true) }
                val primaryProject = row.projectIds.firstNotNullOfOrNull { projectMap[it] }
                val id = existing?.id ?: db.recipeDao().insertRecipe(row.toEntity(primaryProject)).also { recipeCount++ }
                recipeMap[row.sourceId] = id
                if (existing == null) {
                    val current = source.items.filter { it.recipeSourceId == row.sourceId && it.versionSourceId == 0L }
                    val entities = current.mapNotNull { item -> paintMap[item.paintSourceId]?.let { paintId -> MixRecipeItemEntity(recipeId = id, paintId = paintId, baseAmountMl = item.amountMl, sortOrder = item.sortOrder) } }
                    if (entities.isNotEmpty()) db.recipeDao().insertItems(entities)
                    val refs = row.projectIds.mapNotNull { projectMap[it] }.distinct().mapIndexed { index, projectId -> ProjectRecipeCrossRef(projectId, id, sortOrder = index) }
                    if (refs.isNotEmpty()) db.recipeDao().insertProjectRefs(refs)
                    savePhotos(PhotoOwner.RECIPE, id, row.photoPaths)
                }
            }

            val versionMap = mutableMapOf<Long, Long>()
            source.versions.sortedBy { it.number }.forEach { row ->
                val recipeId = recipeMap[row.recipeSourceId] ?: return@forEach
                val versionItems = source.items.filter { it.versionSourceId == row.sourceId }.mapNotNull { item ->
                    paintMap[item.paintSourceId]?.let { paintId -> JSONObject().put("paintId", paintId).put("amountMl", item.amountMl) }
                }
                val newId = db.versionDao().insert(
                    RecipeVersionEntity(
                        recipeId = recipeId,
                        versionNumber = db.versionDao().nextVersionNumber(recipeId),
                        label = row.label,
                        snapshotName = row.name,
                        snapshotColorValue = row.colorValue,
                        snapshotTotalMl = row.totalMl,
                        ingredientSnapshot = JSONArray(versionItems).toString(),
                        aiGenerated = row.aiGenerated,
                        sourcePrompt = row.prompt,
                        createdAt = row.createdAt,
                    ),
                )
                versionMap[row.sourceId] = newId
            }
            source.tests.forEach { row ->
                val recipeId = recipeMap[row.recipeSourceId] ?: return@forEach
                val versionId = versionMap[row.versionSourceId] ?: return@forEach
                val testId = db.testResultDao().insert(TestResultEntity(recipeId = recipeId, recipeVersionId = versionId, testDate = row.date, evaluations = row.evaluations, memo = row.memo))
                savePhotos(PhotoOwner.TEST_RESULT, testId, row.photoPaths)
                testCount++
            }
        }
        return ExcelImportSummary(paintCount, recipeCount, projectCount, testCount)
    }

    private suspend fun savePhotos(ownerType: String, ownerId: Long, paths: List<String>) {
        if (paths.isNotEmpty()) db.photoDao().insertAll(paths.filter { it.isNotBlank() }.distinct().take(3).mapIndexed { index, path -> PhotoEntity(ownerType = ownerType, ownerId = ownerId, uri = path, sortOrder = index) })
    }

    private fun validateRelations(book: ImportedWorkbook, issues: MutableList<ExcelIssue>) {
        val paintIds = book.paints.map { it.sourceId }.toSet()
        val recipeIds = book.recipes.map { it.sourceId }.toSet()
        val versionIds = book.versions.map { it.sourceId }.toSet()
        fun duplicateIds(values: List<Long>, sheet: String, label: String) {
            values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { issues += ExcelIssue(sheet, 0, "중복 $label $it") }
        }
        duplicateIds(book.paints.map { it.sourceId }, "Paints", "ID")
        duplicateIds(book.projects.map { it.sourceId }, "Projects", "Project ID")
        duplicateIds(book.recipes.map { it.sourceId }, "Recipes", "Recipe ID")
        duplicateIds(book.versions.map { it.sourceId }, "RecipeVersions", "Version ID")
        duplicateIds(book.tests.map { it.sourceId }, "TestResults", "Test ID")
        book.items.forEachIndexed { index, row ->
            if (row.recipeSourceId !in recipeIds) issues += ExcelIssue("RecipeItems", index + 2, "알 수 없는 Recipe ID ${row.recipeSourceId}")
            if (row.paintSourceId !in paintIds) issues += ExcelIssue("RecipeItems", index + 2, "알 수 없는 Paint ID ${row.paintSourceId}")
            if (row.versionSourceId != 0L && row.versionSourceId !in versionIds) issues += ExcelIssue("RecipeItems", index + 2, "알 수 없는 Version ID ${row.versionSourceId}")
            if (row.amountMl <= 0) issues += ExcelIssue("RecipeItems", index + 2, "기준 ml는 0보다 커야 합니다.")
            if (row.ratioPercent !in 0.0..100.0) issues += ExcelIssue("RecipeItems", index + 2, "비율은 0~100% 범위여야 합니다.")
        }
        book.items.groupBy { it.recipeSourceId to it.versionSourceId }.forEach { (key, rows) ->
            val sum = rows.sumOf { it.ratioPercent }
            if (rows.isNotEmpty() && abs(sum - 100.0) > 0.5) issues += ExcelIssue("RecipeItems", 0, "Recipe ${key.first} / Version ${key.second} 비율 합계가 ${"%.2f".format(sum)}%입니다.")
        }
        book.tests.forEachIndexed { index, row ->
            if (row.recipeSourceId !in recipeIds || row.versionSourceId !in versionIds) issues += ExcelIssue("TestResults", index + 2, "레시피 또는 버전 연결이 올바르지 않습니다.")
        }
    }

    private fun <T> parseRows(rows: List<List<String>>?, sheet: String, issues: MutableList<ExcelIssue>, parser: (Map<String, String>) -> T): List<T> {
        if (rows.isNullOrEmpty()) return emptyList()
        val headers = rows.first().map { it.trim() }
        return rows.drop(1).mapIndexedNotNull { index, values ->
            if (values.all { it.isBlank() }) return@mapIndexedNotNull null
            val row = headers.mapIndexed { column, key -> key to values.getOrElse(column) { "" } }.toMap()
            runCatching { parser(row) }.onFailure { issues += ExcelIssue(sheet, index + 2, it.message ?: "행을 읽을 수 없습니다.") }.getOrNull()
        }
    }

    private fun parsePaint(r: Map<String, String>) = ImportedPaint(r.long("ID"), r.required("Brand"), r["Series"].orEmpty(), r["Product Code"].orEmpty().ifBlank { null }, r.required("Name"), r["Korean Name"].orEmpty(), parseHex(r.required("HEX")), r.bool("Owned"), parseStock(r), r.bool("Favorite"), r["Memo"].orEmpty())
    private fun parseProject(r: Map<String, String>) = ImportedProject(r.long("Project ID"), r.required("Project Name"), r["Model Name"].orEmpty(), r["Status"].orEmpty().ifBlank { ProjectStatus.PLANNED }, parseInstant(r["Start Date"]), r["Memo"].orEmpty(), parseInstant(r["Updated"]), paths(r["Photo Paths"]))
    private fun parseRecipe(r: Map<String, String>) = ImportedRecipe(r.long("Recipe ID"), r.required("Recipe Name"), r.double("Base Total ml"), ids(r["Project IDs"]), r["Memo"].orEmpty(), parseInstant(r["Created"]), parseInstant(r["Updated"]), parseHex(r["HEX"].orEmpty()), r.bool("Favorite"), paths(r["Photo Paths"]))
    private fun parseVersion(r: Map<String, String>) = ImportedVersion(r.long("Version ID"), r.long("Recipe ID"), r.int("Version"), r.required("Label"), r.required("Name"), parseHex(r.required("HEX")), r.double("Total ml"), r.bool("AI Generated"), r["Source Prompt"].orEmpty(), parseInstant(r["Created"]))
    private fun parseItem(r: Map<String, String>) = ImportedItem(r.long("Recipe ID"), r.long("Version ID"), r.long("Paint ID"), r.double("Ratio %"), r.double("Base ml"), r.int("Sort Order"))
    private fun parseTest(r: Map<String, String>) = ImportedTest(r.long("Test ID"), r.long("Recipe ID"), r.long("Recipe Version ID"), parseInstant(r["Test Date"]), r["Evaluations"].orEmpty(), r["Memo"].orEmpty(), paths(r["Photo Paths"]))

    private fun Map<String, String>.required(name: String) = this[name]?.trim().takeUnless { it.isNullOrBlank() } ?: error("$name 값이 없습니다.")
    private fun Map<String, String>.long(name: String) = required(name).toLongOrNull() ?: error("$name 숫자가 올바르지 않습니다.")
    private fun Map<String, String>.int(name: String) = required(name).toDoubleOrNull()?.toInt() ?: error("$name 숫자가 올바르지 않습니다.")
    private fun Map<String, String>.double(name: String) = required(name).toDoubleOrNull() ?: error("$name 숫자가 올바르지 않습니다.")
    private fun Map<String, String>.bool(name: String) = this[name].orEmpty().equals("true", true) || this[name] == "1" || this[name].orEmpty().equals("yes", true)
    private fun parseStock(row: Map<String, String>): Int = row["Stock Level"]?.toDoubleOrNull()?.toInt()?.coerceIn(0, 4) ?: when (row["Stock"].orEmpty().trim()) {
        "새 제품" -> StockLevel.NEW
        "많이 남음" -> StockLevel.MOST
        "절반" -> StockLevel.HALF
        "얼마 안 남음" -> StockLevel.LOW
        "소진" -> StockLevel.EMPTY
        else -> row["Stock"].orEmpty().toDoubleOrNull()?.toInt()?.coerceIn(0, 4) ?: error("Stock 값이 올바르지 않습니다.")
    }
    private fun parseInstant(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        val number = value.toDoubleOrNull() ?: error("날짜 값이 올바르지 않습니다.")
        if (number in 20_000.0..100_000.0) {
            return LocalDate.of(1899, 12, 30).plusDays(number.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        return number.toLong()
    }
    private fun ids(value: String?) = value.orEmpty().split('|').mapNotNull { it.trim().toLongOrNull() }
    private fun paths(value: String?) = value.orEmpty().split('|').map { it.trim() }.filter { it.isNotEmpty() }
    private fun samePaint(current: PaintEntity, incoming: ImportedPaint): Boolean = if (!incoming.code.isNullOrBlank()) current.brand.equals(incoming.brand, true) && current.productCode.equals(incoming.code, true) else current.brand.equals(incoming.brand, true) && current.name.equals(incoming.name, true)
    private fun ImportedPaint.toEntity() = PaintEntity(brand = brand, series = series, productCode = code, name = name, koreanName = koreanName, colorValue = colorValue, owned = owned, stockLevel = stockLevel, favorite = favorite, memo = memo)
    private fun ImportedProject.toEntity() = ProjectEntity(name = name, modelName = modelName, memo = memo, startDate = startDate, status = status, photoUri = photoPaths.firstOrNull(), updatedAt = updatedAt)
    private fun ImportedRecipe.toEntity(projectId: Long?) = MixRecipeEntity(projectId = projectId, name = name, baseTotalMl = baseTotalMl, memo = memo, resultColorValue = colorValue, favorite = favorite, photoUri = photoPaths.firstOrNull(), createdAt = createdAt, updatedAt = updatedAt)
    private fun instant(value: Long) = Instant.ofEpochMilli(value).toString()
    private fun instantOrBlank(value: Long) = if (value <= 0L) "" else instant(value)
    private fun photoPaths(all: List<PhotoEntity>, type: String, id: Long) = all.filter { it.ownerType == type && it.ownerId == id }.joinToString("|") { it.uri }
    private fun ratio(amount: Double, total: Double) = if (total > 0) amount / total * 100.0 else 0.0
    private fun hex(value: Int) = "#%06X".format(value and 0xFFFFFF)
    private fun parseHex(value: String) = runCatching { (0xFF000000L or value.removePrefix("#").takeLast(6).toLong(16)).toInt() }.getOrDefault(0xFF808080.toInt())
}

private object Xlsx {
    fun write(output: java.io.OutputStream, sheets: LinkedHashMap<String, List<List<Any?>>>) {
        ZipOutputStream(output).use { zip ->
            entry(zip, "[Content_Types].xml", contentTypes(sheets.size))
            entry(zip, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            entry(zip, "xl/workbook.xml", workbook(sheets.keys))
            entry(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            entry(zip, "xl/styles.xml", """<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""")
            sheets.values.forEachIndexed { index, rows -> entry(zip, "xl/worksheets/sheet${index + 1}.xml", sheet(rows)) }
        }
    }

    fun read(input: InputStream): Map<String, List<List<String>>> {
        val entries = mutableMapOf<String, ByteArray>()
        var totalBytes = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val item = zip.nextEntry ?: break
                if (!item.isDirectory) {
                    require(entries.size < 64) { "Excel 파일 내부 항목이 너무 많습니다." }
                    val bytes = readEntry(zip, 12 * 1024 * 1024)
                    totalBytes += bytes.size
                    require(totalBytes <= 32L * 1024 * 1024) { "Excel 파일이 너무 큽니다." }
                    entries[item.name] = bytes
                }
                zip.closeEntry()
            }
        }
        val shared = entries["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        val workbook = entries["xl/workbook.xml"] ?: error("올바른 XLSX 파일이 아닙니다.")
        val document = document(workbook)
        val names = document.getElementsByTagNameNS("*", "sheet")
        return buildMap {
            for (index in 0 until names.length) {
                val name = (names.item(index) as Element).getAttribute("name")
                val bytes = entries["xl/worksheets/sheet${index + 1}.xml"] ?: continue
                put(name, rows(bytes, shared))
            }
        }
    }

    private fun sheet(rows: List<List<Any?>>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
        rows.forEachIndexed { rowIndex, row ->
            append("<row r=\"").append(rowIndex + 1).append("\">")
            row.forEachIndexed { column, value ->
                val ref = columnName(column) + (rowIndex + 1)
                when (value) {
                    is Number -> append("<c r=\"").append(ref).append("\"").append(if (rowIndex == 0) " s=\"1\"" else "").append("><v>").append(value).append("</v></c>")
                    is Boolean -> append("<c r=\"").append(ref).append("\" t=\"b\"><v>").append(if (value) 1 else 0).append("</v></c>")
                    else -> append("<c r=\"").append(ref).append("\" t=\"inlineStr\"").append(if (rowIndex == 0) " s=\"1\"" else "").append("><is><t xml:space=\"preserve\">").append(xml(value?.toString().orEmpty())).append("</t></is></c>")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun rows(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val doc = document(bytes)
        val result = mutableListOf<List<String>>()
        val rowNodes = doc.getElementsByTagNameNS("*", "row")
        for (r in 0 until rowNodes.length) {
            val row = rowNodes.item(r) as Element
            val cells = row.getElementsByTagNameNS("*", "c")
            val values = mutableMapOf<Int, String>()
            var max = -1
            for (c in 0 until cells.length) {
                val cell = cells.item(c) as Element
                val index = columnIndex(cell.getAttribute("r"))
                val type = cell.getAttribute("t")
                val value = when (type) {
                    "inlineStr" -> cell.getElementsByTagNameNS("*", "t").item(0)?.textContent.orEmpty()
                    "s" -> shared.getOrNull(cell.getElementsByTagNameNS("*", "v").item(0)?.textContent?.toIntOrNull() ?: -1).orEmpty()
                    "b" -> if (cell.getElementsByTagNameNS("*", "v").item(0)?.textContent == "1") "true" else "false"
                    else -> cell.getElementsByTagNameNS("*", "v").item(0)?.textContent.orEmpty()
                }
                values[index] = value
                max = maxOf(max, index)
            }
            result += (0..max).map { values[it].orEmpty() }
        }
        return result
    }

    private fun sharedStrings(bytes: ByteArray): List<String> {
        val nodes = document(bytes).getElementsByTagNameNS("*", "si")
        return (0 until nodes.length).map { nodes.item(it).textContent.orEmpty() }
    }
    private fun readEntry(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "Excel Sheet가 너무 큽니다." }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
    private fun document(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    private fun entry(zip: ZipOutputStream, name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
    private fun workbook(names: Set<String>) = """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>${names.mapIndexed { i, n -> "<sheet name=\"${xml(n)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("")}</sheets></workbook>"""
    private fun workbookRels(count: Int) = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${(1..count).joinToString("") { "<Relationship Id=\"rId$it\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$it.xml\"/>" }}<Relationship Id="rId${count + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
    private fun contentTypes(count: Int) = """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>${(1..count).joinToString("") { "<Override PartName=\"/xl/worksheets/sheet$it.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" }}</Types>"""
    private fun columnName(index: Int): String { var n = index + 1; val out = StringBuilder(); while (n > 0) { n--; out.append(('A'.code + n % 26).toChar()); n /= 26 }; return out.reverse().toString() }
    private fun columnIndex(ref: String): Int { var value = 0; ref.takeWhile { it.isLetter() }.forEach { value = value * 26 + (it.uppercaseChar() - 'A' + 1) }; return value - 1 }
    private fun xml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
