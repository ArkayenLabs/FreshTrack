package com.example.freshtrack.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which tab the bottom bar highlights, given the route the graph reports.
 *
 * The subtle case is Kitchen: its declared route carries an optional argument,
 * so a back stack entry names the pattern rather than the filled-in value. A
 * match written against the navigable route would leave the tab unhighlighted
 * the whole time the user was standing on it.
 */
class TopLevelDestinationTest {

    @Test
    fun `each destination matches the route pattern the graph declares`() {
        assertEquals(TopLevelDestination.TODAY, TopLevelDestination.forRoute("today"))
        assertEquals(
            TopLevelDestination.KITCHEN,
            TopLevelDestination.forRoute("product_list?filter={filter}")
        )
        assertEquals(TopLevelDestination.PROGRESS, TopLevelDestination.forRoute("impact"))
    }

    @Test
    fun `kitchen navigates to a route with the argument placeholder resolved away`() {
        // Navigating to the raw pattern would look for a destination literally
        // named "{filter}" and match nothing.
        assertEquals("product_list", TopLevelDestination.KITCHEN.navRoute)
    }

    @Test
    fun `a screen outside the bar highlights nothing`() {
        assertNull(TopLevelDestination.forRoute("settings"))
        assertNull(TopLevelDestination.forRoute("history"))
        assertNull(TopLevelDestination.forRoute(null))
    }
}
