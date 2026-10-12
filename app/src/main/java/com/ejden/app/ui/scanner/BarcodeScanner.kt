package com.ejden.app.ui.scanner

import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.ejden.app.data.JS_WHITESPACE_RUN
import com.ejden.app.data.jsTrim
import com.ejden.app.ui.components.AppIcons
import com.ejden.app.ui.components.IconSpec
import com.ejden.app.ui.components.StrokeIcon
import com.ejden.app.ui.components.rememberCameraGate
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.theme.Ejden
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

enum class ScanTone { Info, Success, Error }

/** Plain : écran des produits et formulaires. Sales : scanner continu avec saisie manuelle. */
enum class ScannerStyle { Plain, Sales }

private const val SAME_CODE_COOLDOWN_MS = 1800L
private const val DETECTION_INTERVAL_MS = 150L

/** État d'un scanner : visible ou non, message affiché, lampe. */
class BarcodeScannerState {
    val defaultMessage = "Placez le code-barres dans le cadre."

    var visible by mutableStateOf(false)
    var message by mutableStateOf(defaultMessage)
    var tone by mutableStateOf(ScanTone.Info)
    var torchOn by mutableStateOf(false)
    var flashToken by mutableIntStateOf(0)
    var flashMs: Long = 2000L

    var onDetect: (String, BarcodeScannerState) -> Unit = { _, _ -> }
    var onManual: ((String) -> Boolean)? = null
    var onFailure: (String) -> Unit = {}
    var openRequest: () -> Unit = {}

    /** Ouvre le scanner (demande la caméra si besoin). */
    fun open() {
        openRequest()
    }

    fun show() {
        message = defaultMessage
        tone = ScanTone.Info
        torchOn = false
        visible = true
    }

    fun close() {
        visible = false
        torchOn = false
    }

    /** Affiche un message court dans le scanner, puis revient au message d'aide. */
    fun flash(text: String, tone: ScanTone, ms: Long = 2000L) {
        message = text
        this.tone = tone
        flashMs = ms
        flashToken += 1
    }
}

@Composable
fun rememberBarcodeScanner(
    onFailure: (String) -> Unit,
    onDetect: (String, BarcodeScannerState) -> Unit
): BarcodeScannerState {
    val context = LocalContext.current
    val state = remember { BarcodeScannerState() }
    val gate = rememberCameraGate {
        state.onFailure("Accès à la caméra refusé. Autorisez-le dans les réglages.")
    }

    SideEffect {
        state.onDetect = onDetect
        state.onFailure = onFailure
        state.openRequest = {
            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                onFailure("Aucune caméra compatible avec le scanner n'a été trouvée.")
            } else {
                gate { state.show() }
            }
        }
    }
    return state
}

private class Cooldown {
    var code = ""
    var time = 0L
}

