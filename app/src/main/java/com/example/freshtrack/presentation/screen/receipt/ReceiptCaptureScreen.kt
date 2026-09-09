package com.example.freshtrack.presentation.screen.receipt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.freshtrack.R
import com.example.freshtrack.domain.capture.ReceiptRow
import com.example.freshtrack.domain.capture.RowDecision
import com.example.freshtrack.presentation.viewmodel.ReceiptError
import com.example.freshtrack.presentation.viewmodel.ReceiptPhase
import com.example.freshtrack.presentation.viewmodel.ReceiptReviewUiState
import com.example.freshtrack.presentation.viewmodel.ReceiptReviewViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.concurrent.Executors

/**
 * A shop, reviewed before it becomes a kitchen.
 *
 * Capture and review are one screen rather than two routes because the review
 * state cannot survive being passed through a navigation argument, and because
 * "scan again" has to be a step backwards rather than a new journey.
 *
 * Everything here happens on the device. The image is read where it was taken
 * and deleted afterwards; nothing about the receipt leaves the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptCaptureScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReceiptReviewViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val errorMessage = state.error?.let { stringResource(errorLabel(it)) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.receipt_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state.phase == ReceiptPhase.REVIEW) {
                CommitBar(state = state, onCommit = viewModel::commit)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state.phase) {
                ReceiptPhase.CAPTURE -> CaptureStage(
                    onImage = { uri ->
                        viewModel.onReadingStarted()
                        recogniseReceipt(
                            context = context,
                            uri = uri,
                            onText = viewModel::onTextRecognised,
                            onFailure = viewModel::onReadingFailed
                        )
                    }
                )

                ReceiptPhase.READING -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.receipt_reading))
                    }
                }

                ReceiptPhase.REVIEW -> ReviewStage(
                    state = state,
                    viewModel = viewModel,
                    onStartOver = viewModel::startOver
                )

                ReceiptPhase.SAVED -> SavedStage(state = state, onDone = onNavigateBack)
            }
        }
    }
}

/**
 * The camera, and the way out of it.
 *
 * A photo of a receipt is a still, not a video: a till receipt is long and
 * narrow and a frame-by-frame analyser reads whichever third of it happens to
 * be in shot. Choosing an existing image is offered equally prominently,
 * because a photo taken in the shop is the commonest way this gets used.
 */
