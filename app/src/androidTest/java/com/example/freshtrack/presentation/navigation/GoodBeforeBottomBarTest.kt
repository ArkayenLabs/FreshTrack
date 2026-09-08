package com.example.freshtrack.presentation.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The bar itself, rendered in isolation.
 *
 * Worth testing separately from the graph because the failure it is most likely
 * to have — a tab that never looks selected because the route it compares
 * against is not the one the back stack reports — is invisible in a compile and
 * easy to miss by eye on the tab you happen to open first.
 */
class GoodBeforeBottomBarTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun allThreeDestinationsAreShown() {
        composeTestRule.setContent {
            GoodBeforeBottomBar(currentRoute = Screen.Today.route, onNavigate = {})
        }

        composeTestRule.onNodeWithText("Today").assertExists()
        composeTestRule.onNodeWithText("Kitchen").assertExists()
        composeTestRule.onNodeWithText("Progress").assertExists()
    }

    @Test
    fun theKitchenTabReadsAsSelectedWhileStandingOnIt() {
        // Kitchen's declared route carries an optional argument, so this is the
        // case where a naive route comparison silently fails.
        composeTestRule.setContent {
            GoodBeforeBottomBar(
                currentRoute = Screen.ProductList.route,
                onNavigate = {}
            )
        }

        composeTestRule.onNodeWithText("Kitchen").assertIsSelected()
    }

    @Test
    fun tappingATabReportsThatDestination() {
        var chosen: TopLevelDestination? = null
        composeTestRule.setContent {
            GoodBeforeBottomBar(
                currentRoute = Screen.Today.route,
                onNavigate = { chosen = it }
            )
        }

        composeTestRule.onNodeWithText("Progress").performClick()

        assertEquals(TopLevelDestination.PROGRESS, chosen)
    }
}
