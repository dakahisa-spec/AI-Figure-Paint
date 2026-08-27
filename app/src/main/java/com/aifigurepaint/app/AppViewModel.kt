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
import com.aifigurepaint.app.ai.AiModelMode
import com.aifigurepaint.app.ai.AiTaskType
import com.aifigurepaint.app.ai.AiPaintDraft
import com.aifigurepaint.app.ai.AiOfficialReference
import com.aifigurepaint.app.ai.AiOriginalColorPlanDraft
import com.aifigurepaint.app.ai.AiOriginalColorPart
import com.aifigurepaint.app.ai.AiPartsComparisonDraft
import com.aifigurepaint.app.ai.AiProjectDraft
import com.aifigurepaint.app.ai.AiProductCodeResult
import com.aifigurepaint.app.ai.AiSubjectCandidate
import com.aifigurepaint.app.ai.AiSettingsStore
import com.aifigurepaint.app.ai.LocalColorEngine
import com.aifigurepaint.app.ai.OpenAiService
import com.aifigurepaint.app.data.AppDatabase
import com.aifigurepaint.app.data.DuplicatePolicy
import com.aifigurepaint.app.data.ExcelBackupService
import com.aifigurepaint.app.data.ExcelImportPreview
import com.aifigurepaint.app.data.MixRecipeEntity
import com.aifigurepaint.app.data.MixRecipeItemEntity
import com.aifigurepaint.app.data.PaintEntity
import com.aifigurepaint.app.data.PartComparisonEntity
import com.aifigurepaint.app.data.OriginalColorPlanEntity
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
import com.aifigurepaint.app.data.TestEvaluation
import com.aifigurepaint.app.data.TestResultEntity
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
    val activeModelLabel: String? = null,
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

data class TestAdjustmentUiState(
    val loading: Boolean = false,
    val testResultId: Long? = null,
    val suggestion: AiMixSuggestion? = null,
    val notice: String? = null,
)

data class ExcelUiState(
    val loading: Boolean = false,
    val preview: ExcelImportPreview? = null,
    val notice: String? = null,
)

data class PartsComparisonUiState(
    val loading: Boolean = false,
    val draft: AiPartsComparisonDraft? = null,
    val notice: String? = null,
    val activeModelLabel: String? = null,
)

data class ProductCodeSearchUiState(
    val loading: Boolean = false,
    val results: List<AiProductCodeResult> = emptyList(),
    val notice: String? = null,
    val activeModelLabel: String? = null,
)

