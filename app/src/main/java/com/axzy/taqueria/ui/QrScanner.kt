package com.axzy.taqueria.ui

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.axzy.taqueria.Alerts
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pantalla de escaneo del QR de activación que genera Axzy Central.
 *
 * CameraX abre la cámara (sin Google Play Services) y cada fotograma se
 * convierte a luminancia para que ZXing lea el QR. Al detectarlo se vibra, se
 * pita y se detiene la cámara; el texto crudo se entrega a [onDetected] para
 * que la pantalla de activación lo valide con `QrPayload.parse`.
 *
 * Si el permiso de cámara se niega, se explica y se ofrece volver para escribir
 * el código a mano ([onCancel]).
 */
@Composable
fun QrScannerScreen(
    onDetected: (String) -> Unit,
    onCancel: () -> Unit,
    title: String = "Escanear QR de la Central",
    hint: String = "Apunta la cámara al QR que muestra la Central.\nEl QR ya trae la dirección del servidor.",
    fallbackLabel: String = "Escribir el código a mano",
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        asked = true
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            granted -> {
                CameraPreview(onDetected = onDetected)
                ScannerFrame(title = title, hint = hint, onCancel = onCancel)
            }
            asked -> CameraPermissionDenied(fallbackLabel = fallbackLabel, onCancel = onCancel, onOpenSettings = context::openAppSettings)
            else -> Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(Ui.pad),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Pidiendo permiso de cámara…", color = Color.White, fontSize = 16.sp)
                TextButton(onClick = onCancel) { Text(fallbackLabel, color = Color.White) }
            }
        }
    }
}

@Composable
private fun ScannerFrame(title: String, hint: String, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(Ui.pad),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onCancel) { Text("Cerrar", color = Color.White) }
        }
        Box(Modifier.size(260.dp).border(3.dp, Palette.primary, RoundedCornerShape(24.dp)))
        Text(
            hint,
            color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun CameraPermissionDenied(fallbackLabel: String, onCancel: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(Ui.pad),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📷", fontSize = 52.sp)
        Text(
            "Sin permiso de cámara", fontSize = 24.sp, fontWeight = FontWeight.Black,
            color = Color.White, modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Para escanear el QR hay que permitir la cámara. También puedes activar escribiendo el código a mano.",
            color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        ActionButton(fallbackLabel, Palette.primary, Color.White, Modifier.fillMaxWidth()) { onCancel() }
        TextButton(onClick = onOpenSettings) { Text("Abrir ajustes de la app", color = Color.White) }
    }
}

/** Cámara + análisis de fotogramas. Debe componerse solo con el permiso concedido. */
@Composable
private fun CameraPreview(onDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = remember(context) { context.findLifecycleOwner() }
    if (lifecycleOwner == null) {
        Text("No se pudo abrir la cámara. Escribe el código a mano.", color = Color.White)
        return
    }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val mainExecutor = remember { Executor { runnable -> mainHandler.post(runnable) } }
    val found = remember { AtomicBoolean(false) }
    val reader = remember { QRCodeReader() }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        val executor = Executors.newSingleThreadExecutor()
        var provider: ProcessCameraProvider? = null
        var analysis: ImageAnalysis? = null
        val listener = Runnable {
            runCatching {
                val cameraProvider = future.get()
                provider = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis.setAnalyzer(executor) { image ->
                    try {
                        if (found.get()) return@setAnalyzer
                        val text = runCatching { decodeQr(image, reader) }.getOrNull() ?: return@setAnalyzer
                        if (!found.compareAndSet(false, true)) return@setAnalyzer
                        Alerts.vibrate(context.applicationContext, 80)
                        Alerts.beep(1)
                        // La cámara se detiene en cuanto se lee: el QR solo se usa una vez.
                        runCatching { cameraProvider.unbindAll() }
                        mainExecutor.execute { onDetected(text) }
                    } finally {
                        image.close()
                    }
                }
                analysis = imageAnalysis
                val selector = if (runCatching { cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(true)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
            }.onFailure { e ->
                error = "No se pudo abrir la cámara. Escribe el código a mano.\n${e.message.orEmpty()}".trim()
            }
        }
        future.addListener(listener, mainExecutor)
        onDispose {
            runCatching { analysis?.clearAnalyzer() }
            runCatching { provider?.unbindAll() }
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        error?.let { message ->
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(Ui.pad),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(message, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * Procesa un fotograma YUV: copia el plano Y (luminancia) y deja que ZXing
 * busque el QR. El detector de ZXing ya prueba las cuatro orientaciones, así
 * que no hace falta rotar la imagen.
 */
private fun decodeQr(image: ImageProxy, reader: QRCodeReader): String? {
    val width = image.width
    val height = image.height
    if (width <= 0 || height <= 0) return null
    val plane = image.planes.getOrNull(0) ?: return null
    val data = ByteArray(width * height)
    val buffer = plane.buffer
    buffer.rewind()
    if (plane.pixelStride == 1 && plane.rowStride == width) {
        buffer.get(data, 0, minOf(data.size, buffer.remaining()))
    } else {
        for (row in 0 until height) {
            val start = row * plane.rowStride
            if (start + width > buffer.limit()) break
            buffer.position(start)
            buffer.get(data, row * width, width)
        }
    }
    val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
    return reader.decode(BinaryBitmap(HybridBinarizer(source))).text
}

private fun Context.hasCameraPermission(): Boolean =
    checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var current: Context? = this
    while (current != null) {
        if (current is LifecycleOwner) return current
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}

private fun Context.openAppSettings() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
