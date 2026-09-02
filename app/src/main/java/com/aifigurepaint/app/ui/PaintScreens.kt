package com.aifigurepaint.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aifigurepaint.app.AppViewModel
import com.aifigurepaint.app.ProductCodeSearchUiState
import com.aifigurepaint.app.ai.AiProductCodeResult
import com.aifigurepaint.app.data.PaintEntity
import com.aifigurepaint.app.data.StockLevel

private enum class PaintFilter(val label: String) {
    ALL("전체"), OWNED("보유"), NOT_OWNED("미보유"), FAVORITE("즐겨찾기"), RECENT("최근 사용")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PaintListScreen(
    paints: List<PaintEntity>,
    viewModel: AppViewModel,
    onAdd: () -> Unit,
    onScan: () -> Unit,
    onOpen: (Long) -> Unit,
    onSetStock: (PaintEntity, Int) -> Unit,
    onToggleFavorite: (PaintEntity) -> Unit,
) {
    val context = LocalContext.current
    val productCodeState by viewModel.productCodeSearchState.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(PaintFilter.ALL) }
    var brand by remember { mutableStateOf("전체 브랜드") }
    var brandMenu by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(PaintSortPreferences.read(context)) }
    var showBatchSearch by remember { mutableStateOf(false) }
    val brands = listOf("전체 브랜드") + paints.map { it.brand }.distinct().sorted()
    val matches = paints.filter { paint ->
        val q = query.trim()
        val queryMatch = q.isBlank() || listOf(paint.brand, paint.series, paint.productCode.orEmpty(), paint.name, paint.koreanName)
            .any { it.contains(q, ignoreCase = true) }
        val filterMatch = when (filter) {
            PaintFilter.ALL -> true
            PaintFilter.OWNED -> paint.owned
            PaintFilter.NOT_OWNED -> !paint.owned
            PaintFilter.FAVORITE -> paint.favorite
            PaintFilter.RECENT -> paint.lastUsedAt != null
        }
        val brandMatch = brand == "전체 브랜드" || paint.brand == brand
        queryMatch && filterMatch && brandMatch
    }
    val recentAware = if (filter == PaintFilter.RECENT) {
        matches.sortedByDescending { it.lastUsedAt ?: 0L }.take(20)
    } else {
        matches
    }
    val filtered = sortPaints(recentAware, sortMode)
    val missingCodes = paints.filter { it.productCode.isNullOrBlank() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("내 도료", style = MaterialTheme.typography.headlineMedium)
            Text("보유 ${paints.count { it.owned }} · 전체 ${paints.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onScan, modifier = Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(horizontal = 10.dp)) { Text("◎ AI 촬영") }
                Button(onClick = onAdd, modifier = Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(horizontal = 13.dp)) { Text("＋ 추가") }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            placeholder = { Text("도료명 · 한글명 · 코드 · 브랜드 검색") },
            singleLine = true,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            PaintFilter.entries.forEach { item ->
                FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item.label) })
            }
            Box {
                FilterChip(selected = brand != "전체 브랜드", onClick = { brandMenu = true }, label = { Text(brand) })
                DropdownMenu(expanded = brandMenu, onDismissRequest = { brandMenu = false }) {
                    brands.forEach { item ->
                        DropdownMenuItem(text = { Text(item) }, onClick = { brand = item; brandMenu = false })
                    }
                }
            }
            PaintSortMenu(sortMode) { selected ->
                sortMode = selected
                PaintSortPreferences.save(context, selected)
            }
            if (missingCodes.isNotEmpty()) {
                FilterChip(
                    selected = false,
                    onClick = {
                        viewModel.clearProductCodeSearch()
                        showBatchSearch = true
                        viewModel.searchProductCodes(missingCodes.take(5))
                    },
                    label = { Text("미확인 번호 찾기") },
                )
            }
        }
        if (filtered.isEmpty()) {
            EmptyCard("조건에 맞는 도료가 없습니다.")
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (supportsFoldTwoPane(maxWidth)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        gridItems(filtered, key = { it.id }) { PaintCard(it, onOpen, onSetStock, onToggleFavorite) }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        items(filtered, key = { it.id }) { PaintCard(it, onOpen, onSetStock, onToggleFavorite) }
                    }
                }
            }
        }
    }
    if (showBatchSearch) {
        ProductCodeSearchDialog(
            paints = missingCodes.take(5),
            state = productCodeState,
            onSearch = { viewModel.searchProductCodes(missingCodes.take(5)) },
            onApply = { selected ->
                viewModel.applyProductCodes(selected) {
                    viewModel.clearProductCodeSearch()
                    showBatchSearch = false
                }
            },
            onDismiss = {
                viewModel.clearProductCodeSearch()
                showBatchSearch = false
            },
        )
    }
}