@Composable
fun BarcodeScannerDialog(state: BarcodeScannerState, style: ScannerStyle, title: String) {
    if (!state.visible) return

    // Le message d'erreur ou de succès repart après quelques secondes.
    LaunchedEffect(state.flashToken) {
        if (state.flashToken > 0) {
            kotlinx.coroutines.delay(state.flashMs)
            state.message = state.defaultMessage
            state.tone = ScanTone.Info
        }
    }

    Dialog(
        onDismissRequest = { state.close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        ScannerContent(state, style, title)
    }
}

@Composable
private fun ScannerContent(state: BarcodeScannerState, style: ScannerStyle, title: String) {
    val c = Ejden.colors
    val sales = style == ScannerStyle.Sales
    val view = LocalView.current
    val activity = LocalContext.current as? ComponentActivity
    var torchSupported by remember { mutableStateOf(false) }
    val cooldown = remember { Cooldown() }

    // Icônes de la barre d'état lisibles sur fond noir.
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    // La caméra s'arrête si l'application passe en arrière-plan.
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) state.close()
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    val onCode: (String) -> Unit = { code ->
        val now = SystemClock.elapsedRealtime()
        if (!(code == cooldown.code && now - cooldown.time < SAME_CODE_COOLDOWN_MS)) {
            cooldown.code = code
            cooldown.time = now
            state.onDetect(code, state)
        }
    }

    val onCameraError: () -> Unit = {
        if (sales) {
            state.message = "Impossible de démarrer le scanner caméra."
            state.tone = ScanTone.Error
        } else {
            state.close()
            state.onFailure("Impossible de démarrer le scanner caméra. Saisissez le code à la main.")
        }
    }

    val screenWidth = LocalConfiguration.current.screenWidthDp.toFloat()

    Box(Modifier.fillMaxSize().background(if (sales) Color(0xFF0B1316) else Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            // ---- En-tête ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .heightIn(min = if (sales) 60.dp else 64.dp)
                    .padding(
                        start = if (sales) 14.dp else 16.dp,
                        end = if (sales) 14.dp else 16.dp,
                        top = if (sales) 8.dp else 0.dp,
                        bottom = if (sales) 8.dp else 12.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (sales) 12.dp else 14.dp)
            ) {
                CircleButton(AppIcons.Close, if (sales) 44 else 42, if (sales) 22 else 20, Color.White.copy(alpha = 0.12f)) {
                    state.close()
                }
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                if (torchSupported) {
                    if (sales) {
                        CircleButton(
                            TORCH, 44, 22,
                            if (state.torchOn) Color(0xFFD98A16) else Color.White.copy(alpha = 0.12f)
                        ) { state.torchOn = !state.torchOn }
                    } else {
                        Box(
                            Modifier
                                .heightIn(min = 42.dp)
                                .background(
                                    if (state.torchOn) Color(0xFFD98A16) else Color.White.copy(alpha = 0.12f),
                                    RoundedCornerShape(21.dp)
                                )
                                .tap { state.torchOn = !state.torchOn }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Lampe", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ---- Caméra ----
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    torchOn = state.torchOn,
                    onTorchSupported = { torchSupported = it },
                    onCode = onCode,
                    onError = onCameraError
                )

                if (sales) {
                    // Cadre arrondi : l'extérieur est assombri.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                            .drawBehind {
                                val frameWidth = minOf(size.width * 0.78f, 320.dp.toPx())
                                val frameHeight = minOf(size.width * 0.46f, 190.dp.toPx())
                                val topLeft = Offset((size.width - frameWidth) / 2, (size.height - frameHeight) / 2)
                                val radius = CornerRadius(16.dp.toPx())
                                drawRect(Color.Black.copy(alpha = 0.45f))
                                drawRoundRect(
                                    Color.Transparent, topLeft, GeoSize(frameWidth, frameHeight), radius,
                                    blendMode = BlendMode.Clear
                                )
                                drawRoundRect(
                                    Color.White.copy(alpha = 0.9f), topLeft, GeoSize(frameWidth, frameHeight), radius,
                                    style = Stroke(3.dp.toPx())
                                )
                            }
                    )
                    val pill = when (state.tone) {
                        ScanTone.Error -> Color(0xFFD64545)
                        ScanTone.Success -> Color(0xFF168A5B)
                        ScanTone.Info -> Color.Black.copy(alpha = 0.6f)
                    }
                    Text(
                        state.message,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 16.dp, end = 16.dp, bottom = 18.dp)
                            .fillMaxWidth()
                            .background(pill, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                } else {
                    val frameWidth = minOf(screenWidth * 0.78f, 340f).dp
                    Box(Modifier.size(frameWidth, 180.dp)) {
                        Box(Modifier.align(Alignment.TopStart).size(30.dp).corner(top = true, left = true))
                        Box(Modifier.align(Alignment.TopEnd).size(30.dp).corner(top = true, left = false))
                        Box(Modifier.align(Alignment.BottomStart).size(30.dp).corner(top = false, left = true))
                        Box(Modifier.align(Alignment.BottomEnd).size(30.dp).corner(top = false, left = false))
                    }
                }
            }

            // ---- Message (écran des produits et formulaires) ----
            if (!sales) {
                val error = state.tone == ScanTone.Error
                Text(
                    state.message,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (error) Color(0xFFD64545) else Color.Black)
                        .navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
                    color = if (error) Color.White else Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center
                )
            }

            // ---- Saisie manuelle (scanner de la vente) ----
            val manual = state.onManual
            if (sales && manual != null) {
                ManualEntry(onSubmit = { code -> manual(code) }, surface = c.surface)
            }
        }
    }
}

