package com.example.freshtrack.presentation.screen.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.freshtrack.R
import com.example.freshtrack.domain.capture.DateCandidate
import com.example.freshtrack.domain.capture.PrintedDateParser
import com.example.freshtrack.domain.model.ConfidenceBand
import com.example.freshtrack.domain.model.DateKind
import com.example.freshtrack.domain.model.DateSource
import com.example.freshtrack.domain.model.ExpiryDate
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.Executors

/**
 * What the camera is being pointed at.
 *
 * One screen rather than two because the camera plumbing, the permission
 * handling and the torch are identical; only the analyzer and what comes back
 * differ.
 */
enum class ScanMode { BARCODE, DATE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    onBarcodeScanned: (String) -> Unit,
    onNavigateBack: () -> Unit,
    mode: ScanMode = ScanMode.BARCODE,
    onDateScanned: (ExpiryDate) -> Unit = {},
    today: LocalDate = LocalDate.now()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Holding a reading freezes the analyzer, so what is on screen stays the
    // thing being asked about rather than shifting under the question.
    var reading by remember { mutableStateOf<DateReading?>(null) }

    // Whether any text at all has come back, which is the difference between
    // "pointed at nothing" and "reading the label but there is no date in it".
    var sawAnyText by remember { mutableStateOf(false) }

