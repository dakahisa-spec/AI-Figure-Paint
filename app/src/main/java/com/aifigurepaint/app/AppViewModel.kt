package com.aifigurepaint.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.aifigurepaint.app.ai.AiMixRequest
import com.aifigurepaint.app.ai.AiMixSuggestion
import com.aifigurepaint.app.ai.AiPaintDraft
import com.aifigurepaint.app.ai.AiProjectDraft
import com.aifigurepaint.app.ai.AiSettingsStore
import com.aifigurepaint.app.ai.LocalColorEngine
import com.aifigurepaint.app.ai.OpenAiService
import com.aifigurepaint.app.data.AppDatabase
import com.aifigurepaint.app.data.MixRecipeEntity
import com.aifigurepaint.app.data.MixRecipeItemEntity
import com.aifigurepaint.app.data.PaintEntity
import com.aifigurepaint.app.data.PhotoEntity
import com.aifigurepaint.app.data.PhotoOwner
import com.aifigurepaint.app.data.ProjectEntity
import com.aifigurepaint.app.data.ProjectPaintRow
import com.aifigurepaint.app.data.ProjectRecipeCrossRef
import com.aifigurepaint.app.data.ProjectStatus
import com.aifigurepaint.app.data.ProjectTimelineEntryEntity
import com.aifigurepaint.app.data.RecipeCardRow
import com.aifigurepaint.app.data.RecipeItemRow
import com.aifigurepaint.app.data.RecipeVersionEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

data class IngredientInput(val paintId: Long, val amountMl: Double)

data class AiUiState(
    val loading: Boolean = false,
    val suggestion: AiMixSuggestion? = null,
    val advice: String? = null,
    val notice: String? = null,
)

data class PaintScanUiState(
    val loading: Boolean = false,
    val draft: AiPaintDraft? = null,
    val notice: String? = null,
)

