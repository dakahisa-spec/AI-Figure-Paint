package com.aifigurepaint.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aifigurepaint.app.AppViewModel
import com.aifigurepaint.app.IngredientInput
import com.aifigurepaint.app.data.MixRecipeEntity
import com.aifigurepaint.app.data.PaintEntity
import com.aifigurepaint.app.data.PhotoOwner
import com.aifigurepaint.app.data.ProjectEntity
import com.aifigurepaint.app.data.RecipeCardRow
import com.aifigurepaint.app.data.RecipeItemRow
import com.aifigurepaint.app.data.RecipeVersionEntity
import com.aifigurepaint.app.data.StockLevel
import com.aifigurepaint.app.data.TestEvaluation
import com.aifigurepaint.app.data.TestResultEntity
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

private data class IngredientDraft(val key: Long, val paintId: Long, val amount: String)

@Composable
internal fun RecipeListScreen(
    cards: List<RecipeCardRow>,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onAiMix: () -> Unit,
    onPhotoAnalyze: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("조색 레시피", style = MaterialTheme.typography.headlineMedium)
            Text("저장된 레시피 ${cards.size}개", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAiMix, modifier = Modifier.weight(1f).height(48.dp)) { Text("AI 조색") }
                Button(onClick = onAdd, modifier = Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(horizontal = 13.dp)) { Text("＋ 새 조색") }
            }
        }
        OutlinedButton(onClick = onPhotoAnalyze, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("사진에서 색상 추출") }
        Spacer(Modifier.height(8.dp))
        if (cards.isEmpty()) {
            EmptyCard("사용한 도료와 양을 기록해보세요.")
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (supportsFoldTwoPane(maxWidth)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) { gridItems(cards, key = { it.id }) { RecipeCard(it, onOpen) } }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) { items(cards, key = { it.id }) { RecipeCard(it, onOpen) } }
                }
            }
        }
    }
}