private val TORCH = IconSpec(listOf("M7 2h10l-1 7H8L7 2z", "M8 9l1 4v9h6v-9l1-4"))

@Composable
private fun CircleButton(
    icon: IconSpec,
    size: Int,
    iconSize: Int,
    background: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier.size(size.dp).background(background, CircleShape).tap(onClick),
        contentAlignment = Alignment.Center
    ) {
        StrokeIcon(icon, Color.White, iconSize.dp, strokeWidth = 2f)
    }
}

/** Un coin du cadre de visée (trait de 3 dp sur deux côtés). */
private fun Modifier.corner(top: Boolean, left: Boolean): Modifier = drawBehind {
    val stroke = 3.dp.toPx()
    val half = stroke / 2
    val y = if (top) half else size.height - half
    val x = if (left) half else size.width - half
    val horizontalFrom = if (left) 0f else size.width
    val verticalFrom = if (top) 0f else size.height
    drawLine(Color.White, Offset(horizontalFrom, y), Offset(x, y), stroke)
    drawLine(Color.White, Offset(x, verticalFrom), Offset(x, y), stroke)
}

@Composable
private fun ManualEntry(onSubmit: (String) -> Boolean, surface: Color) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(16.dp)
    var text by remember { mutableStateOf("") }

    fun submit() {
        if (onSubmit(text)) text = ""
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(surface)
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Ou saisissez le code", color = c.textSecondary, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .border(1.dp, c.border, shape)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) Text("Code-barres", color = Color(0xFF757575), fontSize = 16.sp)
                            inner()
                        }
                    }
                )
            }
            Box(
                Modifier
                    .height(48.dp)
                    .background(c.primary, shape)
                    .tap { submit() }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Ajouter", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Caméra et reconnaissance
// ---------------------------------------------------------------------------

private class CodeAnalyzer(private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_QR_CODE
            )
            .build()
    )
    private var last = 0L

    override fun analyze(imageProxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val media = imageProxy.image
        if (media == null || now - last < DETECTION_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        last = now

        val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val barcode = barcodes.firstOrNull()
                val raw = barcode?.rawValue ?: barcode?.displayValue ?: ""
                val code = raw.replace(JS_WHITESPACE_RUN, "").jsTrim()
                if (code.isNotEmpty()) onCode(code)
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() {
        scanner.close()
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier,
    torchOn: Boolean,
    onTorchSupported: (Boolean) -> Unit,
    onCode: (String) -> Unit,
    onError: () -> Unit
) {
    val context = LocalContext.current
    val owner = context as LifecycleOwner
    val currentOnCode by rememberUpdatedState(onCode)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnTorch by rememberUpdatedState(onTorchSupported)

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(Unit) {
        val executor = Executors.newSingleThreadExecutor()
        val analyzer = CodeAnalyzer { code -> currentOnCode(code) }
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider

                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                            )
                            .build()
                    )
                    .build()
                analysis.setAnalyzer(executor, analyzer)

                cameraProvider.unbindAll()
                val bound = cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                camera = bound
                // La lampe n'est proposée que si le téléphone la gère.
                currentOnTorch(bound.cameraInfo.hasFlashUnit())
            } catch (e: Exception) {
                currentOnError()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            provider?.unbindAll()
            camera = null
            analyzer.close()
            executor.shutdown()
        }
    }

    LaunchedEffect(torchOn, camera) {
        try {
            camera?.cameraControl?.enableTorch(torchOn)
        } catch (e: Exception) {
            // lampe indisponible
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