data class ProjectScanUiState(
    val loading: Boolean = false,
    val draft: AiProjectDraft? = null,
    val notice: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.get(application)
    private val paintsDao = db.paintDao()
    private val projectsDao = db.projectDao()
    private val recipesDao = db.recipeDao()
    private val photosDao = db.photoDao()
    private val versionsDao = db.versionDao()
    private val timelineDao = db.timelineDao()
    private val aiSettings = AiSettingsStore(application)
    private var aiJob: Job? = null

    val paints = paintsDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val projects = projectsDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recipes = recipesDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recipeCards = recipesDao.observeCards().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _aiState = MutableStateFlow(AiUiState())
    val aiState = _aiState.asStateFlow()
    private val _paintScanState = MutableStateFlow(PaintScanUiState())
    val paintScanState = _paintScanState.asStateFlow()
    private val _projectScanState = MutableStateFlow(ProjectScanUiState())
    val projectScanState = _projectScanState.asStateFlow()
    private val _aiConfigured = MutableStateFlow(aiSettings.hasApiKey())
    val aiConfigured = _aiConfigured.asStateFlow()
    private val _aiModel = MutableStateFlow(aiSettings.model())
    val aiModel = _aiModel.asStateFlow()

    fun clearMessage() { _message.value = null }
    fun clearAiResult() { _aiState.value = AiUiState() }
    fun clearPaintScan() {
        aiJob?.cancel()
        _paintScanState.value = PaintScanUiState()
    }
    fun clearProjectScan() {
        aiJob?.cancel()
        _projectScanState.value = ProjectScanUiState()
    }

    fun savePaint(paint: PaintEntity, onSaved: () -> Unit = {}) = viewModelScope.launch {
        runCatching {
            val now = System.currentTimeMillis()
            if (paint.id == 0L) paintsDao.insert(paint.copy(createdAt = now, updatedAt = now))
            else paintsDao.update(paint.copy(updatedAt = now))
        }.onSuccess {
            _message.value = "도료를 저장했습니다."
            onSaved()
        }.onFailure { _message.value = "저장하지 못했습니다: ${it.message.orEmpty()}" }
    }

    fun setPaintOwned(paint: PaintEntity, owned: Boolean) = viewModelScope.launch {
        runCatching {
            paintsDao.update(
                paint.copy(
                    owned = owned,
                    stockLevel = if (owned) maxOf(1, paint.stockLevel) else 0,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
            .onFailure { _message.value = "보유 상태를 변경하지 못했습니다." }
    }

    fun setPaintStock(paint: PaintEntity, stockLevel: Int) = viewModelScope.launch {
        runCatching {
            paintsDao.update(
                paint.copy(
                    stockLevel = stockLevel,
                    owned = stockLevel > 0,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }.onFailure { _message.value = "재고 상태를 변경하지 못했습니다." }
    }

    fun togglePaintFavorite(paint: PaintEntity) = viewModelScope.launch {
        runCatching {
            paintsDao.update(paint.copy(favorite = !paint.favorite, updatedAt = System.currentTimeMillis()))
        }.onFailure { _message.value = "즐겨찾기를 변경하지 못했습니다." }
    }

    fun deletePaint(paint: PaintEntity, onDeleted: () -> Unit = {}) = viewModelScope.launch {
        runCatching { paintsDao.delete(paint) }
            .onSuccess { _message.value = "도료를 삭제했습니다."; onDeleted() }
            .onFailure { _message.value = "레시피에서 사용 중인 도료는 먼저 레시피에서 제거해주세요." }
    }

    fun saveProject(
        project: ProjectEntity,
        photoUris: List<String> = listOfNotNull(project.photoUri),
        onSaved: (Long) -> Unit = {},
    ) = viewModelScope.launch {
        runCatching {
            val now = System.currentTimeMillis()
            var savedId = project.id
            db.withTransaction {
                if (savedId == 0L) {
                    savedId = projectsDao.insert(project.copy(photoUri = photoUris.firstOrNull(), createdAt = now, updatedAt = now))
                } else {
                    projectsDao.update(project.copy(photoUri = photoUris.firstOrNull(), updatedAt = now))
                }
                replacePhotos(PhotoOwner.PROJECT, savedId, photoUris)
            }
            savedId
        }.onSuccess {
            _message.value = "프로젝트를 저장했습니다."
            onSaved(it)
        }.onFailure { _message.value = "프로젝트를 저장하지 못했습니다." }
    }

    fun deleteProject(project: ProjectEntity, onDeleted: () -> Unit = {}) = viewModelScope.launch {
        runCatching {
            db.withTransaction {
                photosDao.deleteForOwner(PhotoOwner.PROJECT, project.id)
                projectsDao.delete(project)
            }
        }
            .onSuccess { _message.value = "프로젝트를 삭제했습니다."; onDeleted() }
            .onFailure { _message.value = "프로젝트를 삭제하지 못했습니다." }
    }

    fun recipeItems(recipeId: Long): Flow<List<RecipeItemRow>> = recipesDao.observeItems(recipeId)
    fun projectRecipes(projectId: Long): Flow<List<RecipeCardRow>> = recipesDao.observeCardsForProject(projectId)
    fun projectRecipeIds(projectId: Long): Flow<List<Long>> = recipesDao.observeRecipeIdsForProject(projectId)
    fun projectPaints(projectId: Long): Flow<List<ProjectPaintRow>> = recipesDao.observePaintsForProject(projectId)
    fun paintRecipes(paintId: Long): Flow<List<RecipeCardRow>> = recipesDao.observeCardsForPaint(paintId)
    fun photos(ownerType: String, ownerId: Long): Flow<List<PhotoEntity>> = photosDao.observe(ownerType, ownerId)
    fun recipeVersions(recipeId: Long): Flow<List<RecipeVersionEntity>> = versionsDao.observeForRecipe(recipeId)
    fun projectTimeline(projectId: Long): Flow<List<ProjectTimelineEntryEntity>> = timelineDao.observeForProject(projectId)

    fun setProjectRecipes(projectId: Long, recipeIds: List<Long>) = viewModelScope.launch {
        runCatching {
            db.withTransaction {
                recipesDao.clearLegacyProjectLinks(projectId)
                recipesDao.deleteProjectRefs(projectId)
                val refs = recipeIds.distinct().mapIndexed { index, recipeId ->
                    ProjectRecipeCrossRef(projectId = projectId, recipeId = recipeId, sortOrder = index)
                }
                if (refs.isNotEmpty()) recipesDao.insertProjectRefs(refs)
            }
        }.onSuccess { _message.value = "프로젝트 레시피를 연결했습니다." }
            .onFailure { _message.value = "레시피 연결을 저장하지 못했습니다." }
    }

    fun saveRecipe(
        recipe: MixRecipeEntity,
        ingredients: List<IngredientInput>,
        photoUris: List<String> = listOfNotNull(recipe.photoUri),
        onSaved: (Long) -> Unit = {},
    ) = viewModelScope.launch {
        runCatching {
            require(recipe.name.isNotBlank()) { "레시피 이름이 필요합니다." }
            val validItems = ingredients.filter { it.amountMl > 0.0 }
            require(validItems.isNotEmpty()) { "도료를 한 가지 이상 추가해주세요." }
            val now = System.currentTimeMillis()
            val total = validItems.sumOf { it.amountMl }
            var savedId = recipe.id
            db.withTransaction {
                if (savedId == 0L) {
                    savedId = recipesDao.insertRecipe(
                        recipe.copy(baseTotalMl = total, photoUri = photoUris.firstOrNull(), createdAt = now, updatedAt = now),
                    )
                } else {
                    recipesDao.updateRecipe(recipe.copy(baseTotalMl = total, photoUri = photoUris.firstOrNull(), updatedAt = now))
                    recipesDao.deleteItems(savedId)
                }
                recipesDao.insertItems(validItems.mapIndexed { index, item ->
                    MixRecipeItemEntity(
                        recipeId = savedId,
                        paintId = item.paintId,
                        baseAmountMl = item.amountMl,
                        sortOrder = index,
                    )
                })
                val versionNumber = versionsDao.nextVersionNumber(savedId)
                versionsDao.insert(
                    RecipeVersionEntity(
                        recipeId = savedId,
                        versionNumber = versionNumber,
                        label = if (versionNumber == 1) "Original" else "v$versionNumber",
                        snapshotName = recipe.name.trim(),
                        snapshotColorValue = recipe.resultColorValue,
                        snapshotTotalMl = total,
                        ingredientSnapshot = ingredientSnapshot(validItems),
                    ),
                )
                replacePhotos(PhotoOwner.RECIPE, savedId, photoUris)
                recipe.projectId?.let { projectId ->
                    recipesDao.insertProjectRefs(listOf(ProjectRecipeCrossRef(projectId, savedId)))
                }
                paintsDao.markUsed(validItems.map { it.paintId }.distinct(), now)
            }
            savedId
        }.onSuccess {
            _message.value = "조색 레시피를 저장했습니다."
            onSaved(it)
        }.onFailure { _message.value = it.message ?: "레시피를 저장하지 못했습니다." }
    }

    fun deleteRecipe(recipe: MixRecipeEntity, onDeleted: () -> Unit = {}) = viewModelScope.launch {
        runCatching {
            db.withTransaction {
                photosDao.deleteForOwner(PhotoOwner.RECIPE, recipe.id)
                recipesDao.deleteRecipeRefs(recipe.id)
                recipesDao.deleteRecipe(recipe)
            }
        }
            .onSuccess { _message.value = "레시피를 삭제했습니다."; onDeleted() }
            .onFailure { _message.value = "레시피를 삭제하지 못했습니다." }
    }

    fun toggleRecipeFavorite(recipe: MixRecipeEntity) = viewModelScope.launch {
        runCatching { recipesDao.updateRecipe(recipe.copy(favorite = !recipe.favorite, updatedAt = System.currentTimeMillis())) }
            .onFailure { _message.value = "레시피 즐겨찾기를 변경하지 못했습니다." }
    }

    private suspend fun replacePhotos(ownerType: String, ownerId: Long, uris: List<String>) {
        photosDao.deleteForOwner(ownerType, ownerId)
        val distinctUris = uris.filter { it.isNotBlank() }.distinct().take(3)
        if (distinctUris.isNotEmpty()) {
            val now = System.currentTimeMillis()
            photosDao.insertAll(distinctUris.mapIndexed { index, uri ->
                PhotoEntity(ownerType = ownerType, ownerId = ownerId, uri = uri, sortOrder = index, createdAt = now)
            })
        }
    }

    fun saveAiSettings(apiKey: String, model: String) {
        aiSettings.save(apiKey, model)
        _aiConfigured.value = aiSettings.hasApiKey()
        _aiModel.value = aiSettings.model()
        _message.value = "AI 연결 설정을 안전하게 저장했습니다."
    }

    fun clearAiSettings() {
        aiSettings.clear()
        _aiConfigured.value = false
        _message.value = "저장된 AI API 키를 삭제했습니다."
    }

    fun analyzePaintPhoto(uri: Uri) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _paintScanState.value = PaintScanUiState(loading = true)
            var fallbackDraft: AiPaintDraft? = null
            try {
                val prepared = preparePaintPhoto(uri)
                val localDraft = AiPaintDraft(
                    brand = "",
                    series = "",
                    productCode = null,
                    name = "",
                    koreanName = "",
                    colorHex = prepared.localHex,
                    confidence = 0.0,
                    notes = "로컬에서 추출한 대표색입니다. 제품 정보는 사진을 보며 직접 확인해주세요.",
                )
                fallbackDraft = localDraft
                val apiKey = aiSettings.readApiKey()
                if (apiKey.isBlank()) {
                    _paintScanState.value = PaintScanUiState(
                        draft = localDraft,
                        notice = "GPT-5.6 연결 설정이 없어 로컬 대표색만 추출했습니다.",
                    )
                } else {
                    val result = OpenAiService(apiKey, aiSettings.model()).analyzePaintPhoto(prepared.dataUrl)
                    _paintScanState.value = PaintScanUiState(draft = result)
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (error: Throwable) {
                _paintScanState.value = PaintScanUiState(
                    draft = fallbackDraft,
                    notice = if (fallbackDraft != null) {
                        "GPT-5.6 연결을 사용할 수 없어 로컬 대표색으로 전환했습니다: ${error.message.orEmpty().take(90)}"
                    } else {
                        "사진을 분석하지 못했습니다: ${error.message.orEmpty().take(120)}"
                    },
                )
            }
        }
    }

    fun analyzeProjectPhoto(uri: Uri) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _projectScanState.value = ProjectScanUiState(loading = true)
            val captureDate = LocalDate.now().toString()
            val manualDraft = AiProjectDraft(
                projectName = "",
                modelName = "",
                startDate = captureDate,
                status = ProjectStatus.PLANNED,
                memo = "",
                confidence = 0.0,
                notes = "사진을 참고해 프로젝트 정보를 직접 확인해주세요. 시작일은 촬영일을 초안으로 넣었습니다.",
            )
            try {
                val prepared = preparePaintPhoto(uri)
                val apiKey = aiSettings.readApiKey()
                if (apiKey.isBlank()) {
                    _projectScanState.value = ProjectScanUiState(
                        draft = manualDraft,
                        notice = "GPT-5.6 연결 설정이 없어 촬영일만 입력했습니다. 나머지 정보는 직접 확인해주세요.",
                    )
                } else {
                    val result = OpenAiService(apiKey, aiSettings.model())
                        .analyzeProjectPhoto(prepared.dataUrl, captureDate)
                    _projectScanState.value = ProjectScanUiState(draft = result)
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (error: Throwable) {
                _projectScanState.value = ProjectScanUiState(
                    draft = manualDraft,
                    notice = "GPT-5.6 분석을 사용할 수 없어 직접 입력 모드로 전환했습니다: ${error.message.orEmpty().take(90)}",
                )
            }
        }
    }

    fun requestMix(
        prompt: String,
        ownedOnly: Boolean,
        brand: String?,
        currentRecipe: String? = null,
        targetHex: String? = null,
    ) {
        if (prompt.isBlank() && targetHex.isNullOrBlank()) {
            _aiState.value = AiUiState(notice = "원하는 색을 설명하거나 사진 색상을 선택해주세요.")
            return
        }
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _aiState.value = AiUiState(loading = true)
            val request = AiMixRequest(
                prompt = prompt.ifBlank { "사진의 $targetHex 색상" },
                paints = paints.value,
                ownedOnly = ownedOnly,
                brand = brand,
                currentRecipe = currentRecipe,
                targetHex = targetHex,
            )
            try {
                val apiKey = aiSettings.readApiKey()
                if (apiKey.isBlank()) {
                    val local = withContext(Dispatchers.Default) { LocalColorEngine.suggest(request) }
                    _aiState.value = AiUiState(
                        suggestion = local,
                        notice = "AI 연결을 사용할 수 없습니다. 로컬 색상 분석 결과를 표시합니다.",
                    )
                } else {
                    val result = OpenAiService(apiKey, aiSettings.model()).suggestMix(request)
                    _aiState.value = AiUiState(suggestion = result)
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (error: Throwable) {
                val local = runCatching { withContext(Dispatchers.Default) { LocalColorEngine.suggest(request) } }.getOrNull()
                _aiState.value = AiUiState(
                    suggestion = local,
                    notice = if (local != null) "AI 연결을 사용할 수 없습니다. 로컬 결과로 전환했습니다. (${error.message.orEmpty().take(80)})"
                    else "AI 추천을 만들지 못했습니다: ${error.message.orEmpty().take(100)}",
                )
            }
        }
    }

    fun requestProjectAdvice(question: String, context: String) {
        if (question.isBlank()) return
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _aiState.value = AiUiState(loading = true)
            val apiKey = aiSettings.readApiKey()
            if (apiKey.isBlank()) {
                _aiState.value = AiUiState(notice = "AI 연결을 사용할 수 없습니다. 설정에서 API 키를 등록해주세요.")
                return@launch
            }
            try {
                val advice = OpenAiService(apiKey, aiSettings.model()).advise(question, context)
                _aiState.value = AiUiState(advice = advice)
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (error: Throwable) {
                _aiState.value = AiUiState(notice = "AI 연결을 사용할 수 없습니다: ${error.message.orEmpty().take(100)}")
            }
        }
    }

    fun cancelAiRequest() {
        aiJob?.cancel()
        _aiState.value = AiUiState()
        _paintScanState.value = PaintScanUiState()
        _projectScanState.value = ProjectScanUiState()
    }

    private data class PreparedPaintPhoto(val dataUrl: String, val localHex: String)

    private suspend fun preparePaintPhoto(uri: Uri): PreparedPaintPhoto = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val original = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: error("사진 파일을 읽을 수 없습니다.")
        val longest = max(original.width, original.height)
        val scale = if (longest > 1280) 1280f / longest else 1f
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                original,
                (original.width * scale).roundToInt().coerceAtLeast(1),
                (original.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            original
        }
        try {
            val output = ByteArrayOutputStream()
            check(resized.compress(Bitmap.CompressFormat.JPEG, 84, output)) { "사진을 변환하지 못했습니다." }
            val encoded = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            PreparedPaintPhoto(
                dataUrl = "data:image/jpeg;base64,$encoded",
                localHex = representativeHex(resized),
            )
        } finally {
            if (resized !== original) resized.recycle()
            original.recycle()
        }
    }

    private fun representativeHex(bitmap: Bitmap): String {
        val sample = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        try {
            for (y in 3 until 21) {
                for (x in 3 until 21) {
                    val pixel = sample.getPixel(x, y)
                    red += Color.red(pixel)
                    green += Color.green(pixel)
                    blue += Color.blue(pixel)
                    count++
                }
            }
        } finally {
            sample.recycle()
        }
        if (count == 0L) return "#808080"
        return "#%02X%02X%02X".format((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
    }

    fun saveAiSuggestion(
        suggestion: AiMixSuggestion,
        projectId: Long? = null,
        recipeId: Long? = null,
        onSaved: (Long) -> Unit = {},
    ) = viewModelScope.launch {
        runCatching {
            val total = 10.0
            val now = System.currentTimeMillis()
            var savedRecipeId = recipeId ?: 0L
            val ingredients = suggestion.components.map { IngredientInput(it.paintId, it.percent / 100.0 * total) }
            db.withTransaction {
                if (savedRecipeId == 0L) {
                    savedRecipeId = recipesDao.insertRecipe(
                        MixRecipeEntity(
                            projectId = projectId,
                            name = suggestion.name,
                            baseTotalMl = total,
                            memo = "${suggestion.explanation}\n예상 조색 · ${suggestion.source}",
                            resultColorValue = parseHex(suggestion.targetHex),
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                    recipesDao.insertItems(ingredients.mapIndexed { index, item ->
                        MixRecipeItemEntity(recipeId = savedRecipeId, paintId = item.paintId, baseAmountMl = item.amountMl, sortOrder = index)
                    })
                    projectId?.let { recipesDao.insertProjectRefs(listOf(ProjectRecipeCrossRef(it, savedRecipeId))) }
                }
                val next = versionsDao.nextVersionNumber(savedRecipeId)
                versionsDao.insert(
                    RecipeVersionEntity(
                        recipeId = savedRecipeId,
                        versionNumber = next,
                        label = if (next == 1) "AI Original" else "AI Adjusted v$next",
                        snapshotName = suggestion.name,
                        snapshotColorValue = parseHex(suggestion.targetHex),
                        snapshotTotalMl = total,
                        ingredientSnapshot = ingredientSnapshot(ingredients),
                        aiGenerated = true,
                        sourcePrompt = suggestion.originalPrompt,
                        createdAt = now,
                    ),
                )
                paintsDao.markUsed(ingredients.map { it.paintId }.distinct(), now)
            }
            savedRecipeId
        }.onSuccess {
            _message.value = if (recipeId == null) "AI 제안을 새 레시피로 저장했습니다." else "AI 제안을 새 버전으로 저장했습니다."
            onSaved(it)
        }.onFailure { _message.value = "AI 제안을 저장하지 못했습니다: ${it.message.orEmpty()}" }
    }

    fun addTimelineEntry(entry: ProjectTimelineEntryEntity) = viewModelScope.launch {
        runCatching { timelineDao.insert(entry.copy(createdAt = System.currentTimeMillis())) }
            .onSuccess { _message.value = "작업 기록을 추가했습니다." }
            .onFailure { _message.value = "작업 기록을 저장하지 못했습니다." }
    }

    fun deleteTimelineEntry(entry: ProjectTimelineEntryEntity) = viewModelScope.launch {
        runCatching { timelineDao.delete(entry) }
            .onFailure { _message.value = "작업 기록을 삭제하지 못했습니다." }
    }

    private fun ingredientSnapshot(items: List<IngredientInput>): String = JSONArray().apply {
        items.forEach { put(JSONObject().put("paintId", it.paintId).put("amountMl", it.amountMl)) }
    }.toString()

    private fun parseHex(value: String): Int = runCatching {
        (0xFF000000L or value.removePrefix("#").takeLast(6).toLong(16)).toInt()
    }.getOrDefault(0xFF808080.toInt())
}
