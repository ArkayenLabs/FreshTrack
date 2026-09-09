package com.example.freshtrack.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.freshtrack.R

/**
 * The three places the app is ever "at".
 *
 * Today answers what to do, Kitchen answers what is there, Progress answers what
 * came of it. Capture is deliberately not here: adding food is an action taken
 * from a screen, not a fourth place to be, so it stays a button rather than
 * spending a permanent slot in the bar.
 */
enum class TopLevelDestination(
    /**
     * The route *pattern*, as the graph declares it, used to decide which tab is
     * current. Kitchen's pattern carries an optional argument, so a live back
     * stack entry reads "product_list?filter={filter}" whether or not a filter
     * was supplied — matching the pattern lights the tab up in both cases.
     */
    val matchRoute: String,
    /**
     * What to actually navigate to, which is not the same string: a pattern
     * still containing "{filter}" would be taken literally and match nothing.
     */
    val navRoute: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    TODAY(
        matchRoute = Screen.Today.route,
        navRoute = Screen.Today.route,
        labelRes = R.string.nav_today,
        selectedIcon = Icons.Filled.Today,
        unselectedIcon = Icons.Outlined.Today
    ),
    KITCHEN(
        matchRoute = Screen.ProductList.route,
        navRoute = Screen.ProductList.createRoute(),
        labelRes = R.string.nav_kitchen,
        selectedIcon = Icons.Filled.Kitchen,
        unselectedIcon = Icons.Outlined.Kitchen
    ),
    PROGRESS(
        matchRoute = Screen.Impact.route,
        navRoute = Screen.Impact.route,
        labelRes = R.string.nav_progress,
        // Not Insights: that icon carries sparkles, and a sparkle motif as a
        // permanent tab icon is the "signal intelligence with decoration" habit
        // the design system rules out. A chart says what this screen holds.
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart
    );

    companion object {
        fun forRoute(route: String?): TopLevelDestination? =
            entries.firstOrNull { it.matchRoute == route }
    }
}

/**
 * A bar that floats clear of the screen edge rather than sitting against it.
 *
 * The reason it can do that safely is the inset padding below. Android gives
 * the system navigation a strip at the bottom of the screen, and that strip is
 * roughly twice as tall for someone using three buttons as for someone using
 * gestures. A floating bar with a hard-coded bottom margin therefore looks
 * correct on whichever device it was designed on and sits on top of the back
 * button on the other. Padding by [WindowInsets.navigationBars] first and
 * adding the floating margin second means the gap is measured from whatever the
 * system actually reserved.
 *
 * It stays in the Scaffold's bottomBar slot, so the Scaffold measures it and
 * pads screen content by its full height. A bar that floated *over* the content
 * instead would hide the last row of every list.
 */
@Composable
fun GoodBeforeBottomBar(
    currentRoute: String?,
    onNavigate: (TopLevelDestination) -> Unit
) {
    val current = TopLevelDestination.forRoute(currentRoute)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopLevelDestination.entries.forEach { destination ->
                    BottomBarItem(
                        destination = destination,
                        selected = destination == current,
                        onClick = { onNavigate(destination) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val indicator by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        label = "indicator"
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "content"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            // selectable merges its descendants, so the label below is what
            // TalkBack reads, and Role.Tab plus the selected flag are what turn
            // it into "Today, tab, selected" rather than a bare button.
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick
            )
            // The whole column is the target, which keeps it comfortably past
            // the 48dp minimum in both directions.
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(28.dp)
                .clip(CircleShape)
                .background(indicator),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) {
                    destination.selectedIcon
                } else {
                    destination.unselectedIcon
                },
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = content
        )
    }
}
