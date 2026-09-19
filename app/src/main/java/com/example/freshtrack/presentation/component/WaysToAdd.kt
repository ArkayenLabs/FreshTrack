package com.example.freshtrack.presentation.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.freshtrack.R
import com.example.freshtrack.presentation.theme.Arrive
import com.example.freshtrack.presentation.theme.pressable

/**
 * The three ways food gets into the app, each a row that goes straight into
 * that flow. This is the first thing a new person sees, on Today and again
 * in the Kitchen, so it does the job an empty box with a sentence under it
 * does not: it says what to do next and takes them there in one tap.
 *
 * [firstIndex] lets a screen that shows something above the rows keep the
 * arrival stagger continuous.
 */
@Composable
fun WaysToAdd(
    onScanBarcode: () -> Unit,
    onScanReceipt: () -> Unit,
    onTypeIn: () -> Unit,
    modifier: Modifier = Modifier,
    firstIndex: Int = 0
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Arrive(index = firstIndex) {
            WayRow(
                icon = Icons.Outlined.QrCodeScanner,
                title = stringResource(R.string.ways_scan_title),
                body = stringResource(R.string.ways_scan_body),
                onClick = onScanBarcode
            )
        }
        Arrive(index = firstIndex + 1) {
            WayRow(
                icon = Icons.Outlined.ReceiptLong,
                title = stringResource(R.string.ways_receipt_title),
                body = stringResource(R.string.ways_receipt_body),
                onClick = onScanReceipt
            )
        }
        Arrive(index = firstIndex + 2) {
            WayRow(
                icon = Icons.Outlined.Keyboard,
                title = stringResource(R.string.ways_type_title),
                body = stringResource(R.string.ways_type_body),
                onClick = onTypeIn
            )
        }
    }
}

@Composable
private fun WayRow(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit
) {
    val press = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(press)
            .clip(MaterialTheme.shapes.medium)
            .clickable(interactionSource = press, indication = LocalIndication.current, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