@Composable
private fun CaptureStage(onImage: (Uri) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImage(uri) }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            runCatching {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture
                                )
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.scanner_permission_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.receipt_permission_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text(stringResource(R.string.scanner_grant_permission))
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.receipt_capture_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    val target = File(context.cacheDir, "receipt-${System.currentTimeMillis()}.jpg")
                    imageCapture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(target).build(),
                        executor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                onImage(output.savedUri ?: Uri.fromFile(target))
                            }

                            override fun onError(exception: ImageCaptureException) {
                                target.delete()
                            }
                        }
                    )
                },
                enabled = hasCameraPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Camera, contentDescription = null)
                Text(
                    text = stringResource(R.string.receipt_take_photo),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            OutlinedButton(
                onClick = { pickImage.launch(arrayOf("image/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Image, contentDescription = null)
                Text(
                    text = stringResource(R.string.receipt_choose_image),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/** The sheet itself: every row, and everything that could not be made one. */
@Composable
private fun ReviewStage(
    state: ReceiptReviewUiState,
    viewModel: ReceiptReviewViewModel,
    onStartOver: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.receipt_review_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.hasUnresolvedRows) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = pluralStringResource(
                                R.plurals.receipt_unresolved_banner,
                                state.unresolvedCount,
                                state.unresolvedCount
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        items(state.rows, key = { it.candidateId }) { row ->
            ReceiptRowCard(row = row, state = state, viewModel = viewModel)
        }

        if (state.unreadableLines.isNotEmpty()) {
            item { UnreadableLinesCard(lines = state.unreadableLines) }
        }

        item {
            TextButton(onClick = onStartOver, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.receipt_start_over))
            }
        }
    }
}

/**
 * One row, with everything about it that is open to question.
 *
 * A row with no date is shown as unfinished rather than being hidden or given
 * one — this is the case the whole screen exists for, and the difference
 * between a tracker that admits it does not know and one that makes something
 * up.
 */
@Composable
private fun ReceiptRowCard(
    row: ReceiptRow,
    state: ReceiptReviewUiState,
    viewModel: ReceiptReviewViewModel
) {
    val skipped = row.decision == RowDecision.SKIP

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = when {
            skipped -> MaterialTheme.colorScheme.surfaceContainerLowest
            row.isUnresolved -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = row.name,
                onValueChange = { viewModel.updateName(row.candidateId, it) },
                label = { Text(stringResource(R.string.receipt_row_name)) },
                singleLine = true,
                enabled = !skipped,
                modifier = Modifier.fillMaxWidth()
            )

            // A stepper rather than a text field. Typing a number into a field
            // backed by an Int means an empty string has nowhere to go, so
            // backspace silently does nothing and the field looks frozen — and
            // a sheet of ten rows is a lot of keyboard for numbers that are
            // almost always one or two.
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.receipt_row_quantity),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = { viewModel.updateQuantity(row.candidateId, row.quantity - 1) },
                    enabled = !skipped && row.quantity > 1
                ) {
                    Icon(
                        Icons.Outlined.Remove,
                        stringResource(R.string.receipt_quantity_less)
                    )
                }
                Text(
                    text = row.quantity.toString(),
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(
                    onClick = { viewModel.updateQuantity(row.candidateId, row.quantity + 1) },
                    enabled = !skipped
                ) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.receipt_quantity_more))
                }
                row.measureNote?.let { note ->
                    // A label, not a control. It says what was on the receipt.
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickerField(
                    label = stringResource(R.string.receipt_row_category),
                    value = row.category ?: stringResource(R.string.receipt_not_chosen),
                    enabled = !skipped,
                    options = state.categories.map { it to it },
                    onSelected = { viewModel.updateCategory(row.candidateId, it) },
                    modifier = Modifier.weight(1f)
                )
                PickerField(
                    label = stringResource(R.string.receipt_row_location),
                    value = state.locations.find { it.id == row.locationId }?.name
                        ?: stringResource(R.string.receipt_no_location),
                    enabled = !skipped,
                    options = state.locations.map { it.id to it.name },
                    onSelected = { viewModel.updateLocation(row.candidateId, it) },
                    modifier = Modifier.weight(1f)
                )
            }

            DateLine(row = row, enabled = !skipped, viewModel = viewModel)

            row.lineTotalMinor?.let { total ->
                state.currency?.let { code ->
                    Text(
                        text = stringResource(R.string.receipt_line_total, formatMoney(total, code)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (row.needsAttention && !skipped) {
                Text(
                    text = stringResource(R.string.receipt_worth_a_look),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            row.duplicate?.let { duplicate ->
                DuplicateChoices(
                    row = row,
                    alreadyHave = duplicate.quantity,
                    onDecision = { viewModel.setDecision(row.candidateId, it) }
                )
            }

            TextButton(
                onClick = {
                    viewModel.setDecision(
                        row.candidateId,
                        if (skipped) RowDecision.ADD else RowDecision.SKIP
                    )
                }
            ) {
                Text(
                    stringResource(
                        if (skipped) R.string.receipt_put_back else R.string.receipt_leave_out
                    )
                )
            }
        }
    }
}

/**
 * The date, and where it came from.
 *
 * An estimate says so and says why, so it can be disagreed with. A row with
 * nothing says that too, in the place the date would be, rather than looking
 * like a row that is simply still loading.
 */
@Composable
private fun DateLine(
    row: ReceiptRow,
    enabled: Boolean,
    viewModel: ReceiptReviewViewModel
) {
    var picking by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (row.expiry == null) {
                    Icons.Outlined.ErrorOutline
                } else {
                    Icons.Outlined.CalendarToday
                },
                contentDescription = null,
                tint = if (row.expiry == null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.expiry?.format(REVIEW_DATE_FORMAT)
                        ?: stringResource(R.string.receipt_needs_a_date),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = when {
                        row.expiry == null -> stringResource(R.string.receipt_no_basis)
                        row.dateIsUserChosen -> stringResource(R.string.receipt_date_you_chose)
                        else -> stringResource(
                            R.string.receipt_date_estimated,
                            row.estimateBasis.orEmpty()
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { picking = true }, enabled = enabled) {
                Text(stringResource(R.string.receipt_pick_date))
            }
        }
    }

    if (picking) {
        val today = LocalDate.now()
        // Material works in UTC millis. Converting through UTC on both sides
        // keeps the round trip exact; mixing the local zone into one of them is
        // what makes a picked date come back a day early.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (row.expiry ?: today).toUtcMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !utcTimeMillis.toLocalDateUtc().isBefore(today)

                override fun isSelectableYear(year: Int): Boolean = year >= today.year
            }
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            viewModel.chooseDate(row.candidateId, it.toLocalDateUtc())
                        }
                        picking = false
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * What to do about something that is already in the kitchen.
 *
 * Three answers, all of them explicit. Adding to the batch, keeping it as its
 * own, and leaving it out are genuinely different intentions, and guessing
 * between them is how a bulk import loses a shop.
 */
@Composable
private fun DuplicateChoices(
    row: ReceiptRow,
    alreadyHave: Int,
    onDecision: (RowDecision) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = pluralStringResource(
                    R.plurals.receipt_duplicate_title,
                    alreadyHave,
                    alreadyHave
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = row.decision == RowDecision.MERGE,
                    onClick = { onDecision(RowDecision.MERGE) },
                    label = { Text(stringResource(R.string.receipt_duplicate_merge)) }
                )
                FilterChip(
                    selected = row.decision == RowDecision.ADD,
                    onClick = { onDecision(RowDecision.ADD) },
                    label = { Text(stringResource(R.string.receipt_duplicate_separate)) }
                )
                FilterChip(
                    selected = row.decision == RowDecision.SKIP,
                    onClick = { onDecision(RowDecision.SKIP) },
                    label = { Text(stringResource(R.string.receipt_duplicate_skip)) }
                )
            }
        }
    }
}