data class OriginalColorMatchUiState(
    val loading: Boolean = false,
    val stage: String = "PHOTO",
    val candidates: List<AiSubjectCandidate> = emptyList(),
    val references: List<AiOfficialReference> = emptyList(),
    val plan: AiOriginalColorPlanDraft? = null,
    val photoWarning: String? = null,
    val notice: String? = null,
    val activeModelLabel: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.get(application)
    private val paintsDao = db.paintDao()
    private val projectsDao = db.projectDao()
    private val recipesDao = db.recipeDao()
    private val photosDao = db.photoDao()
    private val versionsDao = db.versionDao()
    private val timelineDao = db.timelineDao()
    private val partComparisonsDao = db.partComparisonDao()
    private val originalColorPlansDao = db.originalColorPlanDao()
    private val testResultsDao = db.testResultDao()
    private val excel = ExcelBackupService(application, db)
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
    private val _aiModelMode = MutableStateFlow(aiSettings.mode())
    val aiModelMode = _aiModelMode.asStateFlow()
    private val _testAdjustmentState = MutableStateFlow(TestAdjustmentUiState())
    val testAdjustmentState = _testAdjustmentState.asStateFlow()
    private val _excelState = MutableStateFlow(ExcelUiState())
    val excelState = _excelState.asStateFlow()
    private val _partsComparisonState = MutableStateFlow(PartsComparisonUiState())
    val partsComparisonState = _partsComparisonState.asStateFlow()
    private val _productCodeSearchState = MutableStateFlow(ProductCodeSearchUiState())
    val productCodeSearchState = _productCodeSearchState.asStateFlow()
    private val _originalColorMatchState = MutableStateFlow(OriginalColorMatchUiState())
    val originalColorMatchState = _originalColorMatchState.asStateFlow()

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

    fun searchProductCodes(paints: List<PaintEntity>) {
        val targets = paints.distinctBy { it.id }.take(5)
        if (targets.isEmpty()) return
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            val selection = aiSettings.selection(AiTaskType.PRODUCT_CODE_SEARCH)
            _productCodeSearchState.value = ProductCodeSearchUiState(loading = true, activeModelLabel = selection.resultLabel)
            val apiKey = aiSettings.readApiKey()
            if (apiKey.isBlank()) {
                _productCodeSearchState.value = ProductCodeSearchUiState(
                    notice = "AI 연결을 사용할 수 없습니다. 설정에서 API 키를 등록해주세요.",
                    activeModelLabel = selection.resultLabel,
                )
                return@launch
            }
            try {
                val results = OpenAiService(apiKey, selection).searchProductCodes(targets)
                _productCodeSearchState.value = ProductCodeSearchUiState(results = results, activeModelLabel = selection.resultLabel)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _productCodeSearchState.value = ProductCodeSearchUiState(
                    notice = "상품번호 검색을 완료하지 못했습니다: ${error.message.orEmpty().take(120)}",
                    activeModelLabel = selection.resultLabel,
                )
            }
        }
    }

    fun applyProductCode(paint: PaintEntity, code: String, onApplied: () -> Unit = {}) = viewModelScope.launch {
        val normalized = code.trim()
        runCatching {
            require(normalized.isNotBlank()) { "적용할 상품번호가 없습니다." }
            val duplicate = paintsDao.allOnce().firstOrNull {
                it.id != paint.id && it.brand.equals(paint.brand, ignoreCase = true) &&
                    (it.productCode?.trim()?.equals(normalized, ignoreCase = true) == true)
            }
            require(duplicate == null) { "${paint.brand} $normalized ${duplicate?.name.orEmpty()}가 이미 등록되어 있습니다." }
            paintsDao.update(paint.copy(productCode = normalized, updatedAt = System.currentTimeMillis()))
        }.onSuccess {
            _message.value = "상품번호 $normalized 을(를) 적용했습니다."
            onApplied()
        }.onFailure { _message.value = it.message ?: "상품번호를 적용하지 못했습니다." }
    }

    fun applyProductCodes(selections: Map<Long, String>, onApplied: () -> Unit = {}) = viewModelScope.launch {
        runCatching {
            var applied = 0
            var conflicts = 0
            db.withTransaction {
                val all = paintsDao.allOnce().toMutableList()
                selections.entries.take(5).forEach { (paintId, rawCode) ->
                    val paint = all.firstOrNull { it.id == paintId } ?: return@forEach
                    val code = rawCode.trim()
                    if (code.isBlank()) return@forEach
                    val duplicate = all.any {
                        it.id != paint.id && it.brand.equals(paint.brand, true) &&
                            (it.productCode?.trim()?.equals(code, true) == true)
                    }
                    if (duplicate) {
                        conflicts++
                    } else {
                        val updated = paint.copy(productCode = code, updatedAt = System.currentTimeMillis())
                        paintsDao.update(updated)
                        all[all.indexOf(paint)] = updated
                        applied++
                    }
                }
            }
            applied to conflicts
        }.onSuccess { (applied, conflicts) ->
            _message.value = if (conflicts == 0) "상품번호 ${applied}개를 적용했습니다." else "${applied}개 적용 · 중복 ${conflicts}개 건너뜀"
            onApplied()
        }.onFailure { _message.value = "상품번호를 적용하지 못했습니다: ${it.message.orEmpty()}" }
    }

    fun clearProductCodeSearch() {
        aiJob?.cancel()
        _productCodeSearchState.value = ProductCodeSearchUiState()
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
    fun testResults(recipeId: Long): Flow<List<TestResultEntity>> = testResultsDao.observeForRecipe(recipeId)
    fun projectTimeline(projectId: Long): Flow<List<ProjectTimelineEntryEntity>> = timelineDao.observeForProject(projectId)
    fun partComparisons(projectId: Long): Flow<List<PartComparisonEntity>> = partComparisonsDao.observeForProject(projectId)
    fun originalColorPlans(projectId: Long): Flow<List<OriginalColorPlanEntity>> = originalColorPlansDao.observeForProject(projectId)

    fun recognizeOriginalSubject(project: ProjectEntity, imageUris: List<String>) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            val selection = aiSettings.selection(AiTaskType.ORIGINAL_COLOR_MATCH)
            _originalColorMatchState.value = OriginalColorMatchUiState(loading = true, stage = "RECOGNIZE", activeModelLabel = selection.resultLabel)
            val apiKey = aiSettings.readApiKey()
            if (apiKey.isBlank()) {
                _originalColorMatchState.value = OriginalColorMatchUiState(stage = "PHOTO", notice = "AI 연결을 사용할 수 없습니다. 설정에서 API 키를 등록해주세요.", activeModelLabel = selection.resultLabel)
                return@launch
            }
            try {
                val (photos, failedCount) = prepareOriginalColorPhotos(imageUris)
                val result = OpenAiService(apiKey, selection).recognizeOriginalSubject(
                    photos,
                    project.projectType,
                    "이름=${project.name}; 모델=${project.modelName}; 메모=${project.memo.take(200)}",
                )
                _originalColorMatchState.value = OriginalColorMatchUiState(
                    stage = "CANDIDATE",
                    candidates = result.candidates,
                    photoWarning = result.photoWarning.takeIf { it.isNotBlank() },
                    notice = when {
                        result.candidates.isEmpty() -> "대상을 인식하지 못했습니다. 이름을 직접 입력해주세요."
                        failedCount > 0 -> "사진 ${imageUris.size}장 중 ${failedCount}장을 불러오지 못했지만 나머지 사진으로 분석했습니다."
                        else -> null
                    },
                    activeModelLabel = selection.resultLabel,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _originalColorMatchState.value = OriginalColorMatchUiState(stage = "PHOTO", notice = "대상 인식을 완료하지 못했습니다: ${error.message.orEmpty().take(120)}", activeModelLabel = selection.resultLabel)
            }
        }
    }

    fun searchOriginalReferences(candidate: AiSubjectCandidate) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            val selection = aiSettings.selection(AiTaskType.ORIGINAL_COLOR_MATCH)
            _originalColorMatchState.value = _originalColorMatchState.value.copy(loading = true, stage = "REFERENCE", notice = null, activeModelLabel = selection.resultLabel)
            val apiKey = aiSettings.readApiKey()
            if (apiKey.isBlank()) {
                _originalColorMatchState.value = _originalColorMatchState.value.copy(loading = false, notice = "AI 연결을 사용할 수 없습니다. 설정에서 API 키를 등록해주세요.")
                return@launch
            }
            try {
                val references = OpenAiService(apiKey, selection).searchOriginalReferences(candidate.name, candidate.workTitle, candidate.versionName)
                _originalColorMatchState.value = _originalColorMatchState.value.copy(
                    loading = false,
                    stage = "REFERENCE",
                    references = references,
                    notice = if (references.isEmpty()) "공식 참고자료를 확인하지 못했습니다. 사진 자체를 기준으로 분석할 수 있습니다." else null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _originalColorMatchState.value = _originalColorMatchState.value.copy(loading = false, stage = "REFERENCE", notice = "공식 자료를 검색할 수 없습니다: ${error.message.orEmpty().take(120)}")
            }
        }
    }

    fun analyzeOriginalColors(
        project: ProjectEntity,
        imageUris: List<String>,
        candidate: AiSubjectCandidate,
        reference: AiOfficialReference,
        ownedOnly: Boolean,
    ) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            val selection = aiSettings.selection(AiTaskType.ORIGINAL_COLOR_MATCH)
            _originalColorMatchState.value = _originalColorMatchState.value.copy(loading = true, stage = "PLAN", notice = null, activeModelLabel = selection.resultLabel)
            val apiKey = aiSettings.readApiKey()
            if (apiKey.isBlank()) {
                _originalColorMatchState.value = _originalColorMatchState.value.copy(loading = false, notice = "AI 연결을 사용할 수 없습니다. 설정에서 API 키를 등록해주세요.")
                return@launch
            }
            try {
                val (photos, failedCount) = prepareOriginalColorPhotos(imageUris)
                val plan = OpenAiService(apiKey, selection).analyzeOriginalColors(
                    photos,
                    project.projectType,
                    candidate,
                    reference,
                    paints.value,
                    ownedOnly,
                )
                _originalColorMatchState.value = _originalColorMatchState.value.copy(
                    loading = false,
                    stage = "PLAN",
                    plan = plan,
                    notice = if (failedCount > 0) "사진 ${imageUris.size}장 중 ${failedCount}장을 불러오지 못했지만 나머지 사진으로 분석했습니다." else null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _originalColorMatchState.value = _originalColorMatchState.value.copy(loading = false, stage = "REFERENCE", notice = "원작 색상 분석을 완료하지 못했습니다: ${error.message.orEmpty().take(120)}")
            }
        }
    }

    fun analyzePhotoColors(
        project: ProjectEntity,
        imageUris: List<String>,
        candidate: AiSubjectCandidate,
        ownedOnly: Boolean,
    ) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            val selection = aiSettings.selection(AiTaskType.ORIGINAL_COLOR_MATCH)
            _originalColorMatchState.value = _originalColorMatchState.value.copy(
                loading = true,
                stage = "PHOTO_PLAN",
                notice = null,
                activeModelLabel = selection.resultLabel,
            )
            val apiKey = aiSettings.readApiKey()
            if (apiKey.isBlank()) {
                _originalColorMatchState.value = _originalColorMatchState.value.copy(
                    loading = false,
                    notice = "AI 연결을 사용할 수 없습니다. 기존 설정에서 API 키를 확인해주세요.",
                )
                return@launch
            }
            try {
                val (photos, failedCount) = prepareOriginalColorPhotos(imageUris)
                val plan = OpenAiService(apiKey, selection).analyzePhotoColors(
                    imageDataUrls = photos,
                    projectType = project.projectType,
                    subject = candidate,
                    paints = paints.value,
                    ownedOnly = ownedOnly,
                )
                _originalColorMatchState.value = _originalColorMatchState.value.copy(
                    loading = false,
                    stage = "PHOTO_PLAN",
                    plan = plan,
                    notice = if (failedCount > 0) {
                        "사진 ${imageUris.size}장 중 ${failedCount}장을 불러오지 못했지만 나머지 사진으로 분석했습니다."
                    } else null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _originalColorMatchState.value = _originalColorMatchState.value.copy(
                    loading = false,
                    stage = "REFERENCE",
                    notice = "사진 기준 도료 분석을 완료하지 못했습니다: ${error.message.orEmpty().take(120)}",
                )
            }
        }
    }

    fun saveOriginalColorPlan(projectId: Long, plan: AiOriginalColorPlanDraft, onSaved: () -> Unit = {}) = viewModelScope.launch {
        runCatching {
            originalColorPlansDao.insert(
                OriginalColorPlanEntity(
                    projectId = projectId,
                    identifiedName = plan.subjectName,
                    workTitle = plan.workTitle,
                    versionName = plan.versionName,
                    referenceTitle = plan.reference.title,
                    referenceType = plan.reference.referenceType,
                    referenceUrl = plan.reference.url,
                    official = plan.reference.official,
                    partsJson = originalColorPartsJson(plan.parts),
                    modelLabel = _originalColorMatchState.value.activeModelLabel.orEmpty(),
                ),
            )
        }.onSuccess {
            _message.value = if (plan.reference.referenceType == "USER_PHOTO_ONLY") {
                "사진 기준 컬러 플랜을 저장했습니다."
            } else {
                "원작 컬러 플랜을 저장했습니다."
            }
            onSaved()
        }.onFailure { _message.value = "컬러 플랜을 저장하지 못했습니다: ${it.message.orEmpty()}" }
    }

    fun deleteOriginalColorPlan(plan: OriginalColorPlanEntity) = viewModelScope.launch {
        runCatching { originalColorPlansDao.delete(plan) }
            .onFailure { _message.value = "컬러 플랜을 삭제하지 못했습니다." }
    }

    fun clearOriginalColorMatch() {
        aiJob?.cancel()
        _originalColorMatchState.value = OriginalColorMatchUiState()
    }

    fun savePartsBaseline(project: ProjectEntity, uri: String) = viewModelScope.launch {
        runCatching {
            projectsDao.update(project.copy(partsBaselinePhotoUri = uri, updatedAt = System.currentTimeMillis()))
        }.onSuccess { _message.value = "부품 비교 기준 사진을 저장했습니다." }
            .onFailure { _message.value = "기준 사진을 저장하지 못했습니다." }
    }

    fun compareProjectParts(baselineUri: String, currentUri: String) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            val selection = aiSettings.selection(AiTaskType.PARTS_COMPARE)
            _partsComparisonState.value = PartsComparisonUiState(loading = true, activeModelLabel = selection.resultLabel)
            val apiKey = aiSettings.readApiKey()
            if (apiKey.isBlank()) {
                _partsComparisonState.value = PartsComparisonUiState(
                    notice = "AI 연결을 사용할 수 없습니다. 설정에서 API 키를 등록해주세요.",
                    activeModelLabel = selection.resultLabel,
                )
                return@launch
            }
            try {
                val baseline = preparePaintPhoto(Uri.parse(baselineUri))
                val current = preparePaintPhoto(Uri.parse(currentUri))
                val draft = OpenAiService(apiKey, selection).compareParts(baseline.dataUrl, current.dataUrl)
                _partsComparisonState.value = PartsComparisonUiState(draft = draft, activeModelLabel = selection.resultLabel)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _partsComparisonState.value = PartsComparisonUiState(
                    notice = "부품 비교를 완료하지 못했습니다: ${error.message.orEmpty().take(100)}",
                    activeModelLabel = selection.resultLabel,
                )
            }
        }
    }

    fun savePartComparison(
        projectId: Long,
        baselineUri: String,
        currentUri: String,
        draft: AiPartsComparisonDraft,
        modelLabel: String,
        onSaved: () -> Unit = {},
    ) = viewModelScope.launch {
        runCatching {
            partComparisonsDao.insert(
                PartComparisonEntity(
                    projectId = projectId,
                    comparisonDate = System.currentTimeMillis(),
                    baselinePhotoUri = baselineUri,
                    currentPhotoUri = currentUri,
                    changedCount = draft.changedCount,
                    missingCount = draft.missingCount,
                    movedCount = draft.movedCount,
                    summary = draft.summary,
                    findings = draft.findings.joinToString("\n"),
                    modelLabel = modelLabel,
                ),
            )
        }.onSuccess {
            _message.value = "부품 비교 기록을 저장했습니다."
            onSaved()
        }.onFailure { _message.value = "부품 비교 기록을 저장하지 못했습니다." }
    }

    fun deletePartComparison(comparison: PartComparisonEntity) = viewModelScope.launch {
        runCatching { partComparisonsDao.delete(comparison) }
            .onFailure { _message.value = "부품 비교 기록을 삭제하지 못했습니다." }
    }

    fun clearPartsComparison() {
        aiJob?.cancel()
        _partsComparisonState.value = PartsComparisonUiState()
    }

    fun saveTestResult(
        result: TestResultEntity,
        photoUris: List<String>,
        onSaved: () -> Unit = {},
    ) = viewModelScope.launch {
        runCatching {
            require(result.recipeVersionId > 0) { "테스트에 사용한 레시피 버전을 선택해주세요." }
            var savedId = 0L
            db.withTransaction {
                savedId = testResultsDao.insert(result.copy(createdAt = System.currentTimeMillis()))
                replacePhotos(PhotoOwner.TEST_RESULT, savedId, photoUris)
            }
        }.onSuccess {
            _message.value = "테스트 결과를 기록했습니다."
            onSaved()
        }.onFailure { _message.value = it.message ?: "테스트 결과를 저장하지 못했습니다." }
    }

    fun deleteTestResult(result: TestResultEntity) = viewModelScope.launch {
        runCatching {
            db.withTransaction {
                photosDao.deleteForOwner(PhotoOwner.TEST_RESULT, result.id)
                testResultsDao.delete(result)
            }
        }.onSuccess { _message.value = "테스트 기록을 삭제했습니다." }
            .onFailure { _message.value = "테스트 기록을 삭제하지 못했습니다." }
    }

    fun requestTestAdjustment(
        recipe: MixRecipeEntity,
        version: RecipeVersionEntity,
        result: TestResultEntity,
    ) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _testAdjustmentState.value = TestAdjustmentUiState(loading = true, testResultId = result.id)
            val recent = testResultsDao.recentForRecipe(recipe.id, 5)
            val feedback = result.evaluations.split('|').filter { it.isNotBlank() }.joinToString(", ") { TestEvaluation.label(it) }
            val history = recent.joinToString("\n") { row ->
                val labels = row.evaluations.split('|').filter { it.isNotBlank() }.joinToString(", ") { TestEvaluation.label(it) }
                "- $labels ${row.memo}".trim()
            }
            val versionItems = runCatching { JSONArray(version.ingredientSnapshot) }.getOrElse { JSONArray() }
            val paintMap = paints.value.associateBy { it.id }
            val current = buildString {
                append("v${version.versionNumber} ${version.label}; 총량 ${version.snapshotTotalMl}ml; ")
                for (index in 0 until versionItems.length()) {
                    val item = versionItems.optJSONObject(index) ?: continue
                    val paintId = item.optLong("paintId")
                    val amount = item.optDouble("amountMl")
                    val percent = if (version.snapshotTotalMl > 0) amount / version.snapshotTotalMl * 100 else 0.0
                    append("${paintMap[paintId]?.name ?: paintId} ${"%.2f".format(percent)}%, ")
                }
            }
            val request = AiMixRequest(
                prompt = "테스트 피스 평가($feedback)와 메모(${result.memo.ifBlank { "없음" }})를 반영해 비율을 보정해줘. 최근 관련 기록:\n$history",
                paints = paints.value,
                ownedOnly = true,
                currentRecipe = current,
                targetHex = "#%06X".format(recipe.resultColorValue and 0xFFFFFF),
            )
            try {
                val key = aiSettings.readApiKey()
                val suggestion = if (key.isBlank()) withContext(Dispatchers.Default) { LocalColorEngine.suggest(request) }
                else OpenAiService(key, aiSettings.selection(AiTaskType.TEST_PIECE_ADJUST)).suggestMix(request)
                _testAdjustmentState.value = TestAdjustmentUiState(
                    testResultId = result.id,
                    suggestion = suggestion,
                    notice = if (key.isBlank()) "AI 연결을 사용할 수 없어 로컬 후보를 표시합니다." else null,
                )
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (error: Throwable) {
                _testAdjustmentState.value = TestAdjustmentUiState(testResultId = result.id, notice = "AI 보정안을 만들지 못했습니다: ${error.message.orEmpty().take(100)}")
            }
        }
    }

    fun clearTestAdjustment() { aiJob?.cancel(); _testAdjustmentState.value = TestAdjustmentUiState() }

    fun exportExcel(uri: Uri) = viewModelScope.launch {
        _excelState.value = ExcelUiState(loading = true)
        runCatching { withContext(Dispatchers.IO) { excel.exportTo(uri) } }
            .onSuccess { summary -> _excelState.value = ExcelUiState(notice = "Excel 내보내기 완료 · 도료 ${summary.paints}, 레시피 ${summary.recipes}, 프로젝트 ${summary.projects}, 테스트 ${summary.testResults}") }
            .onFailure { _excelState.value = ExcelUiState(notice = "Excel 내보내기 실패: ${it.message.orEmpty().take(120)}") }
    }

    fun previewExcel(uri: Uri) = viewModelScope.launch {
        _excelState.value = ExcelUiState(loading = true)
        runCatching { withContext(Dispatchers.IO) { excel.preview(uri) } }
            .onSuccess { _excelState.value = ExcelUiState(preview = it) }
            .onFailure { _excelState.value = ExcelUiState(notice = "Excel 파일을 검증하지 못했습니다: ${it.message.orEmpty().take(120)}") }
    }

    fun importExcel(policy: DuplicatePolicy, onImported: () -> Unit = {}) = viewModelScope.launch {
        val preview = _excelState.value.preview ?: return@launch
        _excelState.value = _excelState.value.copy(loading = true)
        runCatching { withContext(Dispatchers.IO) { excel.importData(preview, policy) } }
            .onSuccess { summary ->
                _excelState.value = ExcelUiState(notice = "가져오기 완료 · 도료 ${summary.paints}, 레시피 ${summary.recipes}, 프로젝트 ${summary.projects}, 테스트 ${summary.testResults}")
                onImported()
            }
            .onFailure { error -> _excelState.value = ExcelUiState(preview = preview, notice = "가져오기 실패: ${error.message.orEmpty().take(120)}") }
    }

    fun clearExcelState() { _excelState.value = ExcelUiState() }

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

    fun saveAiSettings(apiKey: String, mode: AiModelMode) {
        aiSettings.save(apiKey, mode)
        _aiConfigured.value = aiSettings.hasApiKey()
        _aiModelMode.value = aiSettings.mode()
        _message.value = "AI 연결 설정을 안전하게 저장했습니다."
    }

    fun saveAiModelMode(mode: AiModelMode) {
        aiSettings.saveMode(mode)
        _aiModelMode.value = aiSettings.mode()
        _originalColorMatchState.value = _originalColorMatchState.value.copy(
            activeModelLabel = aiSettings.selection(AiTaskType.ORIGINAL_COLOR_MATCH).resultLabel,
        )
        _message.value = "AI 모델을 ${mode.title}으로 변경했습니다."
    }

    fun aiModelLabel(taskType: AiTaskType, highestQuality: Boolean = false): String =
        aiSettings.selection(taskType, highestQuality).resultLabel

    fun clearAiSettings() {
        aiSettings.clear()
        _aiConfigured.value = false
        _message.value = "저장된 AI API 키를 삭제했습니다."
    }

    fun analyzePaintPhotos(uris: List<Uri>) {
        require(uris.size in 1..3) { "도료 사진은 1장 이상 3장 이하로 선택해주세요." }
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _paintScanState.value = PaintScanUiState(loading = true)
            var fallbackDraft: AiPaintDraft? = null
            try {
                val prepared = uris.map { preparePaintPhoto(it) }
                val localDraft = AiPaintDraft(
                    brand = "",
                    series = "",
                    productCode = null,
                    name = "",
                    koreanName = "",
                    colorHex = averageHex(prepared.map { it.localHex }),
                    confidence = 0.0,
                    notes = "선택한 ${prepared.size}장 사진에서 로컬로 추출한 대표색입니다. 제품 정보는 사진을 보며 직접 확인해주세요.",
                )
                fallbackDraft = localDraft
                val apiKey = aiSettings.readApiKey()
                if (apiKey.isBlank()) {
                    _paintScanState.value = PaintScanUiState(
                        draft = localDraft,
                        notice = "GPT-5.6 연결 설정이 없어 로컬 대표색만 추출했습니다.",
                    )
                } else {
                    val result = OpenAiService(apiKey, aiSettings.selection(AiTaskType.PAINT_SCAN))
                        .analyzePaintPhotos(prepared.map { it.dataUrl })
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

    fun analyzeProjectPhotos(uris: List<Uri>) {
        require(uris.size in 1..5) { "프로젝트 사진은 1장 이상 5장 이하로 선택해주세요." }
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
                val prepared = uris.mapNotNull { uri ->
                    runCatching { preparePaintPhoto(uri) }.getOrNull()
                }
                require(prepared.isNotEmpty()) { "선택한 사진을 불러올 수 없습니다." }
                val apiKey = aiSettings.readApiKey()
                if (apiKey.isBlank()) {
                    _projectScanState.value = ProjectScanUiState(
                        draft = manualDraft,
                        notice = "GPT-5.6 연결 설정이 없어 촬영일만 입력했습니다. 나머지 정보는 직접 확인해주세요.",
                    )
                } else {
                    val result = OpenAiService(apiKey, aiSettings.selection(AiTaskType.SIMPLE_CHAT))
                        .analyzeProjectPhotos(prepared.map { it.dataUrl }, captureDate)
                    _projectScanState.value = ProjectScanUiState(
                        draft = result,
                        notice = if (prepared.size < uris.size) {
                            "사진 ${uris.size}장 중 ${prepared.size}장을 불러와 함께 분석했습니다. 불러오지 못한 사진은 교체해주세요."
                        } else null,
                    )
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
        recipeId: Long? = null,
    ) {
        if (prompt.isBlank() && targetHex.isNullOrBlank()) {
            _aiState.value = AiUiState(notice = "원하는 색을 설명하거나 사진 색상을 선택해주세요.")
            return
        }
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            val taskType = when {
                !targetHex.isNullOrBlank() -> AiTaskType.PHOTO_COLOR_MIX
                !currentRecipe.isNullOrBlank() -> AiTaskType.RECIPE_ADJUST
                else -> AiTaskType.COLOR_MIX
            }
            val highestQuality = requestsHighestQuality(prompt)
            val selection = aiSettings.selection(taskType, highestQuality)
            _aiState.value = AiUiState(loading = true, activeModelLabel = selection.resultLabel)
            val recentFeedback = recipeId?.let { id ->
                testResultsDao.recentForRecipe(id, 5).joinToString("\n") { row ->
                    val labels = row.evaluations.split('|').filter { it.isNotBlank() }.joinToString(", ") { TestEvaluation.label(it) }
                    "테스트: $labels ${row.memo}".trim()
                }.takeIf { it.isNotBlank() }
            }
            val request = AiMixRequest(
                prompt = prompt.ifBlank { "사진의 $targetHex 색상" },
                paints = paints.value,
                ownedOnly = ownedOnly,
                brand = brand,
                currentRecipe = listOfNotNull(currentRecipe, recentFeedback).joinToString("\n").takeIf { it.isNotBlank() },
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
                    val result = OpenAiService(apiKey, selection).suggestMix(request)
                    _aiState.value = AiUiState(suggestion = result, activeModelLabel = selection.resultLabel)
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
            val simpleQuestions = setOf("진행 요약", "다음 작업", "색 추천")
            val taskType = if (question.trim() in simpleQuestions) AiTaskType.SIMPLE_CHAT else AiTaskType.PAINTING_ADVICE
            val selection = aiSettings.selection(taskType, requestsHighestQuality(question))
            _aiState.value = AiUiState(loading = true, activeModelLabel = selection.resultLabel)
            val apiKey = aiSettings.readApiKey()
            if (apiKey.isBlank()) {
                _aiState.value = AiUiState(notice = "AI 연결을 사용할 수 없습니다. 설정에서 API 키를 등록해주세요.")
                return@launch
            }
            try {
                val advice = OpenAiService(apiKey, selection).advise(question, context)
                _aiState.value = AiUiState(advice = advice, activeModelLabel = selection.resultLabel)
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
        _partsComparisonState.value = PartsComparisonUiState()
    }

    private suspend fun prepareOriginalColorPhotos(imageUris: List<String>): Pair<List<String>, Int> {
        val uniqueUris = imageUris.distinct().take(5)
        require(uniqueUris.isNotEmpty()) { "원작 컬러 매칭 사진을 한 장 이상 선택해주세요." }
        val dataUrls = mutableListOf<String>()
        var failedCount = 0
        uniqueUris.forEach { imageUri ->
            try {
                dataUrls += preparePaintPhoto(Uri.parse(imageUri)).dataUrl
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                failedCount += 1
            }
        }
        require(dataUrls.isNotEmpty()) { "선택한 사진을 불러올 수 없습니다." }
        return dataUrls to failedCount
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

    private fun averageHex(colors: List<String>): String {
        val parsed = colors.mapNotNull { color -> runCatching { Color.parseColor(color) }.getOrNull() }
        if (parsed.isEmpty()) return "#808080"
        return "#%02X%02X%02X".format(
            parsed.sumOf { Color.red(it) } / parsed.size,
            parsed.sumOf { Color.green(it) } / parsed.size,
            parsed.sumOf { Color.blue(it) } / parsed.size,
        )
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

    private fun originalColorPartsJson(parts: List<AiOriginalColorPart>): String = JSONArray().apply {
        parts.forEach { part ->
            put(
                JSONObject()
                    .put("category", part.category)
                    .put("partName", part.partName)
                    .put("targetHex", part.targetHex)
                    .put("rgb", part.rgb)
                    .put("colorFamily", part.colorFamily)
                    .put("characteristics", part.characteristics)
                    .put("nearestPaintId", part.nearestPaintId ?: -1)
                    .put("nearestPaintName", part.nearestPaintName)
                    .put("nearestPaintCode", part.nearestPaintCode.orEmpty())
                    .put("singleColorUsable", part.singleColorUsable)
                    .put(
                        "mixOptions",
                        JSONArray().apply {
                            part.mixOptions.forEach { option ->
                                put(
                                    JSONObject()
                                        .put("label", option.label)
                                        .put("explanation", option.explanation)
                                        .put(
                                            "components",
                                            JSONArray().apply {
                                                option.components.forEach { component ->
                                                    put(
                                                        JSONObject()
                                                            .put("paintId", component.paintId)
                                                            .put("paintName", component.paintName)
                                                            .put("productCode", component.productCode.orEmpty())
                                                            .put("percent", component.percent),
                                                    )
                                                }
                                            },
                                        ),
                                )
                            }
                        },
                    ),
            )
        }
    }.toString()

    private fun parseHex(value: String): Int = runCatching {
        (0xFF000000L or value.removePrefix("#").takeLast(6).toLong(16)).toInt()
    }.getOrDefault(0xFF808080.toInt())

    private fun requestsHighestQuality(text: String): Boolean {
        val normalized = text.lowercase()
        return listOf("최고 품질", "정밀", "복잡", "여러 조건", "비교 분석", "highest quality")
            .any { it in normalized }
    }
}