    // A scan that finds nothing must say so. Left alone the camera would sit
    // there indefinitely looking exactly like a camera that is about to work,
    // and the person would never learn that the manual route is one tap away.
    var struggling by remember { mutableStateOf(false) }
    LaunchedEffect(reading) {
        struggling = false
        if (reading == null) {
            delay(UNRESOLVED_AFTER_MS)
            struggling = true
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var flashEnabled by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    // Request permission on first composition
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (mode == ScanMode.DATE) {
                                R.string.scanner_title_date
                            } else {
                                R.string.scanner_title_barcode
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            flashEnabled = !flashEnabled
                            cameraControl?.enableTorch(flashEnabled)
                        }
                    ) {
                        Icon(
                            if (flashEnabled) Icons.Default.FlashlightOn
                            else Icons.Default.FlashlightOff,
                            stringResource(R.string.scanner_toggle_flash)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (hasCameraPermission) {
                CameraPreview(
                    mode = mode,
                    paused = reading != null,
                    onBarcodeScanned = onBarcodeScanned,
                    onTextRecognised = { text ->
                        sawAnyText = true
                        val candidates = PrintedDateParser.parse(text, today)
                        if (candidates.isNotEmpty()) {
                            reading = DateReading(text = text, candidates = candidates)
                        }
                    },
                    onCameraControlReady = { control ->
                        cameraControl = control
                    }
                )

                ScanningOverlay(mode, showHint = !struggling)

                if (mode == ScanMode.DATE && struggling && reading == null) {
                    UnresolvedScanNotice(
                        sawAnyText = sawAnyText,
                        onEnterByHand = onNavigateBack,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                reading?.let { found ->
                    DateReviewSheet(
                        reading = found,
                        onDismiss = { reading = null },
                        onConfirm = { candidate ->
                            onDateScanned(
                                ExpiryDate.recognised(
                                    value = candidate.value,
                                    kind = candidate.kind,
                                    source = DateSource.PRINTED_OCR,
                                    confidence = candidate.confidence
                                ).confirmedAt(System.currentTimeMillis())
                            )
                        }
                    )
                }

            } else {
                // Permission denied state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.scanner_permission_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(
                            if (mode == ScanMode.DATE) {
                                R.string.scanner_permission_body_date
                            } else {
                                R.string.scanner_permission_body_barcode
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    ) {
                        Text(stringResource(R.string.scanner_grant_permission))
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    mode: ScanMode,
    paused: Boolean,
    onBarcodeScanned: (String) -> Unit,
    onTextRecognised: (String) -> Unit,
    onCameraControlReady: (CameraControl) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    val textRecogniser = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    var isScanning by remember { mutableStateOf(false) }

    // The analyzer runs on a camera thread and reads these, so they have to be
    // the live values rather than the ones captured when the view was built.
    val pausedNow by rememberUpdatedState(paused)
    val modeNow by rememberUpdatedState(mode)
    val onBarcode by rememberUpdatedState(onBarcodeScanned)
    val onText by rememberUpdatedState(onTextRecognised)

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            barcodeScanner.close()
            textRecogniser.close()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                // Preview
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // Image analysis for barcode scanning
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            when {
                                pausedNow -> imageProxy.close()
                                modeNow == ScanMode.DATE -> recogniseText(
                                    imageProxy = imageProxy,
                                    recogniser = textRecogniser,
                                    onText = { onText(it) }
                                )
                                isScanning -> imageProxy.close()
                                else -> processImageProxy(
                                    imageProxy = imageProxy,
                                    barcodeScanner = barcodeScanner,
                                    onBarcodeDetected = { barcode ->
                                        isScanning = true
                                        onBarcode(barcode)
                                    }
                                )
                            }
                        }
                    }

                // Camera selector
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )

                    onCameraControlReady(camera.cameraControl)

                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun ScanningOverlay(mode: ScanMode, showHint: Boolean = true) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Scanning frame
            Surface(
                modifier = Modifier.size(250.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.medium
            ) {}

            // Instructions. Suppressed once the unresolved notice is up, which
            // gives better advice than this does and would otherwise be the
            // second of two stacked cards saying overlapping things.
            if (showHint) Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Text(
                    text = stringResource(
                        if (mode == ScanMode.DATE) {
                            R.string.scanner_hint_date
                        } else {
                            R.string.scanner_hint_barcode
                        }
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    imageProxy: ImageProxy,
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onBarcodeDetected: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let { value ->
                        onBarcodeDetected(value)
                        return@addOnSuccessListener
                    }
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}
/** A frozen reading: what the camera saw, and what it could mean. */
private data class DateReading(
    val text: String,
    val candidates: List<DateCandidate>
)

/**
 * The confirmation step between reading a date and believing it.
 *
 * Nothing recognised becomes inventory truth without passing through here.
 * The recognised text is shown alongside the candidates because the failure
 * this is guarding against is not a bad parse — it is a misread digit, and the
 * only person who can catch that is the one holding the packet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateReviewSheet(
    reading: DateReading,
    onDismiss: () -> Unit,
    onConfirm: (DateCandidate) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(
                    if (reading.candidates.size > 1) {
                        R.string.scanner_review_two_ways
                    } else {
                        R.string.scanner_review_is_this
                    }
                ),
                style = MaterialTheme.typography.titleLarge
            )

            if (reading.candidates.size > 1) {
                Text(
                    text = stringResource(R.string.scanner_review_ambiguous_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.scanner_read_from_packet),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = reading.text.trim().take(120),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            reading.candidates.forEach { candidate ->
                CandidateRow(candidate = candidate, onConfirm = { onConfirm(candidate) })
            }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.scanner_scan_again))
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: DateCandidate, onConfirm: () -> Unit) {
    val date = ExpiryDate.recognised(
        value = candidate.value,
        kind = candidate.kind,
        source = DateSource.PRINTED_OCR,
        confidence = candidate.confidence
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onConfirm
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.value.format(REVIEW_DATE_FORMAT),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    // Kind and confidence together, because "best before" and
                    // "use by" are different promises and a medium-confidence
                    // read is exactly the one that looks right and is not.
                    text = stringResource(
                        R.string.scanner_candidate_summary,
                        stringResource(kindLabel(candidate.kind)),
                        stringResource(bandLabel(date.band))
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.scanner_use_this),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * A medium-length date in whatever order the reader's locale writes them.
 *
 * Not a fixed pattern: "12 Mar 2027" and "Mar 12, 2027" are the same date and
 * the wrong one of the two reads as a mistake to whoever is holding the packet.
 */
private val REVIEW_DATE_FORMAT: DateTimeFormatter
    get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@StringRes
private fun kindLabel(kind: DateKind): Int = when (kind) {
    DateKind.BEST_BEFORE -> R.string.date_kind_best_before
    DateKind.USE_BY -> R.string.date_kind_use_by
    DateKind.SELL_BY -> R.string.date_kind_sell_by
    DateKind.OPENED_UNTIL -> R.string.date_kind_opened_until
    DateKind.FROZEN_UNTIL -> R.string.date_kind_frozen_until
    DateKind.ESTIMATED -> R.string.date_kind_estimated
    // The label carried a date but did not say which kind, and saying so is
    // more honest than picking one.
    DateKind.UNKNOWN -> R.string.date_kind_unknown
}

@StringRes
private fun bandLabel(band: ConfidenceBand): Int = when (band) {
    ConfidenceBand.HIGH -> R.string.confidence_high
    ConfidenceBand.MEDIUM -> R.string.confidence_medium
    ConfidenceBand.LOW -> R.string.confidence_low
}

/**
 * Runs text recognition over one frame and hands back everything it read.
 *
 * The whole block goes to the parser rather than a single line, because a date
 * and the label introducing it are frequently recognised as separate lines and
 * the association between them is what tells a best-before from a packing date.
 */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun recogniseText(
    imageProxy: ImageProxy,
    recogniser: com.google.mlkit.vision.text.TextRecognizer,
    onText: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    recogniser.process(image)
        .addOnSuccessListener { result -> if (result.text.isNotBlank()) onText(result.text) }
        .addOnCompleteListener { imageProxy.close() }
}

/** How long to let a scan run before admitting it is not working. */
private const val UNRESOLVED_AFTER_MS = 6_000L

/**
 * Says that the scan is not getting anywhere, and offers the way out.
 *
 * Deliberately not a dead end and not a dismissal: the camera keeps looking
 * while this is up, because the next frame may well succeed. It exists so that
 * failing to read a label is something the person is told about rather than
 * something they sit through.
 */
@Composable
private fun UnresolvedScanNotice(
    sawAnyText: Boolean,
    onEnterByHand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(24.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(
                    if (sawAnyText) {
                        R.string.scanner_unresolved_title_no_date
                    } else {
                        R.string.scanner_unresolved_title_unreadable
                    }
                ),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(
                    if (sawAnyText) {
                        R.string.scanner_unresolved_body_no_date
                    } else {
                        R.string.scanner_unresolved_body_unreadable
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onEnterByHand,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.scanner_enter_by_hand))
            }
        }
    }
}