/** Lines that looked like shopping and could not be read, shown rather than lost. */
@Composable
private fun UnreadableLinesCard(lines: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = pluralStringResource(
                    R.plurals.receipt_unreadable_title,
                    lines.size,
                    lines.size
                ),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.receipt_unreadable_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The commit, and the reason it is unavailable when it is. */
@Composable
private fun CommitBar(state: ReceiptReviewUiState, onCommit: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onCommit,
                enabled = state.canCommit,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isWorking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        pluralStringResource(
                            R.plurals.receipt_commit,
                            state.savedCount,
                            state.savedCount
                        )
                    )
                }
            }
            if (state.hasUnresolvedRows) {
                Text(
                    text = stringResource(R.string.receipt_commit_blocked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** What happened, in the three ways a row could have ended. */
@Composable
private fun SavedStage(state: ReceiptReviewUiState, onDone: () -> Unit) {
    val result = state.result ?: return
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = stringResource(R.string.receipt_saved_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = pluralStringResource(
                R.plurals.receipt_saved_added,
                result.added,
                result.added
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        if (result.merged > 0) {
            Text(
                text = pluralStringResource(
                    R.plurals.receipt_saved_merged,
                    result.merged,
                    result.merged
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (result.left > 0) {
            Text(
                text = pluralStringResource(
                    R.plurals.receipt_saved_left,
                    result.left,
                    result.left
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = onDone, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.action_done))
        }
    }
}

/** A labelled read-only field that opens a menu of choices. */
@Composable
private fun PickerField(
    label: String,
    value: String,
    enabled: Boolean,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(text = label, style = MaterialTheme.typography.labelSmall)
                Text(text = value, style = MaterialTheme.typography.bodyMedium)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelected(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Reads a receipt image and hands back everything printed on it.
 *
 * The file is deleted as soon as it has been read. A photograph of a receipt
 * carries a card fragment, a store, a time and a place; keeping it around after
 * the only thing wanted from it is the shopping would be storing all of that
 * for no reason.
 */
private fun recogniseReceipt(
    context: Context,
    uri: Uri,
    onText: (String) -> Unit,
    onFailure: () -> Unit
) {
    val recogniser = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
    if (image == null) {
        recogniser.close()
        onFailure()
        return
    }

    recogniser.process(image)
        .addOnSuccessListener { result ->
            if (result.text.isBlank()) onFailure() else onText(result.text)
        }
        .addOnFailureListener { onFailure() }
        .addOnCompleteListener {
            recogniser.close()
            // Only ours to remove. A picture the person chose from their own
            // storage stays exactly where they put it.
            if (uri.scheme == "file") runCatching { uri.path?.let { File(it).delete() } }
        }
}

/** Money as the reader's locale writes it, from minor units. */
private fun formatMoney(minorUnits: Int, currencyCode: String): String {
    val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
        ?: return minorUnits.toString()
    val format = NumberFormat.getCurrencyInstance().apply { this.currency = currency }
    return format.format(minorUnits / 100.0)
}

private val REVIEW_DATE_FORMAT: DateTimeFormatter
    get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateUtc(): LocalDate =
    java.time.Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private fun errorLabel(error: ReceiptError): Int = when (error) {
    ReceiptError.NOTHING_READABLE -> R.string.receipt_error_nothing_readable
    ReceiptError.NO_SHOPPING_FOUND -> R.string.receipt_error_no_shopping
    ReceiptError.SAVE_FAILED -> R.string.receipt_error_save_failed
}
