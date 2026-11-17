# StyleCam

Live-camera artistic filters, fully on-device (OpenGL ES 3.0 + CameraX). The app has **no
INTERNET permission** - frames never leave the phone.

Styles: **ORIGINAL**, **RAILWAY** (1930s British railway poster), **CEL**, **GRAPHIC**
(crime-game-poster illustration look) and **IDLEON** (Smooth + Pixel cartoon-RPG look).

## Build

Requirements: Android Studio Koala+ (or JDK 17 + Android SDK 34), internet access for Gradle.

* Android Studio: *File > Open* this folder, let Gradle sync, press Run.
* Command line:  `./gradlew :app:assembleDebug`
  -> `app/build/outputs/apk/debug/StyleCam-debug.apk`
* No local SDK? Push the folder to GitHub: `.github/workflows/build.yml` builds the APK and
  uploads it as the `StyleCam-debug-apk` artifact.

Debug APKs are signed with the debug key and install directly (enable "install unknown apps").

## Architecture

    camera/    CameraController   CameraX acquisition only (ImageAnalysis RGBA + ImageCapture)
    filters/   FilterRenderer     GPU pipeline (live + photo + import), AUTO resolution, stats
               FilterProcessor    common interface + the 5 styles + FilterRegistry
               PresetData         GENERATED from tools/shader-harness/presets.json
               StyleCamGLView     GLSurfaceView + pinch / tap gestures
               GlUtils            program / FBO helpers
    assets/shaders/               GLSL ES 3.00: common, camera, smooth (Kuwahara), composite,
                                  railway / cel / graphic / idleon
    models/    FilterParams (data class, all values 0..1), FilterStyle, Preset, Quality
    storage/   MediaSaver (MediaStore), PresetStore (custom presets), SettingsStore
    ui/        MainActivity (thin coordinator), ControlPanel, SettingsDialog, SplitOverlayView
    utils/     ImageUtils (EXIF-aware decode)

Live path: `ImageAnalysis(KEEP_ONLY_LATEST, RGBA_8888)` -> copy into a reusable direct buffer ->
close the frame -> GL thread: **camera pass** (rotate / mirror / crop) -> **Kuwahara smoothing**
(edge-preserving) -> **style pass** -> **composite** (original / filtered / split). Passes run at
the *processing* resolution (LOW 640 / MEDIUM 960 / HIGH 1280 px long side, AUTO adapts from
measured GPU time) and are bilinearly upscaled, so the on-screen camera area never shrinks.
A slow GPU can only drop frames, never add latency.

Photo path: ImageCapture (16:9, highest resolution) -> same passes offscreen at full size
(<= 3072 px) with `uPhoto = 1` (bigger kernels, finer paper/grain) -> MediaStore.
Gallery import uses the same offscreen path and always saves a *new* copy.

## Adding a style

1. `assets/shaders/mystyle.frag` (uses `common.glsl`; reads `uBase` + `uSmooth`).
2. `object MyFilter : BaseProcessor()` in `FilterProcessor.kt` (sliders, presets, shader name).
3. Register it in `FilterRegistry`, add a `FilterStyle` entry.

## Not done / limitations

* **Video recording is not implemented.** Recording the *filtered* image needs a MediaCodec +
  EGL encoder surface; I did not want to ship an unfiltered-video button that contradicts the app.
* Portrait only (activity is locked; frame rotation, mirroring and photo orientation are handled).
* Flash = torch toggle on the rear camera.
* Tap-to-focus uses CameraX's display-oriented metering mapping (approximate because the 16:9
  stream is centre-cropped to the screen).