@Composable
internal fun RecipeEditorScreen(
    recipe: MixRecipeEntity?,
    initialProjectId: Long? = null,
    paints: List<PaintEntity>,
    projects: List<ProjectEntity>,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val context = LocalContext.current
    val existingRows = if (recipe != null) viewModel.recipeItems(recipe.id).collectAsState(initial = emptyList()).value else emptyList()
    val storedPhotos = if (recipe != null) viewModel.photos(PhotoOwner.RECIPE, recipe.id).collectAsState(initial = emptyList()).value else emptyList()
    var name by remember(recipe?.id) { mutableStateOf(recipe?.name.orEmpty()) }
    var projectId by remember(recipe?.id, initialProjectId) { mutableStateOf(recipe?.projectId ?: initialProjectId) }
    var resultHex by remember(recipe?.id) { mutableStateOf(colorToHex(recipe?.resultColorValue ?: 0xFFE1A1B6.toInt())) }
    var memo by remember(recipe?.id) { mutableStateOf(recipe?.memo.orEmpty()) }
    var projectMenu by remember { mutableStateOf(false) }
    var choosePaintForIndex by remember { mutableStateOf<Int?>(null) }
    var existingLoaded by remember(recipe?.id) { mutableStateOf(recipe == null) }
    var photosLoaded by remember(recipe?.id) { mutableStateOf(recipe == null) }
    val ingredients = remember(recipe?.id) { mutableStateListOf<IngredientDraft>() }
    val photoUris = remember(recipe?.id) { mutableStateListOf<String>() }
    val resultColor = parseHexColor(resultHex, recipe?.resultColorValue ?: 0xFFE1A1B6.toInt())
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && photoUris.size < 3) {
            copyPhotoToAppStorage(context, uri)?.let { stored ->
                if (stored !in photoUris) photoUris += stored
            }
        }
    }

    LaunchedEffect(existingRows) {
        if (recipe != null && !existingLoaded && existingRows.isNotEmpty()) {
            ingredients.clear()
            existingRows.forEach { row -> ingredients += IngredientDraft(row.id, row.paintId, formatMl(row.baseAmountMl)) }
            existingLoaded = true
        }
    }
    LaunchedEffect(storedPhotos, recipe?.id) {
        if (recipe != null && !photosLoaded) {
            val loaded = storedPhotos.map { it.uri }.ifEmpty { listOfNotNull(recipe.photoUri) }
            if (loaded.isNotEmpty()) {
                photoUris.clear()
                photoUris.addAll(loaded.take(3))
                photosLoaded = true
            }
        }
    }
    LaunchedEffect(paints, recipe?.id) {
        if (recipe == null && ingredients.isEmpty() && paints.isNotEmpty()) {
            ingredients += IngredientDraft(System.nanoTime(), paints.first().id, "")
        }
    }

    Scaffold(topBar = { EditorHeader(if (recipe == null) "새 조색" else "조색 수정", onBack) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).imePadding()) {
            val wide = supportsFoldTwoPane(maxWidth)
            val left: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ColorSwatch(resultColor, 62.dp)
                        Column(Modifier.weight(1f)) {
                            Text("조색 레시피", style = MaterialTheme.typography.titleLarge)
                            Text("원본 비율은 그대로 보존됩니다", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("레시피 이름") }, singleLine = true)
                    Box {
                        OutlinedButton(onClick = { projectMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(projects.firstOrNull { it.id == projectId }?.name ?: "프로젝트 연결 (선택)")
                        }
                        DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                            DropdownMenuItem(text = { Text("프로젝트 없음") }, onClick = { projectId = null; projectMenu = false })
                            projects.forEach { project ->
                                DropdownMenuItem(text = { Text(project.name) }, onClick = { projectId = project.id; projectMenu = false })
                            }
                        }
                    }
                    OutlinedTextField(
                        resultHex, { resultHex = it }, Modifier.fillMaxWidth(), label = { Text("결과 색상 HEX") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    )
                    SectionTitle("결과 사진", "갤러리에서 최대 3장")
                    PhotoStrip(photoUris, itemSize = if (wide) 126.dp else 98.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { photoPicker.launch(arrayOf("image/*")) },
                            enabled = photoUris.size < 3,
                            modifier = Modifier.weight(1f),
                        ) { Text(if (photoUris.isEmpty()) "사진 선택" else "사진 추가 ${photoUris.size}/3") }
                        if (photoUris.isNotEmpty()) OutlinedButton(onClick = { photoUris.removeLast() }) { Text("마지막 삭제") }
                    }
                    OutlinedTextField(memo, { memo = it }, Modifier.fillMaxWidth().height(104.dp), label = { Text("메모") })
                }
            }
            val right: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SectionTitle("사용 도료", "보유 도료에서 검색해 추가")
                    if (paints.isEmpty()) Text("먼저 도료 탭에서 도료를 등록해주세요.", color = MaterialTheme.colorScheme.error)
                    ingredients.forEachIndexed { index, ingredient ->
                        val selectedPaint = paints.firstOrNull { it.id == ingredient.paintId }
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (selectedPaint != null) ColorSwatch(selectedPaint.colorValue, 38.dp)
                                Spacer(Modifier.size(9.dp))
                                Column(Modifier.weight(1f).clickable { choosePaintForIndex = index }) {
                                    Text(selectedPaint?.let { listOfNotNull(it.productCode, it.name).joinToString(" ") } ?: "도료 선택", style = MaterialTheme.typography.titleMedium)
                                    Text(selectedPaint?.brand ?: "내 도료에서 선택", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                OutlinedTextField(
                                    value = ingredient.amount,
                                    onValueChange = { ingredients[index] = ingredient.copy(amount = it.replace(',', '.')) },
                                    modifier = Modifier.size(width = 106.dp, height = 52.dp),
                                    suffix = { Text("ml") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                )
                                if (ingredients.size > 1) TextButton(onClick = { ingredients.removeAt(index) }) { Text("×") }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { paints.firstOrNull()?.let { ingredients += IngredientDraft(System.nanoTime(), it.id, "") } },
                        enabled = paints.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("＋ 도료 추가") }
                    val total = ingredients.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("기준 총량", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("${formatMl(total)} ml", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    Button(
                        onClick = {
                            val inputs = ingredients.mapNotNull { item ->
                                item.amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { IngredientInput(item.paintId, it) }
                            }
                            viewModel.saveRecipe(
                                recipe = MixRecipeEntity(
                                    id = recipe?.id ?: 0,
                                    projectId = projectId,
                                    name = name.trim(),
                                    baseTotalMl = total,
                                    memo = memo.trim(),
                                    resultColorValue = resultColor,
                                    favorite = recipe?.favorite ?: false,
                                    photoUri = photoUris.firstOrNull(),
                                    createdAt = recipe?.createdAt ?: System.currentTimeMillis(),
                                ),
                                ingredients = inputs,
                                photoUris = photoUris.toList(),
                                onSaved = onSaved,
                            )
                        },
                        enabled = name.isNotBlank() && total > 0 && paints.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("레시피 저장") }
                }
            }
            if (wide) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Column(Modifier.weight(.85f).verticalScroll(rememberScrollState())) { left(); Spacer(Modifier.height(16.dp)) }
                    Column(Modifier.weight(1.15f).verticalScroll(rememberScrollState())) { right(); Spacer(Modifier.height(16.dp)) }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) { left(); right(); Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    choosePaintForIndex?.let { index ->
        PaintChooserDialog(
            paints = paints,
            onDismiss = { choosePaintForIndex = null },
            onChoose = { paint ->
                ingredients[index] = ingredients[index].copy(paintId = paint.id)
                choosePaintForIndex = null
            },
        )
    }
}

@Composable
private fun PaintChooserDialog(paints: List<PaintEntity>, onDismiss: () -> Unit, onChoose: (PaintEntity) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = paints.filter { paint ->
        query.isBlank() || listOf(paint.productCode.orEmpty(), paint.name, paint.koreanName, paint.brand)
            .any { it.contains(query.trim(), ignoreCase = true) }
    }.sortedWith(compareByDescending<PaintEntity> { it.favorite }.thenByDescending { it.owned }.thenByDescending { it.lastUsedAt ?: 0L })
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("내 도료 선택", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("도료명 · 코드 · 브랜드 검색") }, singleLine = true)
                LazyColumn(Modifier.height(390.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered, key = { it.id }) { paint ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onChoose(paint) }.padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ColorSwatch(paint.colorValue, 36.dp)
                            Spacer(Modifier.size(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(listOfNotNull(paint.productCode, paint.name).joinToString(" "), style = MaterialTheme.typography.titleMedium)
                                Text("${paint.brand} · ${StockLevel.label(paint.stockLevel)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (paint.favorite) Text("★", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("닫기") }
            }
        }
    }
}

@Composable
internal fun RecipeDetailScreen(
    recipe: MixRecipeEntity,
    project: ProjectEntity?,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAiAdjust: () -> Unit,
) {
    val items by viewModel.recipeItems(recipe.id).collectAsState(initial = emptyList())
    val storedPhotos by viewModel.photos(PhotoOwner.RECIPE, recipe.id).collectAsState(initial = emptyList())
    val photoUris = storedPhotos.map { it.uri }.ifEmpty { listOfNotNull(recipe.photoUri) }
    val versions by viewModel.recipeVersions(recipe.id).collectAsState(initial = emptyList())
    val testResults by viewModel.testResults(recipe.id).collectAsState(initial = emptyList())
    val adjustment by viewModel.testAdjustmentState.collectAsState()
    var targetText by remember(recipe.id) { mutableStateOf(formatMl(recipe.baseTotalMl)) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var showTestEditor by remember { mutableStateOf(false) }
    val target = targetText.toDoubleOrNull()?.takeIf { it > 0 } ?: recipe.baseTotalMl
    val scale = if (recipe.baseTotalMl > 0) target / recipe.baseTotalMl else 1.0

    Scaffold(topBar = { EditorHeader("조색 상세", onBack) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 700.dp
            val summary: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(recipe.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.toggleRecipeFavorite(recipe) }) { Text(if (recipe.favorite) "★" else "☆", fontSize = 22.sp) }
                    }
                    Text(
                        "${project?.name ?: "연결 프로젝트 없음"} · 수정 ${formatDateTime(recipe.updatedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (project != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ColorSwatch(recipe.resultColorValue, if (wide) 112.dp else 80.dp)
                        Column {
                            Text("기준 ${formatMl(recipe.baseTotalMl)} ml", style = MaterialTheme.typography.titleLarge)
                            Text("도료 ${items.size}종", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    PhotoStrip(photoUris, itemSize = if (wide) 122.dp else 96.dp)
                    if (recipe.memo.isNotBlank()) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))) {
                            Text(recipe.memo, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            val amounts: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("총량 자동 환산", "원본 비율은 변경되지 않습니다")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(3.0, 5.0, 10.0, 15.0, 20.0).forEach { value ->
                            FilterChip(
                                selected = abs(target - value) < 0.001,
                                onClick = { targetText = formatMl(value) },
                                label = { Text("${formatMl(value)}ml") },
                            )
                        }
                    }
                    OutlinedTextField(
                        targetText,
                        { targetText = it.replace(',', '.') },
                        Modifier.fillMaxWidth(),
                        label = { Text("직접 입력") },
                        suffix = { Text("ml") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("환산 총량", modifier = Modifier.weight(1f))
                            Text("${formatMl(target)} ml", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    SectionTitle("조색 구성")
                    items.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            ColorSwatch(item.colorValue, 40.dp)
                            Spacer(Modifier.size(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(listOfNotNull(item.productCode, item.paintName).joinToString(" "), style = MaterialTheme.typography.titleMedium)
                                Text(item.brand, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${formatMl(item.baseAmountMl * scale)} ml", style = MaterialTheme.typography.titleLarge)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
                    }
                    SectionTitle("레시피 버전", "기존 버전은 삭제하거나 덮어쓰지 않습니다")
                    if (versions.isEmpty()) EmptyCard("다음 저장부터 버전 기록이 생성됩니다.")
                    versions.take(8).forEach { version ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("v${version.versionNumber} · ${version.label}", style = MaterialTheme.typography.titleMedium)
                                    Text("${formatMl(version.snapshotTotalMl)}ml · ${formatDateTime(version.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (version.aiGenerated) Text("AI", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    SectionTitle("테스트 기록", "실제 테스트 피스 결과와 AI 보정")
                    Button(
                        onClick = { showTestEditor = true },
                        enabled = versions.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                    ) { Text("테스트 결과 기록") }
                    if (versions.isEmpty()) {
                        Text("먼저 레시피를 저장해 버전을 만든 뒤 기록할 수 있습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (testResults.isEmpty()) EmptyCard("아직 테스트 피스 기록이 없습니다.")
                    testResults.forEach { result ->
                        TestResultCard(
                            result = result,
                            version = versions.firstOrNull { it.id == result.recipeVersionId },
                            viewModel = viewModel,
                            onAdjust = {
                                versions.firstOrNull { it.id == result.recipeVersionId }?.let { version ->
                                    viewModel.requestTestAdjustment(recipe, version, result)
                                }
                            },
                        )
                    }
                    val visibleSuggestion = adjustment.suggestion?.takeIf { adjustment.testResultId in testResults.map { it.id } }
                    if (visibleSuggestion != null) {
                        TestAdjustmentComparison(
                            currentItems = items,
                            suggestion = visibleSuggestion,
                            loading = adjustment.loading,
                            notice = adjustment.notice,
                            onSave = { viewModel.saveAiSuggestion(visibleSuggestion, recipeId = recipe.id) { viewModel.clearTestAdjustment() } },
                            onDismiss = viewModel::clearTestAdjustment,
                        )
                    } else if (adjustment.testResultId in testResults.map { it.id } && (adjustment.loading || adjustment.notice != null)) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))) {
                            Text(if (adjustment.loading) "AI 보정안을 계산하고 있습니다…" else adjustment.notice.orEmpty(), Modifier.padding(12.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onEdit, modifier = Modifier.weight(1f).height(42.dp)) { Text("수정") }
                        OutlinedButton(onClick = onAiAdjust, modifier = Modifier.weight(1f).height(42.dp)) { Text("AI 조정") }
                        TextButton(onClick = { deleteConfirm = true }, modifier = Modifier.height(42.dp)) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Column(Modifier.weight(.8f).verticalScroll(rememberScrollState())) { summary() }
                    Column(Modifier.weight(1.2f).verticalScroll(rememberScrollState())) { amounts() }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(17.dp),
                ) { summary(); amounts(); Spacer(Modifier.height(16.dp)) }
            }
        }
    }
    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("레시피 삭제") },
            text = { Text("${recipe.name} 레시피를 삭제할까요?") },
            confirmButton = { TextButton(onClick = { viewModel.deleteRecipe(recipe, onBack) }) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("취소") } },
        )
    }
    if (showTestEditor) {
        TestResultEditorDialog(
            recipeId = recipe.id,
            versions = versions,
            viewModel = viewModel,
            onDismiss = { showTestEditor = false },
        )
    }
}

@Composable
private fun TestResultEditorDialog(
    recipeId: Long,
    versions: List<RecipeVersionEntity>,
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var selectedVersion by remember { mutableStateOf(versions.firstOrNull()) }
    var versionMenu by remember { mutableStateOf(false) }
    var dateText by remember { mutableStateOf(LocalDate.now().toString()) }
    var memo by remember { mutableStateOf("") }
    val evaluations = remember { mutableStateListOf<String>() }
    val photos = remember { mutableStateListOf<String>() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && photos.size < 3) copyPhotoToAppStorage(context, uri)?.let { if (it !in photos) photos += it }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("테스트 결과 기록") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(onClick = { versionMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedVersion?.let { "v${it.versionNumber} · ${it.label}" } ?: "레시피 버전 선택")
                    }
                    DropdownMenu(versionMenu, { versionMenu = false }) {
                        versions.forEach { version ->
                            DropdownMenuItem(
                                text = { Text("v${version.versionNumber} · ${version.label}") },
                                onClick = { selectedVersion = version; versionMenu = false },
                            )
                        }
                    }
                }
                OutlinedTextField(dateText, { dateText = it }, Modifier.fillMaxWidth(), label = { Text("작업 날짜 (YYYY-MM-DD)") }, singleLine = true)
                Text("결과 평가 · 복수 선택", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(TestEvaluation.entries) { value ->
                        FilterChip(
                            selected = value in evaluations,
                            onClick = { if (value in evaluations) evaluations.remove(value) else evaluations.add(value) },
                            label = { Text(TestEvaluation.label(value)) },
                        )
                    }
                }
                PhotoStrip(photos, itemSize = 76.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, enabled = photos.size < 3, modifier = Modifier.weight(1f)) { Text("사진 추가 ${photos.size}/3") }
                    if (photos.isNotEmpty()) TextButton(onClick = { photos.removeLast() }) { Text("삭제") }
                }
                OutlinedTextField(memo, { memo = it }, Modifier.fillMaxWidth().height(92.dp), label = { Text("사용자 메모 (선택)") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val date = runCatching { LocalDate.parse(dateText).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrDefault(System.currentTimeMillis())
                    val version = selectedVersion ?: return@Button
                    viewModel.saveTestResult(
                        TestResultEntity(recipeId = recipeId, recipeVersionId = version.id, testDate = date, evaluations = evaluations.joinToString("|"), memo = memo.trim()),
                        photos.toList(),
                        onDismiss,
                    )
                },
                enabled = selectedVersion != null && runCatching { LocalDate.parse(dateText) }.isSuccess,
            ) { Text("기록 저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun TestResultCard(
    result: TestResultEntity,
    version: RecipeVersionEntity?,
    viewModel: AppViewModel,
    onAdjust: () -> Unit,
) {
    val photos by viewModel.photos(PhotoOwner.TEST_RESULT, result.id).collectAsState(initial = emptyList())
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f)), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${formatDateTime(result.testDate)} · ${version?.let { "v${it.versionNumber}" } ?: "버전 정보 없음"}", style = MaterialTheme.typography.titleSmall)
                    val labels = result.evaluations.split('|').filter { it.isNotBlank() }.joinToString(" · ") { TestEvaluation.label(it) }
                    if (labels.isNotBlank()) Text(labels, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = onAdjust, enabled = version != null) { Text("AI 보정 요청") }
            }
            PhotoStrip(photos.map { it.uri }, itemSize = 68.dp)
            if (result.memo.isNotBlank()) Text(result.memo, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { viewModel.deleteTestResult(result) }, modifier = Modifier.align(Alignment.End)) { Text("기록 삭제", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun TestAdjustmentComparison(
    currentItems: List<RecipeItemRow>,
    suggestion: com.aifigurepaint.app.ai.AiMixSuggestion,
    loading: Boolean,
    notice: String?,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val total = currentItems.sumOf { it.baseAmountMl }.takeIf { it > 0 } ?: 1.0
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("현재 레시피 vs AI 보정안", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("현재 레시피", style = MaterialTheme.typography.labelLarge)
                    currentItems.forEach { Text("${it.productCode ?: it.paintName} ${"%.1f".format(it.baseAmountMl / total * 100)}%", style = MaterialTheme.typography.bodySmall) }
                }
                Column(Modifier.weight(1f)) {
                    Text("AI 보정안", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    suggestion.components.forEach { Text("${it.productCode ?: it.paintName} ${"%.1f".format(it.percent)}%", style = MaterialTheme.typography.bodySmall) }
                }
            }
            Text(suggestion.explanation, style = MaterialTheme.typography.bodySmall)
            Text(suggestion.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text("예상 조색 · 실제 결과는 안료, 희석비, 바탕색과 도막 두께에 따라 달라질 수 있습니다.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            notice?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("닫기") }
                Button(onClick = onSave, enabled = !loading) { Text("새 버전으로 저장") }
            }
        }
    }
}
