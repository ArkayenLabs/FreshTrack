package com.example.freshtrack.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.freshtrack.R
import com.example.freshtrack.domain.model.ExpiryUrgency
import com.example.freshtrack.domain.model.Item
import com.example.freshtrack.presentation.theme.*
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import com.example.freshtrack.presentation.theme.pressable

/**
 * One item in a list.
 *
 * [today] is passed in rather than read from the system inside the card, so
 * every row in a list is dated against the same day. Reading the clock per card
 * meant a long list rendering across midnight could show two identical dates
 * with different day counts.
 */
@Composable
fun ProductCard(
    product: Item,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val press = remember { MutableInteractionSource() }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .pressable(press)
            .clickable(interactionSource = press, indication = LocalIndication.current, onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.medium // More rounded for modern look
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon + Info Section (60% width)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Icon with colored background
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(getCategoryColor(product.category).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(product.category),
                        contentDescription = product.category,
                        tint = getCategoryColor(product.category),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Product Info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quantity with icon
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${product.quantity}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)

                        // Expiry date with icon
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // Same test Today uses, so a date reads the same way
                            // on every screen: a guess is disclosed as one rather
                            // than borrowing the voice of a printed date.
                            val unconfirmed = product.needsDateReview || product.hasEstimatedDate
                            Text(
                                text = formatDateShort(product.expiry.value, today) +
                                    if (unconfirmed) {
                                        stringResource(R.string.date_estimated_short_suffix)
                                    } else {
                                        ""
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Expiry Badge (10% width - accent color)
            ExpiryBadge(
                daysRemaining = product.daysUntilExpiry(today),
                urgency = product.urgency(today)
            )
        }
    }
}

/**
 * Modern Expiry Badge - Compact and colorful
 */
@Composable
fun ExpiryBadge(
    daysRemaining: Long,
    urgency: ExpiryUrgency,
    modifier: Modifier = Modifier
) {
    // The text colour travels with the fill rather than being assumed white:
    // the dark theme puts dark text on a light badge, and hard-coding white
    // here is what made these unreadable in one theme or the other.
    val palette = GoodBefore.urgency
    val (backgroundColor, textColor) = when (urgency) {
        ExpiryUrgency.SAFE -> palette.safe to palette.onSafe
        ExpiryUrgency.WARNING -> palette.warning to palette.onWarning
        ExpiryUrgency.CRITICAL -> palette.critical to palette.onCritical
        ExpiryUrgency.EXPIRED -> palette.expired to palette.onExpired
    }

    val text = when {
        daysRemaining < 0 -> "!"
        daysRemaining == 0L -> "0d"
        daysRemaining < 10 -> "${daysRemaining}d"
        else -> "9+"
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = textColor
        )
    }
}

/**
 * Minimalist Category Chip
 */
@Composable
fun CategoryChip(
    category: String,
    modifier: Modifier = Modifier
) {
    val categoryColor = getCategoryColor(category)

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = categoryColor.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getCategoryIcon(category),
                contentDescription = null,
                tint = categoryColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = category,
                style = MaterialTheme.typography.labelSmall,
                color = categoryColor
            )
        }
    }
}

/**
 * Modern Empty State with icon
 */
@Composable
fun EmptyState(
    title: String,
    message: String,
    icon: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Default.Inventory2,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
    },
    actionButton: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (actionButton != null) {
            Spacer(modifier = Modifier.height(24.dp))
            actionButton()
        }
    }
}

/**
 * Clean Loading State
 */
@Composable
fun LoadingState(
    message: String = "Loading...",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Modern Stat Card with icon - Icon first design
 */
@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    backgroundColor: Color,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor.copy(alpha = 0.12f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = backgroundColor
                )

                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(backgroundColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = backgroundColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// Helper functions
// Colours for the current food-only categories. The old mapping still referenced
// removed categories (food/medicine/cosmetics), so everything but Beverages fell
// through to the same colour.
private fun getCategoryColor(category: String): Color {
    return when (category.lowercase()) {
        "fresh produce" -> Color(0xFF4CAF50)
        "dairy & eggs" -> Color(0xFF2196F3)
        "meat & fish" -> Color(0xFFE53935)
        "ready meals" -> Color(0xFF8E24AA)
        "bakery" -> Color(0xFFFF9800)
        "beverages" -> Color(0xFF00BCD4)
        "store cupboard" -> Color(0xFF795548)
        "leftovers" -> Color(0xFFFF5722)
        else -> CategoryOther
    }
}

private fun getCategoryIcon(category: String): ImageVector {
    return when (category.lowercase()) {
        "fresh produce" -> Icons.Default.Eco
        "dairy & eggs" -> Icons.Default.WaterDrop
        "meat & fish" -> Icons.Default.SetMeal
        "ready meals" -> Icons.Default.LunchDining
        "bakery" -> Icons.Default.BakeryDining
        "beverages" -> Icons.Default.LocalCafe
        "store cupboard" -> Icons.Default.Kitchen
        "leftovers" -> Icons.Default.TakeoutDining
        else -> Icons.Default.Category
    }
}

/**
 * Short on the card, but never ambiguous. A date in another year carries the
 * year, because "Sep 10" for something that keeps until next September reads
 * as today, and the badge beside it is the only thing saying otherwise.
 */
private fun formatDateShort(date: LocalDate, today: LocalDate): String {
    val pattern = if (date.year == today.year) "MMM dd" else "MMM dd, yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}