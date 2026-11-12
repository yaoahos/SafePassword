package com.yaoahos.safepassword.android.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
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
import com.yaoahos.safepassword.core.qr.QrDecoder
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/**
 * 扫码录入动态验证码密钥。
 *
 * 取景页只负责「拿到一串文本」；是否像 TOTP 密钥交给调用方用 [com.yaoahos.safepassword.core.security.Totp.parse]
 * 判断，避免这里和 core 的解析规则各写一份。
 * 没有相机权限时仍可从相册选图识别（相册不需要 CAMERA 权限）。
 *
 * 三处容易「没反应」的地方在这里显式处理：
 * 1. 解码在 ImageAnalysis 的线程上跑，回调必须切回主线程——否则调用方的 Toast
 *    会抛「Can't toast on a thread that has not called Looper」并被分析线程的 catch 吞掉；
 * 2. 相机绑定失败不再静默吞异常，直接把原因显示在提示行上；
 * 3. 分析帧分辨率从默认 640×480 提到 1280×720，远距离的小二维码才够像素。
 */
@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    onDecoded: (String) -> Unit
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
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
    var cameraError by remember { mutableStateOf<String?>(null) }
    // 已经跑过多少帧：>0 说明相机真的在出图，只是还没认出码，两种情况要给不同的提示。
    var frames by remember { mutableStateOf(0) }

    val finish: (String) -> Unit = { text ->
        if (!handled) {
            handled = true
            onDecoded(text)
        }
    }

    // 相机在出图但 7 秒还没认出码：提示用户调整，而不是一直干等。
    LaunchedEffect(frames > 0, handled) {
        if (frames > 0 && !handled) {
            delay(7000)
            if (!handled) {
                hint = "还没认出来：把二维码放平、离远一点，或改用相册里的图片"
            }
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
                    onError = { message -> cameraError = message },
                    onFrame = { if (frames == 0) frames = 1 },
                    // 分析线程 → 主线程：状态写入与 Toast 都必须在主线程。
                    onDecoded = { text -> mainHandler.post { finish(text) } }
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
                    cameraError ?: hint,
                    color = if (cameraError != null) Color(0xFFFFB4AB) else Color(0xFFE6E8EB),
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
    onError: (String) -> Unit,
    onFrame: () -> Unit,
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
    val analyzer = remember { QrAnalyzer(onDecoded = onDecoded, onFrame = onFrame) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(lifecycleOwner) {
        var provider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val bound = future.get()
                provider = bound
                val preview = Preview.Builder()
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
                @Suppress("DEPRECATION")
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    // 默认 640×480 对远处的二维码像素不够，解不出来又不报错，表现就是「没反应」。
                    .setTargetResolution(Size(1280, 720))
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }
                bound.unbindAll()
                camera = bound.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                // 以前这里整个 runCatching 静默吞掉：相机没绑上时画面全黑、也不会出任何提示。
                Log.w(TAG, "相机启动失败", e)
                onError("相机启动失败：${e.message ?: "未知原因"}，可改用相册识别")
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
 *
 * 解码本身在 [QrDecoder]（纯 JVM，有单测覆盖行填充与旋转），这里只负责取 Y 平面、
 * 复用缓冲区、把结果交出去。缓冲区复用是为了不在 30fps 下每帧分配近 1MB。
 */
private class QrAnalyzer(
    private val onDecoded: (String) -> Unit,
    private val onFrame: () -> Unit
) : ImageAnalysis.Analyzer {
    private val reader = QrDecoder.newReader()
    private var luma = ByteArray(0)
    @Volatile
    private var handled = false
    private var announced = false

    override fun analyze(image: ImageProxy) {
        try {
            if (handled) return
            if (!announced) {
                announced = true
                onFrame()
            }
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val length = buffer.remaining()
            if (length <= 0) return
            if (luma.size < length) luma = ByteArray(length)
            buffer.get(luma, 0, length)
            val text = QrDecoder.decodeYuv(
                reader = reader,
                data = luma,
                dataLength = length,
                width = image.width,
                height = image.height,
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride,
                rotationDegrees = image.imageInfo.rotationDegrees
            ) ?: return
            handled = true
            onDecoded(text)
        } catch (e: Exception) {
            // 单帧解码失败是常态（对焦中、角度偏），记一次日志后丢给下一帧。
            Log.d(TAG, "解码失败：${e.message}")
        } finally {
            image.close()
        }
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
        QrDecoder.decodePixels(QrDecoder.newReader(), pixels, scaled.width, scaled.height)
    } finally {
        bitmap.recycle()
    }
}.getOrNull()

private const val TAG = "QrScanner"
