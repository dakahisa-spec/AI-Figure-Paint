package com.aifigurepaint.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aifigurepaint.app.AppViewModel
import com.aifigurepaint.app.ai.AiMixSuggestion
import com.aifigurepaint.app.ai.AiModelMode
import com.aifigurepaint.app.ai.AiTaskType
import com.aifigurepaint.app.data.MixRecipeEntity
import com.aifigurepaint.app.data.PaintEntity
import com.aifigurepaint.app.data.ProjectEntity
import com.aifigurepaint.app.data.ProjectStage
import com.aifigurepaint.app.data.ProjectStatus
import com.aifigurepaint.app.data.ProjectTimelineEntryEntity
import com.aifigurepaint.app.data.RecipeCardRow
import com.aifigurepaint.app.data.StockLevel

@Composable
internal fun AiSettingsDialog(
    configured: Boolean,
    currentMode: AiModelMode,
    onDismiss: () -> Unit,
    onSave: (String, AiModelMode) -> Unit,
    onClear: () -> Unit,
    onDataManagement: () -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var mode by remember(currentMode) { mutableStateOf(currentMode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 연결 설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "API 키는 소스나 APK 상수에 포함되지 않으며 Android Keystore로 이 기기 안에 암호화됩니다. 배포용 앱은 서버 프록시 사용을 권장합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (configured) "새 API 키 (변경할 때만 입력)" else "OpenAI API 키") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Text("AI 모델", style = MaterialTheme.typography.titleSmall)
                AiModelMode.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { mode = option }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == option, onClick = { mode = option })
                        Column(Modifier.weight(1f)) {
                            Text(option.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(option.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    if (configured) "연결 정보가 설정되어 있습니다." else "키가 없어도 RGB/HEX 추출과 로컬 색상 후보는 작동합니다.",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { onDismiss(); onDataManagement() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("데이터 관리 · Excel") }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(key, mode); onDismiss() }) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (configured) TextButton(onClick = { onClear(); onDismiss() }) { Text("키 삭제", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
        },
    )
}

@Composable
internal fun WidePaintStudio(
    paints: List<PaintEntity>,
    viewModel: AppViewModel,
    onAdd: () -> Unit,
    onScan: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var query by remember { mutableStateOf("") }
    var ownedOnly by remember { mutableStateOf(false) }
    LaunchedEffect(paints) { if (selectedId == null || paints.none { it.id == selectedId }) selectedId = paints.firstOrNull()?.id }
    val filtered = paints.filter {
        (!ownedOnly || it.owned) && (query.isBlank() || listOf(it.name, it.koreanName, it.brand, it.productCode.orEmpty()).any { value -> value.contains(query, true) })
    }
    val selected = paints.firstOrNull { it.id == selectedId }
    Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        val panelShape = RoundedCornerShape(10.dp)
        Card(
            Modifier.weight(.42f).fillMaxHeight().border(1.dp, StudioBorder, panelShape),
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
            shape = panelShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(Modifier.fillMaxSize().padding(10.dp)) {
                StudioHeader(
                    "내 도료",
                    "보유 ${paints.count { it.owned }} · 전체 ${paints.size}",
                    "＋ 추가",
                    onAdd,
                    secondaryAction = "◎ AI 촬영",
                    onSecondary = onScan,
                )
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 10.dp), placeholder = { Text("도료명 · 코드 · 브랜드 검색") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(ownedOnly, { ownedOnly = it })
                    Text("보유 도료만", style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filtered, key = { it.id }) { paint ->
                        val active = selectedId == paint.id
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth().clickable { selectedId = paint.id },
                            colors = CardDefaults.outlinedCardColors(if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f) else MaterialTheme.colorScheme.surface),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                ColorSwatch(paint.colorValue, 38.dp)
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(paint.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${paint.brand} · ${paint.productCode.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(if (paint.favorite) "★" else "☆", color = MaterialTheme.colorScheme.primary)
                                    Text(StockLevel.percentLabel(paint.stockLevel), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
        Card(
            Modifier.weight(.58f).fillMaxHeight().border(1.dp, StudioBorder, panelShape),
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
            shape = panelShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            if (selected == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("도료를 선택해주세요.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                PaintMasterDetail(selected, viewModel, onEdit)
            }
        }
    }
}

@Composable
private fun PaintMasterDetail(paint: PaintEntity, viewModel: AppViewModel, onEdit: (Long) -> Unit) {
    val usedRecipes by viewModel.paintRecipes(paint.id).collectAsState(initial = emptyList())
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            ColorSwatch(paint.colorValue, 126.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(paint.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.togglePaintFavorite(paint) }) { Text(if (paint.favorite) "★" else "☆", fontSize = 24.sp) }
                }
                if (paint.koreanName.isNotBlank()) Text(paint.koreanName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${paint.brand} · ${paint.series} · ${paint.productCode.orEmpty()}", style = MaterialTheme.typography.bodyMedium)
                Text("최근 사용 ${paint.lastUsedAt?.let(::formatDateTime) ?: "기록 없음"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider()
        SectionTitle("재고 상태", "단계형 잔량 관리")
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            StockLevel.entries.forEach { level ->
                FilterChip(selected = paint.stockLevel == level, onClick = { viewModel.setPaintStock(paint, level) }, label = { Text(StockLevel.percentLabel(level)) })
            }
        }
        if (paint.memo.isNotBlank()) {
            SectionTitle("메모")
            Text(paint.memo, style = MaterialTheme.typography.bodyMedium)
        }
        SectionTitle("사용된 레시피", "${usedRecipes.size}개")
        if (usedRecipes.isEmpty()) EmptyCard("아직 이 도료를 사용한 레시피가 없습니다.")
        usedRecipes.take(6).forEach { card ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ColorSwatch(card.resultColorValue, 30.dp)
                Spacer(Modifier.width(9.dp))
                Text(card.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(formatDateTime(card.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = { onEdit(paint.id) }, modifier = Modifier.fillMaxWidth()) { Text("도료 정보 수정") }
    }
}

@Composable
internal fun WideRecipeStudio(
    cards: List<RecipeCardRow>,
    recipes: List<MixRecipeEntity>,
    paints: List<PaintEntity>,
    projects: List<ProjectEntity>,
    viewModel: AppViewModel,
    onNew: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenSaved: (Long) -> Unit,
    onSettings: () -> Unit,
    onPhotoAnalyze: () -> Unit,
) {
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var query by remember { mutableStateOf("") }
    var favoriteOnly by remember { mutableStateOf(false) }
    var projectFilter by remember { mutableStateOf<Long?>(null) }
    var projectMenu by remember { mutableStateOf(false) }
    LaunchedEffect(cards) { if (selectedId == null || cards.none { it.id == selectedId }) selectedId = cards.firstOrNull()?.id }
    val filtered = cards.filter { card ->
        (!favoriteOnly || recipes.firstOrNull { it.id == card.id }?.favorite == true) &&
            (projectFilter == null || card.projectName?.contains(projects.firstOrNull { it.id == projectFilter }?.name.orEmpty(), true) == true) &&
            (query.isBlank() || listOf(card.name, card.projectName.orEmpty(), card.components).any { it.contains(query, true) })
    }
    val selectedRecipe = recipes.firstOrNull { it.id == selectedId }
    val selectedCard = cards.firstOrNull { it.id == selectedId }
    val recipeItems = if (selectedRecipe != null) viewModel.recipeItems(selectedRecipe.id).collectAsState(initial = emptyList()).value else emptyList()
    Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        val panelShape = RoundedCornerShape(10.dp)
        Card(
            Modifier.weight(.28f).fillMaxHeight().border(1.dp, StudioBorder, panelShape),
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
            shape = panelShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(Modifier.fillMaxSize().padding(10.dp)) {
                StudioHeader("조색 레시피", "${cards.size}개", "＋ 새 조색", onNew)
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = { Text("레시피 · 프로젝트 검색") }, singleLine = true)
                FilterChip(selected = !favoriteOnly && projectFilter == null, onClick = { favoriteOnly = false; projectFilter = null }, label = { Text("최근 사용") })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = favoriteOnly, onClick = { favoriteOnly = !favoriteOnly }, label = { Text("★ 즐겨찾기") })
                    Box {
                        FilterChip(
                            selected = projectFilter != null,
                            onClick = { projectMenu = true },
                            label = { Text(projects.firstOrNull { it.id == projectFilter }?.name ?: "프로젝트") },
                        )
                        DropdownMenu(projectMenu, { projectMenu = false }) {
                            DropdownMenuItem(text = { Text("전체 프로젝트") }, onClick = { projectFilter = null; projectMenu = false })
                            projects.forEach { project ->
                                DropdownMenuItem(text = { Text(project.name) }, onClick = { projectFilter = project.id; projectMenu = false })
                            }
                        }
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filtered, key = { it.id }) { card ->
                        OutlinedCard(
                            Modifier.fillMaxWidth().clickable { selectedId = card.id },
                            colors = CardDefaults.outlinedCardColors(if (selectedId == card.id) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f) else MaterialTheme.colorScheme.surface),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                ColorSwatch(card.resultColorValue, 34.dp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(card.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${card.itemCount}색 · ${formatMl(card.baseTotalMl)}ml", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (recipes.firstOrNull { it.id == card.id }?.favorite == true) Text("★", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
        Card(
            Modifier.weight(.42f).fillMaxHeight().border(1.dp, StudioBorder, panelShape),
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
            shape = panelShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            if (selectedRecipe == null || selectedCard == null) {
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
                    Text("조색 레시피가 없습니다", style = MaterialTheme.typography.titleLarge)
                    Text("원하는 색을 AI에게 설명하거나 직접 레시피를 만들어보세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onNew) { Text("직접 만들기") }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColorSwatch(selectedRecipe.resultColorValue, 72.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(selectedRecipe.name, style = MaterialTheme.typography.headlineSmall)
                            Text(selectedCard.projectName ?: "독립 레시피", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { viewModel.toggleRecipeFavorite(selectedRecipe) }) { Text(if (selectedRecipe.favorite) "★" else "☆", fontSize = 21.sp) }
                    }
                    SectionTitle("현재 레시피", "기준 ${formatMl(selectedRecipe.baseTotalMl)}ml")
                    recipeItems.forEach { row ->
                        val ratio = if (selectedRecipe.baseTotalMl > 0) row.baseAmountMl / selectedRecipe.baseTotalMl * 100 else 0.0
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            ColorSwatch(row.colorValue, 32.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(listOfNotNull(row.productCode, row.paintName).joinToString(" "), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("${formatMl(ratio)}%", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    RecipeConversionPanel(selectedRecipe, recipeItems.map { it.paintName to it.baseAmountMl })
                    OutlinedButton(onClick = { onEdit(selectedRecipe.id) }, modifier = Modifier.fillMaxWidth()) { Text("레시피 편집") }
                }
            }
        }
        Card(
            Modifier.weight(.30f).fillMaxHeight().border(1.dp, StudioBorder, panelShape),
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
            shape = panelShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            AiMixPanel(
                paints = paints,
                projects = projects,
                viewModel = viewModel,
                currentRecipe = selectedRecipe,
                currentRecipeText = selectedRecipe?.let { recipe ->
                    "${recipe.name}: " + recipeItems.joinToString { "${it.productCode ?: it.paintName} ${formatMl(it.baseAmountMl)}ml" }
                },
                onOpenSaved = onOpenSaved,
                onSettings = onSettings,
                onPhotoAnalyze = onPhotoAnalyze,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun RecipeConversionPanel(recipe: MixRecipeEntity, components: List<Pair<String, Double>>) {
    var total by remember(recipe.id) { mutableStateOf(10.0) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        SectionTitle("실시간 환산")
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(3.0, 5.0, 10.0, 15.0, 20.0).forEach { amount ->
                FilterChip(selected = total == amount, onClick = { total = amount }, label = { Text("${amount.toInt()}ml") })
            }
        }
        components.forEach { (name, amount) ->
            Row(Modifier.fillMaxWidth()) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text("${formatMl(if (recipe.baseTotalMl > 0) amount / recipe.baseTotalMl * total else 0.0)}ml", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
internal fun AiMixScreen(
    paints: List<PaintEntity>,
    projects: List<ProjectEntity>,
    viewModel: AppViewModel,
    targetHex: String?,
    currentRecipe: MixRecipeEntity? = null,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onPhotoAnalyze: () -> Unit,
    onOpenSaved: (Long) -> Unit,
) {
    val currentItems = if (currentRecipe != null) viewModel.recipeItems(currentRecipe.id).collectAsState(initial = emptyList()).value else emptyList()
    androidx.compose.material3.Scaffold(topBar = { EditorHeader("AI 조색 Assistant", onBack) }) { padding ->
        AiMixPanel(
            paints = paints,
            projects = projects,
            viewModel = viewModel,
            targetHex = targetHex,
            currentRecipe = currentRecipe,
            currentRecipeText = currentRecipe?.let { recipe ->
                "${recipe.name}: " + currentItems.joinToString { "${it.productCode ?: it.paintName} ${formatMl(it.baseAmountMl)}ml" }
            },
            onSettings = onSettings,
            onPhotoAnalyze = onPhotoAnalyze,
            onOpenSaved = onOpenSaved,
            modifier = Modifier.fillMaxSize().padding(padding).padding(14.dp),
        )
    }
}

@Composable
private fun AiMixPanel(
    paints: List<PaintEntity>,
    projects: List<ProjectEntity>,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
    currentRecipe: MixRecipeEntity? = null,
    currentRecipeText: String? = null,
    targetHex: String? = null,
    onSettings: () -> Unit,
    onPhotoAnalyze: () -> Unit,
    onOpenSaved: (Long) -> Unit,
) {
    val state by viewModel.aiState.collectAsState()
    val configured by viewModel.aiConfigured.collectAsState()
    val selectedMode by viewModel.aiModelMode.collectAsState()
    var prompt by remember(targetHex, currentRecipe?.id) { mutableStateOf(if (targetHex != null) "$targetHex 색상을 보유 도료로 만들어줘" else "") }
    var ownedOnly by remember { mutableStateOf(true) }
    var brand by remember { mutableStateOf<String?>(null) }
    var brandMenu by remember { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }
    var projectId by remember { mutableStateOf<Long?>(currentRecipe?.projectId) }
    var total by remember { mutableStateOf(10.0) }
    val brands = paints.filter { !ownedOnly || it.owned }.map { it.brand }.distinct().sorted()
    Column(modifier.verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AI Assistant", style = MaterialTheme.typography.titleLarge)
                Text(if (configured) "보유 도료를 이해하는 조색 보조" else "로컬 분석 사용 중 · AI 설정 가능", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val plannedTask = when {
                    targetHex != null -> AiTaskType.PHOTO_COLOR_MIX
                    currentRecipe != null -> AiTaskType.RECIPE_ADJUST
                    else -> AiTaskType.COLOR_MIX
                }
                Text(
                    "${selectedMode.title} · ${viewModel.aiModelLabel(plannedTask)} 사용 예정",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(onClick = onSettings) { Text("설정") }
        }
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth().height(104.dp),
            label = { Text(if (currentRecipe == null) "어떤 색을 만들까요?" else "현재 레시피를 어떻게 조정할까요?") },
            placeholder = { Text("예: 사자비 외장에 쓸 깊고 고급스러운 빨강") },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(ownedOnly, { ownedOnly = it })
            Text("보유 도료만 사용", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Box {
                FilterChip(selected = brand != null, onClick = { brandMenu = true }, label = { Text(brand ?: "모든 브랜드") })
                DropdownMenu(brandMenu, { brandMenu = false }) {
                    DropdownMenuItem(text = { Text("모든 브랜드") }, onClick = { brand = null; brandMenu = false })
                    brands.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { brand = item; brandMenu = false }) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Button(
                onClick = { viewModel.requestMix(prompt, ownedOnly, brand, currentRecipeText, targetHex, currentRecipe?.id) },
                enabled = !state.loading && (prompt.isNotBlank() || targetHex != null),
                modifier = Modifier.weight(1f),
            ) { Text(if (currentRecipe == null) "추천 만들기" else "AI로 조정") }
            OutlinedButton(onClick = { viewModel.cancelAiRequest(); onPhotoAnalyze() }) { Text("사진 분석") }
        }
        if (state.loading) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(9.dp))
                Text("예상 조색을 계산하는 중…")
            }
        }
        state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        state.suggestion?.let { suggestion ->
            AiSuggestionCard(suggestion, total) { total = it }
            if (currentRecipe == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("프로젝트 연결", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { projectMenu = true }) { Text(projects.firstOrNull { it.id == projectId }?.name ?: "선택 안 함") }
                        DropdownMenu(projectMenu, { projectMenu = false }) {
                            DropdownMenuItem(text = { Text("선택 안 함") }, onClick = { projectId = null; projectMenu = false })
                            projects.forEach { project ->
                                DropdownMenuItem(text = { Text(project.name) }, onClick = { projectId = project.id; projectMenu = false })
                            }
                        }
                    }
                }
                Button(
                    onClick = { viewModel.saveAiSuggestion(suggestion, projectId = projectId, onSaved = onOpenSaved) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("새 레시피로 저장") }
            } else {
                Button(
                    onClick = { viewModel.saveAiSuggestion(suggestion, recipeId = currentRecipe.id, onSaved = onOpenSaved) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("새 버전으로 저장") }
                Text("현재 레시피는 변경되지 않습니다.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("예상 조색", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            "실제 결과는 도료의 안료 특성, 희석비, 바탕색 및 도막 두께에 따라 달라질 수 있습니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AiSuggestionCard(suggestion: AiMixSuggestion, total: Double, onTotal: (Double) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorSwatch(parseHexColor(suggestion.targetHex, 0xFF808080.toInt()), 52.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(suggestion.name, style = MaterialTheme.typography.titleMedium)
                    Text("${suggestion.source} · 예상 조색", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(3.0, 5.0, 10.0, 15.0, 20.0).forEach { value ->
                    FilterChip(selected = total == value, onClick = { onTotal(value) }, label = { Text("${value.toInt()}ml") })
                }
            }
            suggestion.components.forEach { item ->
                Row(Modifier.fillMaxWidth()) {
                    Text(listOfNotNull(item.productCode, item.paintName).joinToString(" "), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text("${formatMl(item.percent)}% · ${formatMl(item.percent / 100.0 * total)}ml", style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(suggestion.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun WideProjectStudio(
    projects: List<ProjectEntity>,
    cards: List<RecipeCardRow>,
    viewModel: AppViewModel,
    onAdd: () -> Unit,
    onScan: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onNewRecipe: (Long) -> Unit,
    onSettings: () -> Unit,
    onPhotoAnalyze: () -> Unit,
) {
    var selectedId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(projects) { if (selectedId == null || projects.none { it.id == selectedId }) selectedId = projects.firstOrNull()?.id }
    val selected = projects.firstOrNull { it.id == selectedId }
    Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        val panelShape = RoundedCornerShape(10.dp)
        Card(
            Modifier.weight(.36f).fillMaxHeight().border(1.dp, StudioBorder, panelShape),
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
            shape = panelShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(Modifier.fillMaxSize().padding(10.dp)) {
                StudioHeader(
                    "프로젝트",
                    "도색 작업 ${projects.size}개",
                    "＋ 프로젝트",
                    onAdd,
                    secondaryAction = "◎ AI 촬영",
                    onSecondary = onScan,
                )
                LazyColumn(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(projects, key = { it.id }) { project ->
                        OutlinedCard(
                            Modifier.fillMaxWidth().clickable { selectedId = project.id },
                            colors = CardDefaults.outlinedCardColors(if (selectedId == project.id) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f) else MaterialTheme.colorScheme.surface),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                PhotoPreview(project.photoUri, Modifier.size(58.dp))
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(project.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                    Text(project.modelName.ifBlank { "모델명 미입력" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(ProjectStatus.label(project.status), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
        Card(
            Modifier.weight(.64f).fillMaxHeight().border(1.dp, StudioBorder, panelShape),
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
            shape = panelShape,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            if (selected == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("프로젝트를 선택해주세요.") }
            else WideProjectDetail(selected, cards, viewModel, onEdit, onOpenRecipe, onNewRecipe, onSettings, onPhotoAnalyze)
        }
    }
}

@Composable
private fun WideProjectDetail(
    project: ProjectEntity,
    cards: List<RecipeCardRow>,
    viewModel: AppViewModel,
    onEdit: (Long) -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onNewRecipe: (Long) -> Unit,
    onSettings: () -> Unit,
    onPhotoAnalyze: () -> Unit,
) {
    val linked by viewModel.projectRecipes(project.id).collectAsState(initial = emptyList())
    val usedPaints by viewModel.projectPaints(project.id).collectAsState(initial = emptyList())
    val timeline by viewModel.projectTimeline(project.id).collectAsState(initial = emptyList())
    val aiState by viewModel.aiState.collectAsState()
    var question by remember(project.id) { mutableStateOf("") }
    var addTimeline by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(.48f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PhotoPreview(project.photoUri, Modifier.fillMaxWidth().height(190.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(project.name, style = MaterialTheme.typography.headlineMedium)
                    Text("${project.modelName.ifBlank { "모델명 미입력" }} · ${ProjectStatus.label(project.status)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { onEdit(project.id) }) { Text("수정") }
            }
            if (project.memo.isNotBlank()) Text(project.memo, style = MaterialTheme.typography.bodyMedium)
            SectionTitle("사용 레시피", "${linked.size}개")
            linked.take(6).forEach { RecipeCard(it, onOpenRecipe) }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = { onNewRecipe(project.id) }, modifier = Modifier.weight(1f)) { Text("새 레시피") }
                Text("사용 도료 ${usedPaints.size}", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(Modifier.weight(.52f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("작업 Timeline", "${timeline.size}개 기록")
                Button(onClick = { addTimeline = true }) { Text("＋ 기록") }
            }
            if (timeline.isEmpty()) EmptyCard("준비, 서페이서, 본도색 등 진행 기록을 남겨보세요.")
            timeline.forEach { entry ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Row {
                            Text(ProjectStage.label(entry.stage), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            Text(formatDate(entry.date), style = MaterialTheme.typography.labelSmall)
                        }
                        if (entry.memo.isNotBlank()) Text(entry.memo, style = MaterialTheme.typography.bodySmall)
                        entry.photoUri?.let { PhotoPreview(it, Modifier.fillMaxWidth().height(84.dp).padding(top = 6.dp)) }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AI Project Assistant", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onSettings) { Text("설정") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("진행 요약", "색 추천", "다음 작업", "도색 상담").forEach { action -> FilterChip(false, { question = action }, { Text(action) }) }
            }
            OutlinedButton(onClick = onPhotoAnalyze, modifier = Modifier.fillMaxWidth()) { Text("사진 색상 분석") }
            OutlinedTextField(question, { question = it }, Modifier.fillMaxWidth(), placeholder = { Text("이 색에는 어떤 서페이서가 좋아?") })
            Button(
                onClick = {
                    val context = "프로젝트=${project.name}, 모델=${project.modelName}, 상태=${ProjectStatus.label(project.status)}, 메모=${project.memo}, 레시피=${linked.joinToString { it.name }}, 최근기록=${timeline.take(5).joinToString { ProjectStage.label(it.stage) + ':' + it.memo }}"
                    viewModel.requestProjectAdvice(question, context)
                },
                enabled = question.isNotBlank() && !aiState.loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("AI에게 묻기") }
            aiState.advice?.let {
                OutlinedCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        aiState.activeModelLabel?.let { label -> Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            aiState.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (addTimeline) TimelineEntryDialog(project.id, cards, viewModel) { addTimeline = false }
}

@Composable
internal fun TimelineEntryDialog(
    projectId: Long,
    cards: List<RecipeCardRow>,
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var stage by remember { mutableStateOf(ProjectStage.PREPARATION) }
    var memo by remember { mutableStateOf("") }
    var recipeId by remember { mutableStateOf<Long?>(null) }
    var photoUri by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) photoUri = copyPhotoToAppStorage(context, uri)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("작업 기록 추가") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("단계", style = MaterialTheme.typography.labelLarge)
                ProjectStage.entries.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        row.forEach { item -> FilterChip(stage == item, { stage = item }, { Text(ProjectStage.label(item)) }) }
                    }
                }
                OutlinedTextField(memo, { memo = it }, Modifier.fillMaxWidth(), label = { Text("메모") })
                OutlinedButton(onClick = { photoPicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text(if (photoUri == null) "사진 선택" else "사진 선택됨") }
                if (cards.isNotEmpty()) {
                    Text("사용 레시피 (선택)", style = MaterialTheme.typography.labelLarge)
                    cards.take(5).forEach { card ->
                        FilterChip(recipeId == card.id, { recipeId = if (recipeId == card.id) null else card.id }, { Text(card.name) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                viewModel.addTimelineEntry(
                    ProjectTimelineEntryEntity(projectId = projectId, date = System.currentTimeMillis(), stage = stage, memo = memo.trim(), photoUri = photoUri, recipeId = recipeId),
                )
                onDismiss()
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun StudioHeader(
    title: String,
    subtitle: String,
    action: String,
    onAction: () -> Unit,
    secondaryAction: String? = null,
    onSecondary: () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!secondaryAction.isNullOrBlank()) {
            OutlinedButton(
                onClick = onSecondary,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(7.dp),
                contentPadding = PaddingValues(horizontal = 9.dp),
            ) { Text(secondaryAction) }
            Spacer(Modifier.width(7.dp))
        }
        Button(
            onClick = onAction,
            modifier = Modifier.height(36.dp),
            shape = RoundedCornerShape(7.dp),
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) { Text(action) }
    }
}
