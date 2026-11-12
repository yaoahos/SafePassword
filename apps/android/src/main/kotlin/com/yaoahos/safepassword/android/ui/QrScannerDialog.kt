package com.yaoahos.safepassword.android.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

private val QR_HINTS: Map<DecodeHintType, Any> = mapOf(
    DecodeHintType.POSSIBLE_FORMATS to listOf(
        BarcodeFormat.QR_CODE,
        BarcodeFormat.DATA_MATRIX,
        BarcodeFormat.CODE_128,
        BarcodeFormat.CODE_39
    ),
    DecodeHintType.TRY_HARDER to true
)

/**
 * 扫码录入动态验证码密钥。
 *
 * 取景页只负责「拿到一串文本」；是否像 TOTP 密钥交给调用方用 [com.yaoahos.safepassword.core.security.Totp.parse]
 * 判断，避免这里和 core 的解析规则各写一份。
 * 没有相机权限时仍可从相册选图识别（相册不需要 CAMERA 权限）。
 */
@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    onDecoded: (String) -> Unit
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result -> granted = result }

    var torchOn by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("把取景框对准网站给出的二维码") }
    var handled by remember { mutableStateOf(false) }

    val finish: (String) -> Unit = { text ->
        if (!handled) {
            handled = true
            onDecoded(text)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val text = decodeFromUri(context, uri)
            if (text != null) {
                finish(text)
            } else {
                hint = "这张图里没认出二维码，换一张或直接对着屏幕扫"
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (granted) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    torchOn = torchOn,
                    onDecoded = { text -> finish(text) }
                )
            } else {
                NoCameraPermission(
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onPickImage = { galleryLauncher.launch("image/*") }
                )
            }

            // 取景框：只是视觉引导，解码范围是整帧。
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.68f)
                    .aspectRatio(1f)
                    .border(2.dp, Color(0x66FFFFFF), RoundedCornerShape(18.dp))
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 6.dp, end = 6.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "扫描动态验证码",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                if (granted) {
                    IconButton(onClick = { torchOn = !torchOn }) {
                        Icon(
                            if (torchOn) Icons.Outlined.FlashOff else Icons.Outlined.FlashOn,
                            contentDescription = if (torchOn) "关闭手电筒" else "打开手电筒",
                            tint = Color.White
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // Dialog 窗口拿到的导航栏 inset 是 0（窗口本身贴在屏幕底），
                    // 按钮会压在系统手势区里点不动，所以这里固定留出高度。
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 72.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    hint,
                    color = Color(0xFFE6E8EB),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { galleryLauncher.launch("image/*") }) {
                    Icon(
                        Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("从相册选择图片")
                }
            }
        }
    }
}

@Composable
private fun NoCameraPermission(onRequest: () -> Unit, onPickImage: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.QrCodeScanner,
            contentDescription = null,
            tint = Color(0xFF9AA0A6),
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "需要相机权限才能实时扫码",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "也可以从相册里选一张二维码截图来识别。",
            color = Color(0xFF9AA0A6),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest) { Text("去授权") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onPickImage) { Text("从相册选择图片") }
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier,
    torchOn: Boolean,
    onDecoded: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember { QrAnalyzer(onDecoded) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(lifecycleOwner) {
        var provider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val bound = future.get()
                provider = bound
                val preview = Preview.Builder()
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }
                bound.unbindAll()
                camera = bound.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { provider?.unbindAll() }
            runCatching { executor.shutdown() }
        }
    }

    LaunchedEffect(torchOn, camera) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/**
 * 逐帧解码。单线程执行（ImageAnalysis 的 executor），解出一帧后就不再处理。
 * 同一个内容 1.2 秒内只回调一次，避免抖动导致重复回填。
 */
private class QrAnalyzer(private val onFound: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply { setHints(QR_HINTS) }
    @Volatile
    private var handled = false
    private var lastText: String? = null
    private var lastAt = 0L

    override fun analyze(image: ImageProxy) {
        try {
            if (handled) return
            val text = decodeFrame(image) ?: return
            val now = SystemClock.elapsedRealtime()
            if (text == lastText && now - lastAt < 1200L) return
            lastText = text
            lastAt = now
            handled = true
            onFound(text)
        } catch (_: Exception) {
            // 单帧解码失败是常态（对焦中、角度偏），直接丢给下一帧。
        } finally {
            image.close()
        }
    }

    private fun decodeFrame(image: ImageProxy): String? = runCatching {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val data: ByteArray
        if (pixelStride == 1 && rowStride == width) {
            data = ByteArray(buffer.remaining()).also { buffer.get(it) }
        } else {
            // Y 平面每行末尾可能有填充字节，逐行抠出真正的 width 个像素。
            data = ByteArray(width * height)
            val row = ByteArray(rowStride)
            var offset = 0
            for (y in 0 until height) {
                val len = minOf(rowStride, buffer.remaining())
                if (len <= 0) break
                buffer.get(row, 0, len)
                System.arraycopy(row, 0, data, offset, minOf(width, len))
                offset += width
            }
        }
        var source: LuminanceSource =
            PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        // rotationDegrees 是「顺时针转多少度摆正」，ZXing 提供的是逆时针旋转。
        repeat(rotationSteps(image.imageInfo.rotationDegrees)) {
            source = source.rotateCounterClockwise()
        }
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    }.getOrNull()

    private fun rotationSteps(degrees: Int): Int = when (((degrees % 360) + 360) % 360) {
        90 -> 3
        180 -> 2
        270 -> 1
        else -> 0
    }
}

/** 从相册图片里识别一个码；识别不到返回 null。 */
private fun decodeFromUri(context: Context, uri: Uri): String? = runCatching {
    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        ?: return null
    try {
        val maxSide = 1600
        val longest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longest > maxSide) {
            val ratio = maxSide.toFloat() / longest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        val source = RGBLuminanceSource(scaled.width, scaled.height, pixels)
        MultiFormatReader()
            .apply { setHints(QR_HINTS) }
            .decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            .text
    } finally {
        bitmap.recycle()
    }
}.getOrNull()
