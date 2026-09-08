package com.example.freshtrack.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

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
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    TODAY(
        matchRoute = Screen.Today.route,
        navRoute = Screen.Today.route,
        label = "Today",
        selectedIcon = Icons.Filled.Today,
        unselectedIcon = Icons.Outlined.Today
    ),
    KITCHEN(
        matchRoute = Screen.ProductList.route,
        navRoute = Screen.ProductList.createRoute(),
        label = "Kitchen",
        selectedIcon = Icons.Filled.Kitchen,
        unselectedIcon = Icons.Outlined.Kitchen
    ),
    PROGRESS(
        matchRoute = Screen.Impact.route,
        navRoute = Screen.Impact.route,
        label = "Progress",
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
 * The bottom bar shown on the three top-level destinations, and nowhere else.
 *
 * It is passed into each screen's own Scaffold rather than wrapping the NavHost
 * in a second one. Nesting Scaffolds means two of them competing to apply the
 * same window insets, which is how content ends up either double-padded or
 * underneath the navigation bar.
 */
@Composable
fun GoodBeforeBottomBar(
    currentRoute: String?,
    onNavigate: (TopLevelDestination) -> Unit
) {
    val current = TopLevelDestination.forRoute(currentRoute)

    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected = destination == current
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) {
                            destination.selectedIcon
                        } else {
                            destination.unselectedIcon
                        },
                        contentDescription = null
                    )
                },
                label = { Text(destination.label) }
            )
        }
    }
}
