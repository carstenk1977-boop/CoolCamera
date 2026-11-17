package com.stylecam.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.os.Bundle
import com.stylecam.camera.CameraController
import com.stylecam.databinding.ActivityMainBinding
import com.stylecam.filters.FilterProcessor
import com.stylecam.filters.FilterRegistry
import com.stylecam.filters.FilterRenderer
import com.stylecam.filters.RailwayFilter
import com.stylecam.filters.StyleCamGLView
import com.stylecam.models.CustomPreset
import com.stylecam.models.FilterParams
import com.stylecam.models.FilterStyle
import com.stylecam.storage.MediaSaver
import com.stylecam.storage.PresetStore
import com.stylecam.storage.SettingsStore
import com.stylecam.utils.ImageUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.random.Random

/**
 * Thin coordinator: owns no image processing. Camera acquisition lives in camera/, the GPU
 * pipeline in filters/, persistence in storage/. This class only wires UI events to them.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var settings: SettingsStore
    private lateinit var presetStore: PresetStore
    private lateinit var camera: CameraController
    private lateinit var panel: ControlPanel

    private val renderer: FilterRenderer get() = b.glView.filterRenderer

    // ---- filter state ----
    private var processor: FilterProcessor = RailwayFilter
    private var params: FilterParams = RailwayFilter.defaultParams
    private val styleParams = HashMap<FilterStyle, FilterParams>()
    private val stylePresetKey = HashMap<FilterStyle, String?>()
    private var activePresetKey: String? = null            // "b:<name>" or "c:<id>"
    private val styleChips = HashMap<FilterStyle, TextView>()
    private val presetChips = ArrayList<Pair<String, TextView>>()

    // ---- modes ----
    private var importUri: Uri? = null
    private var splitOn = false
    private var cameraStarted = false

    private val rng = Random(System.nanoTime())
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onPermissionResult() }
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) enterImportMode(uri) }

    // ---------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        settings = SettingsStore(this)
        presetStore = PresetStore(this)
        camera = CameraController(this, renderer)
        camera.onBound = { updateCameraButtons() }

        applyInsets()
        setUpPanel()
        setUpGestures()
        setUpButtons()
        buildStyleCarousel()

        processor = FilterRegistry.byStyle(settings.lastStyle)
        params = processor.defaultParams
        activePresetKey = "b:" + processor.presets.first().name
        onFilterChanged(rebuildPanel = true)
        applySettings()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    importUri != null -> exitImportMode()
                    panel.isShowing -> panel.hide()
                    else -> finish()
                }
            }
        })

        startCameraIfPermitted(requestIfNeeded = true)
    }

    override fun onResume() {
        super.onResume()
        b.glView.onResume()
        // Returning from the system settings page after granting the permission there
        if (b.permissionPanel.visibility == View.VISIBLE) startCameraIfPermitted(requestIfNeeded = false)
    }

    override fun onPause() {
        b.glView.onPause()
        super.onPause()
    }

    // ---------------------------------------------------------------------------------------
    // Permissions
    // ---------------------------------------------------------------------------------------

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private val needsLegacyStorage get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P

    private fun startCameraIfPermitted(requestIfNeeded: Boolean) {
        if (has(Manifest.permission.CAMERA)) {
            b.permissionPanel.visibility = View.GONE
            if (!cameraStarted && importUri == null) {
                cameraStarted = true
                camera.start()
            }
        } else {
            b.permissionPanel.visibility = View.VISIBLE
            if (requestIfNeeded) permissionLauncher.launch(requiredPermissions())
        }
    }

    private fun requiredPermissions(): Array<String> {
        val list = arrayListOf(Manifest.permission.CAMERA)
        if (needsLegacyStorage) list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        return list.toTypedArray()
    }

    private fun onPermissionResult() {
        startCameraIfPermitted(requestIfNeeded = false)
        if (!has(Manifest.permission.CAMERA) && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            // "Don't ask again" - the only way left is the system settings page
            toast("Enable the camera permission in system settings")
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
    }

    // ---------------------------------------------------------------------------------------
    // Set-up
    // ---------------------------------------------------------------------------------------

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            b.topBar.setPadding(b.topBar.paddingLeft, bars.top + UiKit.dp(this, 8), b.topBar.paddingRight, b.topBar.paddingBottom)
            b.bottomStack.setPadding(0, b.bottomStack.paddingTop, 0, bars.bottom + UiKit.dp(this, 8))
            (b.fpsText.layoutParams as FrameLayout.LayoutParams).topMargin = bars.top + UiKit.dp(this, 64)
            (b.importBar.layoutParams as FrameLayout.LayoutParams).topMargin = bars.top + UiKit.dp(this, 64)
            b.fpsText.requestLayout()
            b.importBar.requestLayout()
            insets
        }
    }

    private fun setUpPanel() {
        panel = ControlPanel(this, b.controlPanel, object : ControlPanel.Callbacks {
            override fun onParamChanged(key: String, value: Float) {
                params = params.with(key, value)
                activePresetKey = null
                renderer.setFilter(processor, params)
                updateLabel()
                updatePresetSelection()
            }

            override fun onOptionSelected(index: Int) {
                params = processor.applyOption(params, index)
                activePresetKey = null
                onFilterChanged(rebuildPanel = false)
            }

            override fun onReset() {
                params = processor.defaultParams
                activePresetKey = "b:" + processor.presets.first().name
                onFilterChanged(rebuildPanel = false)
            }

            override fun onRandomise() {
                params = processor.randomise(rng)
                activePresetKey = null
                onFilterChanged(rebuildPanel = false)
            }

            override fun onSavePreset() = askSavePreset()
        })
    }

    private fun setUpGestures() {
        b.glView.gestures = object : StyleCamGLView.GestureCallbacks {
            override fun onTap(x: Float, y: Float) {
                if (panel.isShowing) { panel.hide(); return }
                if (importUri != null) return
                camera.focusAt(x, y, b.glView.width, b.glView.height)
                showFocusRing(x, y)
            }

            override fun onPinch(scaleFactor: Float) {
                if (importUri == null) camera.zoomBy(scaleFactor)
            }
        }
        renderer.statsListener = object : FilterRenderer.StatsListener {
            override fun onStats(fps: Int, gpuMs: Float, width: Int, height: Int, levelName: String) {
                runOnUiThread {
                    if (settings.showFps) {
                        b.fpsText.text = String.format(Locale.US, "%d fps  %.1f ms\n%dx%d  %s", fps, gpuMs, width, height, levelName)
                    }
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setUpButtons() {
        b.btnFlip.setOnClickListener { camera.switchCamera(); updateTorchIcon() }
        b.btnFlash.setOnClickListener {
            if (!camera.hasFlash) { toast("No flash on this camera"); return@setOnClickListener }
            camera.setTorch(!camera.isTorchOn())
            updateTorchIcon()
        }
        b.btnSettings.setOnClickListener { SettingsDialog.show(this, settings) { applySettings() } }
        b.btnGallery.setOnClickListener { pickImage.launch("image/*") }
        b.btnTune.setOnClickListener { if (processor.controls.isNotEmpty()) panel.toggle() }
        b.btnShutter.setOnClickListener { onShutter() }

        // Press-and-hold ORIGINAL: shows the unfiltered frame only while the finger is down
        b.btnHold.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { v.isPressed = true; renderer.setHoldOriginal(true) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.isPressed = false; renderer.setHoldOriginal(false) }
            }
            true
        }

        // Draggable ORIGINAL | FILTERED split view
        b.splitOverlay.onPositionChanged = { pos -> renderer.setSplit(true, pos) }
        b.btnSplit.setOnClickListener {
            splitOn = !splitOn
            b.splitOverlay.visibility = if (splitOn) View.VISIBLE else View.GONE
            if (splitOn) b.splitOverlay.reset()
            b.btnSplit.setColorFilter(if (splitOn) UiKit.AMBER else 0xFFFFFFFF.toInt())
            renderer.setSplit(splitOn, b.splitOverlay.position)
        }

        b.btnImportSave.setOnClickListener { exportImported() }
        b.btnImportClose.setOnClickListener { exitImportMode() }
        b.btnGrant.setOnClickListener { permissionLauncher.launch(requiredPermissions()) }
    }

    private fun applySettings() {
        renderer.setQuality(settings.quality)
        b.fpsText.visibility = if (settings.showFps) View.VISIBLE else View.GONE
    }

    private fun updateCameraButtons() {
        b.btnFlip.visibility = if (camera.hasFrontCamera && camera.hasBackCamera) View.VISIBLE else View.INVISIBLE
        b.btnFlash.visibility = if (camera.hasFlash) View.VISIBLE else View.INVISIBLE
        updateTorchIcon()
    }

    private fun updateTorchIcon() {
        if (camera.isTorchOn()) b.btnFlash.setColorFilter(UiKit.AMBER) else b.btnFlash.clearColorFilter()
    }

    // ---------------------------------------------------------------------------------------
    // Styles, presets
    // ---------------------------------------------------------------------------------------

    private fun buildStyleCarousel() {
        b.styleRow.removeAllViews()
        FilterRegistry.styles.forEach { style ->
            val chip = UiKit.chip(this, style.label, false, 13f)
            chip.setOnClickListener { selectStyle(style) }
            styleChips[style] = chip
            b.styleRow.addView(chip)
        }
    }

    private fun selectStyle(style: FilterStyle) {
        if (style == processor.style) return
        styleParams[processor.style] = params
        stylePresetKey[processor.style] = activePresetKey
        processor = FilterRegistry.byStyle(style)
        params = styleParams[style] ?: processor.defaultParams
        activePresetKey = if (styleParams.containsKey(style)) stylePresetKey[style]
        else "b:" + processor.presets.first().name
        settings.lastStyle = style
        onFilterChanged(rebuildPanel = true)
    }

    /** Push the current processor/params to the GPU and refresh every piece of UI that shows them. */
    private fun onFilterChanged(rebuildPanel: Boolean) {
        renderer.setFilter(processor, params)
        updateLabel()
        styleChips.forEach { (style, chip) ->
            UiKit.styleChip(chip, style == processor.style)
            if (style == processor.style) chip.post {
                b.styleScroll.smoothScrollTo(chip.left - (b.styleScroll.width - chip.width) / 2, 0)
            }
        }
        rebuildPresetRow()
        val hasControls = processor.controls.isNotEmpty()
        b.btnTune.visibility = if (hasControls) View.VISIBLE else View.INVISIBLE
        if (!hasControls) panel.hide()
        if (rebuildPanel) panel.bind(processor, params) else panel.refresh(params)
    }

    private fun presetDisplayName(): String? {
        val key = activePresetKey ?: return null
        return if (key.startsWith("b:")) key.removePrefix("b:")
        else presetStore.all().firstOrNull { "c:${it.id}" == key }?.name
    }

    private fun updateLabel() {
        b.styleLabel.text =
            if (processor.style == FilterStyle.ORIGINAL) "ORIGINAL"
            else "${processor.title} · ${(presetDisplayName() ?: "CUSTOM").uppercase(Locale.getDefault())}"
    }

    private fun rebuildPresetRow() {
        b.presetRow.removeAllViews()
        presetChips.clear()
        if (processor.style == FilterStyle.ORIGINAL) {
            b.presetScroll.visibility = View.GONE
            return
        }
        b.presetScroll.visibility = View.VISIBLE
        processor.presets.forEach { preset ->
            val key = "b:${preset.name}"
            val chip = UiKit.chip(this, preset.name, key == activePresetKey, 11f)
            chip.setOnClickListener { applyPreset(key, preset.params) }
            presetChips.add(key to chip)
            b.presetRow.addView(chip)
        }
        presetStore.forStyle(processor.style).forEach { custom ->
            val key = "c:${custom.id}"
            val chip = UiKit.chip(this, "★ ${custom.name}", key == activePresetKey, 11f)
            chip.setOnClickListener { applyPreset(key, custom.params) }
            chip.setOnLongClickListener { showCustomPresetMenu(custom, chip); true }
            presetChips.add(key to chip)
            b.presetRow.addView(chip)
        }
    }

    private fun updatePresetSelection() {
        presetChips.forEach { (key, chip) -> UiKit.styleChip(chip, key == activePresetKey) }
    }

    private fun applyPreset(key: String, p: FilterParams) {
        params = p
        activePresetKey = key
        onFilterChanged(rebuildPanel = false)
    }

    private fun askSavePreset() {
        val input = EditText(this).apply {
            setText("${processor.title} – MY LOOK")
            setSelectAllOnFocus(true)
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle("Save preset")
            .setMessage("Saves the current ${processor.title} settings on this phone.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "My look" }
                val created = presetStore.create(name, processor.style, params)
                activePresetKey = "c:${created.id}"
                rebuildPresetRow()
                updateLabel()
                toast("Saved “$name”")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomPresetMenu(custom: CustomPreset, anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "Rename")
            menu.add(0, 2, 1, "Overwrite with current settings")
            menu.add(0, 3, 2, "Delete")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> askRename(custom)
                    2 -> { presetStore.overwrite(custom.id, params); toast("Preset updated") }
                    3 -> {
                        presetStore.delete(custom.id)
                        if (activePresetKey == "c:${custom.id}") activePresetKey = null
                        rebuildPresetRow()
                        updateLabel()
                    }
                }
                true
            }
            show()
        }
    }

    private fun askRename(custom: CustomPreset) {
        val input = EditText(this).apply { setText(custom.name); setSelectAllOnFocus(true); setSingleLine() }
        AlertDialog.Builder(this)
            .setTitle("Rename preset")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    presetStore.rename(custom.id, name)
                    rebuildPresetRow()
                    updateLabel()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------------------------------------------
    // Photo capture / import / export
    // ---------------------------------------------------------------------------------------

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun maxPhotoSide(): Int = min(3072, renderer.maxTextureSize)

    private fun onShutter() {
        if (importUri != null) { exportImported(); return }
        if (needsLegacyStorage && !has(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            return
        }
        playShutterFlash()

        val proc = processor
        val prm = params
        val s = settings
        val cropAspect = if (s.cropToPreview) b.glView.width.toFloat() / b.glView.height else 0f
        val name = "StyleCam_${proc.style.id}_${stamp()}"

        camera.takePhoto(s.mirrorFrontPhotos) { original ->
            if (original == null) {
                runOnUiThread { toast("Capture failed") }
                return@takePhoto
            }
            // Photo mode: same shader passes as live, but at full resolution with uPhoto = 1
            val toProcess = ImageUtils.limit(original, maxPhotoSide(), recycleSource = false)
            renderer.processBitmap(toProcess, proc, prm, cropAspect) { result ->
                ioExecutor.execute {
                    val uri = result?.let { MediaSaver.saveJpeg(this, it, name) }
                    if (s.saveOriginal) MediaSaver.saveJpeg(this, original, "${name}_original")
                    if (toProcess !== original) toProcess.recycle()
                    original.recycle()
                    result?.recycle()
                    runOnUiThread { toast(if (uri != null) "Saved to Pictures/StyleCam" else "Could not save photo") }
                }
            }
        }
    }

    private fun enterImportMode(uri: Uri) {
        ioExecutor.execute {
            val bmp = ImageUtils.decode(this, uri, 1600)
            runOnUiThread {
                if (bmp == null) { toast("Could not open that image"); return@runOnUiThread }
                importUri = uri
                camera.stop()
                cameraStarted = false
                renderer.setImage(bmp)
                b.importBar.visibility = View.VISIBLE
                b.btnFlip.visibility = View.INVISIBLE
                b.btnFlash.visibility = View.INVISIBLE
            }
        }
    }

    private fun exitImportMode() {
        importUri = null
        renderer.setImage(null)
        b.importBar.visibility = View.GONE
        startCameraIfPermitted(requestIfNeeded = false)
    }

    /** Saves a NEW filtered copy of the imported photo; the original file is never touched. */
    private fun exportImported() {
        val uri = importUri ?: return
        if (needsLegacyStorage && !has(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            return
        }
        val proc = processor
        val prm = params
        val name = "StyleCam_import_${proc.style.id}_${stamp()}"
        toast("Saving…")
        ioExecutor.execute {
            val full = ImageUtils.decode(this, uri, maxPhotoSide())
            if (full == null) {
                runOnUiThread { toast("Could not read the image") }
                return@execute
            }
            renderer.processBitmap(full, proc, prm, 0f) { result ->
                ioExecutor.execute {
                    val saved = result?.let { MediaSaver.saveJpeg(this, it, name) }
                    full.recycle()
                    result?.recycle()
                    runOnUiThread { toast(if (saved != null) "Saved a filtered copy to Pictures/StyleCam" else "Could not save photo") }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Small visual effects
    // ---------------------------------------------------------------------------------------

    private fun playShutterFlash() {
        b.flashOverlay.visibility = View.VISIBLE
        b.flashOverlay.alpha = 0.7f
        b.flashOverlay.animate().alpha(0f).setDuration(220).withEndAction { b.flashOverlay.visibility = View.GONE }.start()
    }

    private fun showFocusRing(x: Float, y: Float) {
        val ring = b.focusRing
        ring.animate().cancel()
        ring.x = x - ring.width / 2f
        ring.y = y - ring.height / 2f
        ring.scaleX = 1.5f
        ring.scaleY = 1.5f
        ring.alpha = 1f
        ring.visibility = View.VISIBLE
        ring.animate().setStartDelay(0).scaleX(1f).scaleY(1f).setDuration(180).withEndAction {
            ring.animate().alpha(0f).setStartDelay(700).setDuration(250).withEndAction {
                ring.visibility = View.GONE
            }.start()
        }.start()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