@Composable
private fun PaintCard(
    paint: PaintEntity,
    onOpen: (Long) -> Unit,
    onSetStock: (PaintEntity, Int) -> Unit,
    onToggleFavorite: (PaintEntity) -> Unit,
) {
    var stockMenu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, StudioBorder, shape).clickable { onOpen(paint.id) },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorSwatch(paint.colorValue, 42.dp)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(paint.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val subName = paint.koreanName.ifBlank { paint.series }
                Text(subName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(
                    listOf(paint.brand, paint.productCode?.takeIf { it.isNotBlank() } ?: "상품번호 미확인").joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            TextButton(onClick = { onToggleFavorite(paint) }, contentPadding = PaddingValues(4.dp), modifier = Modifier.size(48.dp)) {
                Text(if (paint.favorite) "★" else "☆", color = if (paint.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                TextButton(onClick = { stockMenu = true }, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp)) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(StockLevel.label(paint.stockLevel), style = MaterialTheme.typography.labelMedium, color = if (paint.owned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(StockLevel.percentLabel(paint.stockLevel), style = MaterialTheme.typography.labelSmall)
                    }
                }
                DropdownMenu(expanded = stockMenu, onDismissRequest = { stockMenu = false }) {
                    StockLevel.entries.forEach { level ->
                        DropdownMenuItem(
                            text = { Text("${StockLevel.label(level)} · ${StockLevel.percentLabel(level)}") },
                            onClick = { onSetStock(paint, level); stockMenu = false },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PaintEditorScreen(paint: PaintEntity?, viewModel: AppViewModel, onBack: () -> Unit) {
    val productCodeState by viewModel.productCodeSearchState.collectAsState()
    var brand by remember(paint?.id) { mutableStateOf(paint?.brand ?: "Mr.Color") }
    var series by remember(paint?.id) { mutableStateOf(paint?.series ?: "Mr.Color") }
    var code by remember(paint?.id) { mutableStateOf(paint?.productCode.orEmpty()) }
    var name by remember(paint?.id) { mutableStateOf(paint?.name.orEmpty()) }
    var koreanName by remember(paint?.id) { mutableStateOf(paint?.koreanName.orEmpty()) }
    var hex by remember(paint?.id) { mutableStateOf(colorToHex(paint?.colorValue ?: 0xFFDDDDDD.toInt())) }
    var stockLevel by remember(paint?.id) { mutableStateOf(paint?.stockLevel ?: StockLevel.EMPTY) }
    var favorite by remember(paint?.id) { mutableStateOf(paint?.favorite ?: false) }
    var memo by remember(paint?.id) { mutableStateOf(paint?.memo.orEmpty()) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var showProductCodeSearch by remember { mutableStateOf(false) }
    val selectedColor = parseHexColor(hex, paint?.colorValue ?: 0xFFDDDDDD.toInt())

    Scaffold(topBar = { EditorHeader(if (paint == null) "도료 추가" else "도료 수정", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp, vertical = 10.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ColorSwatch(selectedColor, 64.dp)
                Column(Modifier.weight(1f)) {
                    Text("도료 정보", style = MaterialTheme.typography.titleLarge)
                    Text("색상과 재고 상태를 기록합니다", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { favorite = !favorite }) { Text(if (favorite) "★ 즐겨찾기" else "☆ 즐겨찾기") }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (supportsFoldTwoPane(maxWidth)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(brand, { brand = it }, Modifier.weight(1f), label = { Text("브랜드") }, singleLine = true)
                        OutlinedTextField(series, { series = it }, Modifier.weight(1f), label = { Text("시리즈") }, singleLine = true)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(brand, { brand = it }, Modifier.fillMaxWidth(), label = { Text("브랜드") }, singleLine = true)
                        OutlinedTextField(series, { series = it }, Modifier.fillMaxWidth(), label = { Text("시리즈") }, singleLine = true)
                    }
                }
            }
            OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth(), label = { Text("제품 코드") }, singleLine = true)
            if (paint != null) {
                OutlinedButton(
                    onClick = {
                        viewModel.clearProductCodeSearch()
                        showProductCodeSearch = true
                        viewModel.searchProductCodes(listOf(paint.copy(productCode = code.trim().ifBlank { null })))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (code.isBlank()) "AI로 상품번호 찾기" else "AI 검색 결과와 현재 번호 비교") }
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("제품명") }, singleLine = true)
            OutlinedTextField(koreanName, { koreanName = it }, Modifier.fillMaxWidth(), label = { Text("한글 이름") }, singleLine = true)
            OutlinedTextField(
                hex, { hex = it }, Modifier.fillMaxWidth(), label = { Text("대표 색상 HEX") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            SectionTitle("재고 상태", "정확한 ml 대신 빠른 단계로 관리")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                StockLevel.entries.forEach { level ->
                    FilterChip(selected = stockLevel == level, onClick = { stockLevel = level }, label = { Text(StockLevel.label(level)) })
                }
            }
            OutlinedTextField(memo, { memo = it }, Modifier.fillMaxWidth().height(96.dp), label = { Text("메모") })
            Button(
                onClick = {
                    viewModel.savePaint(
                        PaintEntity(
                            id = paint?.id ?: 0,
                            brand = brand.trim(),
                            series = series.trim(),
                            productCode = code.trim().ifBlank { null },
                            name = name.trim(),
                            koreanName = koreanName.trim(),
                            colorValue = selectedColor,
                            owned = stockLevel > 0,
                            stockLevel = stockLevel,
                            favorite = favorite,
                            lastUsedAt = paint?.lastUsedAt,
                            memo = memo.trim(),
                            createdAt = paint?.createdAt ?: System.currentTimeMillis(),
                        ),
                        onSaved = onBack,
                    )
                },
                enabled = name.isNotBlank() && brand.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("저장") }
            if (paint != null) {
                OutlinedButton(onClick = { deleteConfirm = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("도료 삭제", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
    if (deleteConfirm && paint != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("도료 삭제") },
            text = { Text("${paint.productCode.orEmpty()} ${paint.name}을(를) 삭제할까요?") },
            confirmButton = { TextButton(onClick = { viewModel.deletePaint(paint, onBack) }) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("취소") } },
        )
    }
    if (showProductCodeSearch && paint != null) {
        val searchPaint = paint.copy(
            brand = brand.trim(),
            series = series.trim(),
            productCode = code.trim().ifBlank { null },
            name = name.trim(),
            koreanName = koreanName.trim(),
            colorValue = selectedColor,
            memo = memo.trim(),
        )
        ProductCodeSearchDialog(
            paints = listOf(searchPaint),
            state = productCodeState,
            onSearch = { viewModel.searchProductCodes(listOf(searchPaint)) },
            onApply = { selected ->
                selected[paint.id]?.let { foundCode ->
                    viewModel.applyProductCode(searchPaint, foundCode) {
                        code = foundCode
                        viewModel.clearProductCodeSearch()
                        showProductCodeSearch = false
                    }
                }
            },
            onDismiss = {
                viewModel.clearProductCodeSearch()
                showProductCodeSearch = false
            },
        )
    }
}

@Composable
internal fun PaintSortMenu(mode: PaintSortMode, onSelected: (PaintSortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = mode != PaintSortMode.DEFAULT,
            onClick = { expanded = true },
            label = { Text("정렬 · ${mode.label}") },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PaintSortMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(if (option == mode) "✓ ${option.label}" else option.label) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun ProductCodeSearchDialog(
    paints: List<PaintEntity>,
    state: ProductCodeSearchUiState,
    onSearch: () -> Unit,
    onApply: (Map<Long, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedCodes by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    LaunchedEffect(state.results) {
        selectedCodes = state.results.mapNotNull { result ->
            result.candidates.firstOrNull()?.code?.let { result.paintId to it }
        }.toMap()
    }
    val confirmed = state.results.count { it.candidates.isNotEmpty() }
    val unresolved = state.results.count { it.candidates.isEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (paints.size == 1) "AI 상품번호 검색 결과" else "미확인 상품번호 찾기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    "제조사 공식 자료를 우선 검색하며 결과는 적용 전까지 저장되지 않습니다. 한 번에 최대 5개를 확인합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.loading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("인터넷에서 공식 상품번호를 확인하는 중…")
                    }
                }
                state.activeModelLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                if (state.results.isNotEmpty()) {
                    Text("확인됨 $confirmed · 확인 필요 $unresolved", style = MaterialTheme.typography.titleSmall)
                    state.results.forEach { result ->
                        val paint = paints.firstOrNull { it.id == result.paintId } ?: return@forEach
                        ProductCodeResultRow(
                            paint = paint,
                            result = result,
                            selectedCode = selectedCodes[result.paintId],
                            onSelected = { code -> selectedCodes = selectedCodes + (result.paintId to code) },
                        )
                    }
                }
                state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(selectedCodes) }, enabled = selectedCodes.isNotEmpty() && !state.loading) {
                Text(if (paints.size == 1) "선택 번호 적용" else "확인된 항목 적용")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSearch, enabled = !state.loading) { Text("다시 검색") }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}

@Composable
private fun ProductCodeResultRow(
    paint: PaintEntity,
    result: AiProductCodeResult,
    selectedCode: String?,
    onSelected: (String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f))) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorSwatch(paint.colorValue, 32.dp)
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(paint.name, style = MaterialTheme.typography.titleSmall)
                    Text("${paint.brand} · ${paint.series}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (result.candidates.isEmpty()) {
                Text(result.note.ifBlank { "확인할 수 없음" }, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            } else {
                result.candidates.forEach { candidate ->
                    FilterChip(
                        selected = selectedCode == candidate.code,
                        onClick = { onSelected(candidate.code) },
                        label = { Text("${candidate.code} · ${confidenceLabel(candidate.confidence)}") },
                    )
                    Text(
                        listOf(candidate.sourceType, candidate.evidence).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (result.note.isNotBlank()) Text(result.note, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun confidenceLabel(value: String): String = when (value) {
    "HIGH" -> "신뢰도 높음"
    "MEDIUM" -> "신뢰도 보통"
    else -> "사용자 확인 필요"
}

@Composable
internal fun EditorHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).border(1.dp, StudioBorder)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) { Text("‹ 뒤로", color = StudioNavy) }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = StudioNavy)
    }
}
