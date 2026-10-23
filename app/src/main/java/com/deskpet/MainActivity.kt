package com.deskpet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deskpet.data.FileManager
import com.deskpet.data.PetRepository
import com.deskpet.data.SamplePetLoader
import com.deskpet.overlay.PetOverlayService
import com.deskpet.ui.ImportScreen
import com.deskpet.ui.PetListScreen
import com.deskpet.ui.theme.DeskPetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DeskPetTheme { App((application as DeskPetApp).petRepository) } }
    }

    @Composable
    private fun App(repo: PetRepository) {
        val sc = rememberCoroutineScope()
        var showImport by remember { mutableStateOf(false) }
        val overlayOn by PetOverlayService.running.collectAsStateWithLifecycle()
        var scale by remember { mutableFloatStateOf(1f) }
        var pendingStart by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
        var showHelp by remember { mutableStateOf(false) }
        val petList by repo.getAllPets().collectAsStateWithLifecycle(emptyList())
        val activeId by repo.getActivePetId().collectAsStateWithLifecycle(null)
        val isFirstLaunch by repo.isFirstLaunch().collectAsStateWithLifecycle(true)
        LaunchedEffect(Unit) { SamplePetLoader.loadSamplePets(this@MainActivity, repo) }
        LaunchedEffect(petList, activeId) { scale = petList.find { it.id == activeId }?.scale ?: 1f }
        val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) Toast.makeText(this@MainActivity, "通知未开启，桌宠仍可运行；可在设置中开启运行通知", Toast.LENGTH_LONG).show()
        }
        val requestNotification = {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 从设置页返回后自动重试开启
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle, pendingStart, activeId) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && pendingStart && Settings.canDrawOverlays(this@MainActivity)) {
                    pendingStart = false
                    activeId?.let {
                        ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, PetOverlayService::class.java)
                            .apply { putExtra(PetOverlayService.EXTRA_PET_ID, it) })
                    }
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }

        if (isFirstLaunch || showHelp) {
            WelcomeGuide(requestNotification) { sc.launch { repo.setFirstLaunchDone(); showHelp = false } }
        }

        if (showImport) {
            ImportScreen(repo, { showImport = false }, { showImport = false })
        } else {
            PetListScreen(
                petList = petList,
                activePetId = activeId,
                isOverlayRunning = overlayOn,
                curScale = scale,
                onPetSelected = { sc.launch { repo.setActivePet(it) } },
                onStartOverlay = {
                    requestNotification()
                    activeId?.let {
                        if (Settings.canDrawOverlays(this@MainActivity)) {
                            ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, PetOverlayService::class.java)
                                .apply { putExtra(PetOverlayService.EXTRA_PET_ID, it) })
                        } else {
                            pendingStart = true
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${this@MainActivity.packageName}"))
                            startActivity(intent)
                        }
                    }
                },
                onStopOverlay = {
                    stopService(Intent(this@MainActivity, PetOverlayService::class.java))
                },
                onImportClick = { showImport = true },
                onDeletePet = { id ->
                    sc.launch {
                        val pet = repo.getPet(id)
                        repo.deletePet(id)
                        pet?.let { FileManager(this@MainActivity).deletePet(it) }
                    }
                },
                onScaleChange = { s -> scale = s; activeId?.let { sc.launch { repo.setScale(it, s) } } },
                onExportPet = { pet ->
                    sc.launch {
                        val fm = FileManager(this@MainActivity)
                        val zip = withContext(Dispatchers.IO) { fm.exportPetToZip(pet) }
                        if (zip != null) fm.shareZip(zip)
                        else Toast.makeText(this@MainActivity, "导出失败", Toast.LENGTH_SHORT).show()
                    }
                },
                onBackupAll = {
                    sc.launch {
                        try {
                            val fm = FileManager(this@MainActivity)
                            val zip = withContext(Dispatchers.IO) { fm.exportAllToZip(petList) }
                            if (zip != null) fm.shareZip(zip)
                            else Toast.makeText(this@MainActivity, "备份失败", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Backup crash", e)
                            Toast.makeText(this@MainActivity, "出错：${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onShowHelp = { showHelp = true }
            )
        }
    }

    private fun openSettings(action: String, packageUri: Boolean) {
        val intent = Intent(action).apply {
            if (packageUri) data = Uri.parse("package:$packageName")
            if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        try { startActivity(intent) }
        catch (_: android.content.ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    @Composable
    private fun WelcomeGuide(requestNotification: () -> Unit, onDismiss: () -> Unit) {
        val ctx = LocalContext.current
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("白发 GPT 龙娘桌宠", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    // 权限
                    Text("📌 权限说明", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text("点击「开启」后如提示需悬浮窗权限，请允许。各手机路径不同，通常：设置 → 应用 → 桌面宠物 → 显示在其他应用上层")

                    Spacer(Modifier.height(12.dp))

                    TextButton(onClick = { openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true) }) { Text("开启悬浮窗权限") }
                    TextButton(onClick = requestNotification) { Text("允许通知（安卓13及以上）") }
                    TextButton(onClick = { openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS, false) }) { Text("通知设置") }
                    Text("后台运行：允许应用后台活动，电池设置选择「不限制」。部分手机还需要开启自启动，并在最近任务中锁定应用。系统强行停止后需重新打开并点击开启。")
                    TextButton(onClick = { openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, false) }) { Text("后台电池设置") }
                    TextButton(onClick = { openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true) }) { Text("应用详情 / 后台运行设置") }

                    // 操作
                    Text("🖐 操作方式", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text("• 单击悬浮窗宠物 = 招手\n• 长按拖拽 = 移动位置\n• 拖拽方向 = 切换跑动/跳跃动画\n• 双指捏合 = 缩放\n• 界面大小按钮 = 精确控制大小")

                    Spacer(Modifier.height(12.dp))

                    // 导入
                    Text("📦 导入 Codex 宠物", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text("完整构建内置白发 GPT 龙娘，首次安装默认选中，无需导入。你也可以导入更多：\n1. 点击底部「导入」按钮\n2. 选择「🎨 导入 Codex Pet 素材包」\n3. 选取下载的 .zip 文件")

                    Spacer(Modifier.height(8.dp))
                    Text("从哪里下载 Codex 宠物？", fontWeight = FontWeight.Bold)
                    Text(buildAnnotatedString {
                        append("👉 ")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                            append("codexpet.xyz")
                        }
                    }, modifier = Modifier.clickable {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://codexpet.xyz/")))
                    })
                    Text(buildAnnotatedString {
                        append("👉 ")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                            append("github.com/Codexdei/petdex")
                        }
                    }, modifier = Modifier.clickable {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Codexdei/petdex")))
                    })

                    Spacer(Modifier.height(12.dp))

                    // 提示
                    Text("💡 小提示", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text("• 每只宠物的位置和大小独立记忆\n• 长按宠物栏卡片可删除宠物\n• 右上角菜单可备份全部宠物\n• 换手机时用备份恢复功能迁移\n• 宠物不含网络权限，纯本地运行")
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
        )
    }
}

