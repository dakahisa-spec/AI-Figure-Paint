package com.aifigurepaint.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Android Window Size Class breakpoints, with a Fold inner-screen two-pane threshold. */
internal enum class AppWindowWidthSizeClass { Compact, Medium, Expanded }

internal fun windowWidthSizeClass(width: Dp): AppWindowWidthSizeClass = when {
    width < 600.dp -> AppWindowWidthSizeClass.Compact
    width < 840.dp -> AppWindowWidthSizeClass.Medium
    else -> AppWindowWidthSizeClass.Expanded
}

internal fun supportsFoldTwoPane(width: Dp): Boolean =
    windowWidthSizeClass(width) != AppWindowWidthSizeClass.Compact && width >= 700.dp

@Composable
fun ColorSwatch(colorValue: Int, size: Dp = 48.dp, round: Boolean = false) {
    val shape = if (round) CircleShape else RoundedCornerShape(8.dp)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(Color(colorValue))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .28f), shape),
    )
}

@Composable
fun PhotoPreview(uriText: String?, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uriText) {
        value = uriText?.let { text ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(text)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = if (uri.scheme == "file") {
                            ImageDecoder.createSource(File(requireNotNull(uri.path)))
                        } else {
                            ImageDecoder.createSource(context.contentResolver, uri)
                        }
                        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            val largestSide = maxOf(info.size.width, info.size.height)
                            decoder.setTargetSampleSize((largestSide / 1600).coerceAtLeast(1))
                        }.asImageBitmap()
                    } else {
                        val stream = if (uri.scheme == "file") FileInputStream(File(requireNotNull(uri.path)))
                        else requireNotNull(context.contentResolver.openInputStream(uri))
                        stream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                    }
                }.getOrNull()
            }
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = "선택한 사진",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoStrip(uris: List<String>, modifier: Modifier = Modifier, itemSize: Dp = 112.dp) {
    if (uris.isEmpty()) return
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        uris.take(3).forEach { uri -> PhotoPreview(uri, Modifier.size(itemSize)) }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(if (subtitle.isNullOrBlank()) 22.dp else 34.dp).clip(RoundedCornerShape(4.dp)).background(StudioTeal))
        androidx.compose.foundation.layout.Column(Modifier.padding(start = 9.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = StudioNavy)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

fun persistPhotoPermission(context: Context, uri: Uri?) {
    if (uri == null) return
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

fun copyPhotoToAppStorage(context: Context, source: Uri): String? = runCatching {
    val directory = File(context.filesDir, "figure_photos").apply { mkdirs() }
    val target = File(directory, "${UUID.randomUUID()}.img")
    context.contentResolver.openInputStream(source).use { input ->
        requireNotNull(input) { "사진을 열 수 없습니다." }
        target.outputStream().use { output -> input.copyTo(output) }
    }
    Uri.fromFile(target).toString()
}.getOrNull()

fun parseHexColor(text: String, fallback: Int): Int {
    val clean = text.trim().removePrefix("#")
    return runCatching {
        when (clean.length) {
            6 -> (0xFF000000L or clean.toLong(16)).toInt()
            8 -> clean.toLong(16).toInt()
            else -> fallback
        }
    }.getOrDefault(fallback)
}

fun colorToHex(value: Int): String = "#%06X".format(value and 0xFFFFFF)

private val mlFormat = DecimalFormat("0.##")
fun formatMl(value: Double): String = mlFormat.format(value)

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private val inputDateFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun formatDate(epochMillis: Long): String = if (epochMillis <= 0) "날짜 미정" else runCatching {
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat)
}.getOrDefault("날짜 미정")

fun formatDateTime(epochMillis: Long): String = runCatching {
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat)
}.getOrDefault("")

fun dateToInput(epochMillis: Long): String = if (epochMillis <= 0) "" else runCatching {
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().format(inputDateFormat)
}.getOrDefault("")

fun parseDateInput(text: String): Long = runCatching {
    LocalDate.parse(text.trim(), inputDateFormat).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrDefault(0L)
