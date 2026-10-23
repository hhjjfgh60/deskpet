package com.deskpet.overlay

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.deskpet.data.PetRepository
import com.deskpet.data.model.DragDirection
import com.deskpet.data.model.PetAnimationState
import com.deskpet.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect

class PetOverlayService : LifecycleService(), ViewModelStoreOwner, SavedStateRegistryOwner {

    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry = SavedStateRegistryController.create(this).also { it.performRestore(null) }.savedStateRegistry

    private lateinit var wm: WindowManager
    private var cv: ComposeView? = null
    private lateinit var repo: PetRepository
    private lateinit var engine: PetEngine
    private lateinit var anim: AnimationController
    private lateinit var gesture: GestureHandler
    private lateinit var parser: SpritesheetParser
    private var auto: AutonomousBehavior? = null
    private var petId: String? = null
    private var job: Job? = null
    private var saveJob: Job? = null
    private var wx = 0f
    private var wy = 200f
    private var savedScale = 1f
    private val sc = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: android.content.Context?, i: Intent?) {
            if (::anim.isInitialized) when (i?.action) {
                Intent.ACTION_SCREEN_OFF -> anim.stop()
                Intent.ACTION_SCREEN_ON -> anim.start()
            }
        }
    }

    private fun onDrag(dx: Float, dy: Float, d: DragDirection) {
        wx += dx
        wy += dy
        updatePosition()
        auto?.onUserTouch()
        scheduleSave()
        if (anim.isCodexPet() && anim.currentState.value != gesture.mapDirectionToState(d)) {
            anim.switchState(gesture.mapDirectionToState(d))
        }
    }

    private fun onTap() {
        if (anim.isCodexPet()) {
            anim.switchState(PetAnimationState.WAVING)
        } else {
            bounce()
        }
        auto?.onUserTouch()
    }

    private fun onScale(zoom: Float) {
        val ns = (savedScale * zoom).coerceIn(0.3f, 5f)
        anim.setScale(ns)
        savedScale = ns
        updatePosition()
        scheduleSave()
    }

    private fun scheduleSave() {
        val id = petId ?: return
        val x = wx; val y = wy; val scale = savedScale
        saveJob?.cancel()
        saveJob = sc.launch {
            delay(250)
            repo.savePosition(id, x, y, scale)
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        repo = (application as com.deskpet.DeskPetApp).petRepository
        parser = SpritesheetParser()
        engine = PetEngine(parser, GifRenderer())
        anim = AnimationController(engine, sc)
        gesture = GestureHandler(
            onDrag = ::onDrag,
            onTap = ::onTap,
            isCodexPet = anim::isCodexPet,
            onScale = ::onScale
        )
        androidx.core.content.ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        startForeground()
        sc.launch {
            repo.getActivePet().collect { pet ->
                if (pet == null && cv != null) {
                    stopSelf()
                } else if (pet != null && cv != null) {
                    if (pet.id != petId) {
                        petId?.let { repo.savePosition(it, wx, wy, savedScale) }
                        show(pet.id)
                    } else if (pet.scale != savedScale) {
                        saveJob?.cancel()
                        savedScale = pet.scale.coerceIn(0.3f, 5f)
                        anim.setScale(savedScale)
                        updatePosition()
                    }
                }
            }
        }
    }

    private fun startForeground() {
        try {
            val nm = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(android.app.NotificationChannel(
                    CHANNEL_ID, "桌面宠物", android.app.NotificationManager.IMPORTANCE_LOW
                ).apply { description = "宠物悬浮窗运行中" })
            }
            val openApp = PendingIntent.getActivity(this, 0,
                Intent(this, com.deskpet.MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val stop = PendingIntent.getService(this, 1,
                Intent(this, PetOverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val ntf = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("桌面宠物")
                .setContentText("桌宠运行中 · 点击返回设置")
                .setContentIntent(openApp)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭桌宠", stop)
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFY_ID, ntf, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFY_ID, ntf)
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed", e)
            Toast.makeText(this@PetOverlayService, "前台服务失败: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        super.onStartCommand(i, f, s)
        if (i?.action == ACTION_STOP || !android.provider.Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        i?.getStringExtra(EXTRA_PET_ID)?.let { id ->
            job?.cancel()
            job = sc.launch {
                try {
                    show(id)
                } catch (e: Exception) {
                    Log.e(TAG, "show failed", e)
                    Toast.makeText(this@PetOverlayService, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
                    stopSelf()
                }
            }
        } ?: sc.launch {
            try {
                val id = repo.getActivePetId().first()
                if (id != null) show(id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to recover pet on restart", e)
                Toast.makeText(this@PetOverlayService, "恢复失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        return START_STICKY
    }

    private fun persistSnapshot() {
        val id = petId ?: return
        val repository = repo
        val x = wx; val y = wy; val scale = savedScale
        saveJob?.cancel()
        (application as com.deskpet.DeskPetApp).persistenceScope.launch {
            try { repository.savePosition(id, x, y, scale) }
            catch (error: Exception) { Log.w(TAG, "Failed to save overlay snapshot", error) }
        }
    }

    override fun onDestroy() {
        persistSnapshot()
        running.value = false
        removeOverlayView()
        viewModelStore.clear()
        if (::anim.isInitialized) anim.stop()
        auto?.stop()
        if (::parser.isInitialized) parser.release()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister screen receiver", e)
        }
        sc.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(r: Intent?) {
        super.onTaskRemoved(r)
        persistSnapshot()
        // Keep the foreground overlay alive when the activity is swiped away.
    }
    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        val p = Point()
        wm.defaultDisplay.getSize(p)
        if (wx > p.x || wy > p.y) {
            wx = 100f
            wy = 100f
        }
        clampPosition()
        updatePosition()
    }

    private suspend fun show(id: String) {
        saveJob?.cancel()
        val info = withContext(Dispatchers.IO) { repo.getPet(id) } ?: return
        petId = id
        wx = info.positionX
        wy = info.positionY
        if (!anim.reload(info)) {
            Toast.makeText(this, "宠物素材无法读取，请检查素材文件", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        anim.setScale(info.scale)
        savedScale = info.scale
        anim.start()
        add()
        auto?.stop()
        auto = AutonomousBehavior(anim, sc)
        auto?.start()
    }

    private fun add() {
        if (!::wm.isInitialized) return
        if (!android.provider.Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = android.net.Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            return
        }
        removeOverlayView()
        cv = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PetOverlayService)
            setViewTreeViewModelStoreOwner(this@PetOverlayService)
            setViewTreeSavedStateRegistryOwner(this@PetOverlayService)
            setContent { PetOverlayRenderer(anim, gesture.modifier) }
        }
        clampPosition()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = wx.toInt()
            y = wy.toInt()
        }
        try {
            wm.addView(cv, params)
            running.value = true
        } catch (e: Exception) {
            Log.e(TAG, "addView", e)
            stopSelf()
        }
    }

    private fun removeOverlayView() {
        cv?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove overlay view", e)
            }
        }
        cv = null
    }

    private fun updatePosition() {
        clampPosition()
        cv?.let { v ->
            (v.layoutParams as? WindowManager.LayoutParams)?.let { p ->
                p.x = wx.toInt()
                p.y = wy.toInt()
                try {
                    wm.updateViewLayout(v, p)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to update overlay position", e)
                }
            }
        }
    }

    private fun clampPosition() {
        val p = Point()
        wm.defaultDisplay.getSize(p)
        val w = anim.petWidth.value
        wx = wx.coerceIn((-w + 32).toFloat(), (p.x - 32).toFloat())
        wy = wy.coerceIn(0f, (p.y - 32).toFloat())
    }

    private fun bounce() {
        sc.launch {
            val o = wy
            wy = o - 20
            updatePosition()
            delay(100)
            wy = o
            updatePosition()
        }
    }

    companion object {
        val running = kotlinx.coroutines.flow.MutableStateFlow(false)
        private const val ACTION_STOP = "com.deskpet.STOP"
        const val EXTRA_PET_ID = "pet_id"; private const val TAG = "PO"; private const val NOTIFY_ID = 1; private const val CHANNEL_ID = "deskpet_fg" }
}

