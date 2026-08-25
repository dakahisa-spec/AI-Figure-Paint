package com.aifigurepaint.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun PhotoColorAnalyzerScreen(
    onBack: () -> Unit,
    onUseColor: (String) -> Unit,
) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var zoom by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var sampledColor by remember { mutableStateOf(AndroidColor.rgb(128, 128, 128)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedUri = uri
        zoom = 1f
        offset = Offset.Zero
    }

    LaunchedEffect(selectedUri) {
        bitmap = selectedUri?.let { uri ->
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }
        }
    }
    LaunchedEffect(bitmap, zoom, offset, canvasSize) {
        val image = bitmap ?: return@LaunchedEffect
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return@LaunchedEffect
        sampledColor = sampleCenterArea(image, canvasSize, zoom, offset)
    }

    Scaffold(topBar = { EditorHeader("사진 색상 분석", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("사진을 확대·이동해 십자선에 목표 색상을 맞추세요.", style = MaterialTheme.typography.bodyMedium)
            Card(
                Modifier.fillMaxWidth().height(430.dp).onSizeChanged { canvasSize = it },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    Modifier.fillMaxSize().pointerInput(bitmap) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(1f, 8f)
                            offset += pan
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    val image = bitmap
                    if (image == null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("분석할 사진을 선택해주세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { picker.launch(arrayOf("image/*")) }) { Text("갤러리에서 선택") }
                        }
                    } else {
                        Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = "색상 분석 사진",
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = offset.x
                                translationY = offset.y
                            },
                            contentScale = ContentScale.Fit,
                        )
                        Crosshair()
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(68.dp).clip(CircleShape).background(Color(sampledColor)))
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(colorToHex(sampledColor), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "RGB ${AndroidColor.red(sampledColor)}, ${AndroidColor.green(sampledColor)}, ${AndroidColor.blue(sampledColor)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("십자선 주변 9×9 영역 평균", style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) { Text("다른 사진") }
                Button(onClick = { onUseColor(colorToHex(sampledColor)) }, enabled = bitmap != null, modifier = Modifier.weight(1f)) { Text("이 색으로 AI 조색") }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .65f))) {
                Text(
                    "사진의 색상은 조명, 카메라 화이트밸런스 및 촬영 환경에 따라 실제 색상과 다를 수 있습니다.",
                    Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Crosshair() {
    Canvas(Modifier.size(54.dp)) {
        val color = Color.White
        drawCircle(Color.Black.copy(alpha = .45f), radius = size.minDimension / 2)
        drawLine(color, Offset(size.width / 2, 4f), Offset(size.width / 2, size.height - 4f), strokeWidth = 2.4f)
        drawLine(color, Offset(4f, size.height / 2), Offset(size.width - 4f, size.height / 2), strokeWidth = 2.4f)
        drawCircle(color, radius = 5f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.4f))
    }
}

private fun sampleCenterArea(bitmap: Bitmap, canvas: IntSize, zoom: Float, offset: Offset): Int {
    val baseScale = min(canvas.width.toFloat() / bitmap.width, canvas.height.toFloat() / bitmap.height)
    val x = (bitmap.width / 2f - offset.x / (baseScale * zoom)).roundToInt().coerceIn(0, bitmap.width - 1)
    val y = (bitmap.height / 2f - offset.y / (baseScale * zoom)).roundToInt().coerceIn(0, bitmap.height - 1)
    var red = 0L
    var green = 0L
    var blue = 0L
    var count = 0
    for (py in (y - 4).coerceAtLeast(0)..(y + 4).coerceAtMost(bitmap.height - 1)) {
        for (px in (x - 4).coerceAtLeast(0)..(x + 4).coerceAtMost(bitmap.width - 1)) {
            val color = bitmap.getPixel(px, py)
            red += AndroidColor.red(color)
            green += AndroidColor.green(color)
            blue += AndroidColor.blue(color)
            count++
        }
    }
    return AndroidColor.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
}
