package com.deskpet.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deskpet.data.model.PetInfo
import com.deskpet.data.model.PetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val SCALE_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.5f, 2f, 2.5f, 3f)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PetListScreen(
    petList: List<PetInfo>,
    activePetId: String?,
    isOverlayRunning: Boolean,
    curScale: Float,
    onPetSelected: (String) -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onImportClick: () -> Unit,
    onDeletePet: (String) -> Unit,
    onScaleChange: (Float) -> Unit,
    onExportPet: (PetInfo) -> Unit = {},
    onBackupAll: () -> Unit = {},
    onShowHelp: () -> Unit = {}
) {
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    deleteTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除宠物") },
            text = { Text("确定删除？") },
            confirmButton = {
                TextButton({ onDeletePet(id); deleteTarget = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ deleteTarget = null }) { Text("取消") } }
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("宠物栏", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onImportClick) {
                    Icon(Icons.Default.Add, "导入", tint = MaterialTheme.colorScheme.onSurface)
                }
                if (activePetId != null) {
                    if (isOverlayRunning)
                        Button(onClick = onStopOverlay,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text("关闭") }
                    else
                        Button(onClick = onStartOverlay,
                            shape = RoundedCornerShape(20.dp)
                        ) { Text("开启") }
                }
                IconButton(onClick = onShowHelp) {
                    Text("?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, "更多", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("💾 备份全部") },
                            onClick = { menuExpanded = false; onBackupAll() },
                            leadingIcon = { Icon(Icons.Default.Share, null) }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (activePetId != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SCALE_OPTIONS.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { s ->
                            FilterChip(
                                selected = curScale == s,
                                onClick = { onScaleChange(s) },
                                label = { Text(if (s == 1f) "100% 原生" else "${(s * 100).toInt()}%", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size < 3) repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            PetResolutionInfo(petList.find { it.id == activePetId }, curScale, isOverlayRunning)
            Spacer(Modifier.height(8.dp))
        }

        if (petList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有宠物", style = MaterialTheme.typography.bodyLarge)
                    Text("点击「导入」添加\n长按宠物可删除",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                contentPadding = PaddingValues(0.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(petList, key = { it.id }, contentType = { it.type }) { pet ->
                    PetGridCard(
                        pet = pet,
                        isActive = pet.id == activePetId,
                        onClick = { onPetSelected(pet.id) },
                        onLongClick = { deleteTarget = pet.id },
                        onExport = { onExportPet(pet) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PetGridCard(
    pet: PetInfo,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onExport: () -> Unit = {}
) {
    val scale by animateFloatAsState(
        if (isActive) 1.04f else 1f,
        spring(dampingRatio = 0.6f, stiffness = 400f)
    )
    Card(
        modifier = Modifier.aspectRatio(1f).graphicsLayer {
            scaleX = scale; scaleY = scale
        }.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 6.dp else 1.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                PetPreview(pet)
                Spacer(Modifier.height(2.dp))
                Text(pet.displayName, style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onExport, modifier = Modifier.align(Alignment.BottomEnd).size(28.dp)) {
                Icon(Icons.Default.Share, "导出",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PetPreview(pet: PetInfo) {
    var thumb by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pet.spritesheetPath) {
        if (pet.type == PetType.CODEX_PET) thumb = withContext(Dispatchers.IO) { loadOrCreateThumb(pet) }
    }
    if (thumb != null) Image(thumb!!.asImageBitmap(), pet.displayName, Modifier.fillMaxSize(0.75f), contentScale = ContentScale.Fit)
    else Text(if (pet.type == PetType.CODEX_PET) "🎨" else "📷", style = MaterialTheme.typography.headlineMedium)
}

private fun loadOrCreateThumb(pet: PetInfo): Bitmap? {
    val cache = java.io.File(pet.spritesheetPath).parentFile?.resolve("thumb_atlas_v2.png") ?: return null
    if (cache.exists()) return BitmapFactory.decodeFile(cache.absolutePath)
    java.io.File(pet.spritesheetPath).parentFile?.listFiles { f -> f.name.startsWith("thumb") && f.name != "thumb_atlas_v2.png" }?.forEach { it.delete() }
    return try {
        val parser = com.deskpet.engine.SpritesheetParser()
        val config = pet.petJsonPath?.let { parser.parse(it) } ?: return null
        val t = try {
            parser.extractFrame(config, pet.spritesheetPath, com.deskpet.data.model.PetAnimationState.IDLE, 0)
        } finally { parser.release() }
        t?.let { java.io.FileOutputStream(cache).use { o -> it.compress(Bitmap.CompressFormat.PNG, 80, o) } }; t
    } catch (e: Exception) { Log.w("PetListScreen", "thumbnail failed", e); null }
}


@Composable
private fun PetResolutionInfo(pet: PetInfo?, scale: Float, overlayRunning: Boolean) {
    var native by remember(pet?.spritesheetPath) { mutableStateOf<androidx.compose.ui.unit.IntSize?>(null) }
    val measured by com.deskpet.overlay.OverlayDiagnostics.pixels.collectAsState()
    LaunchedEffect(pet?.spritesheetPath, pet?.petJsonPath) {
        native = withContext(Dispatchers.IO) {
            if (pet?.type != PetType.CODEX_PET) return@withContext null
            val parser = com.deskpet.engine.SpritesheetParser()
            val config = pet.petJsonPath?.let { parser.parse(it) } ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
            BitmapFactory.decodeFile(pet.spritesheetPath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null
            else androidx.compose.ui.unit.IntSize(bounds.outWidth / config.columns, bounds.outHeight / config.rows)
        }
    }
    native?.let { source ->
        val actual = measured?.takeIf { overlayRunning && it.source == source }
        val expected = androidx.compose.ui.unit.IntSize((source.width * scale).toInt().coerceAtLeast(32),
            (source.height * scale).toInt().coerceAtLeast(32))
        val displayed = actual?.displayed ?: expected
        val prefix = if (actual != null) "悬浮窗实测" else "预计显示"
        val enlarged = displayed.width > source.width || displayed.height > source.height
        Text("原始每帧 ${source.width}×${source.height} px · $prefix ${displayed.width}×${displayed.height} px",
            style = MaterialTheme.typography.bodySmall)
        if (actual != null) Text("屏幕密度 ${actual.density} px/dp · 位置与大小设置继续保留",
            style = MaterialTheme.typography.bodySmall)
        Text(if (enlarged) "当前超过原生分辨率；放大不增加细节。建议点「100% 原生」或75%。"
             else "100%为逐像素原图；75%适合稍小显示。素材细节上限取决于原始每帧分辨率。",
            style = MaterialTheme.typography.bodySmall)
    }
}
