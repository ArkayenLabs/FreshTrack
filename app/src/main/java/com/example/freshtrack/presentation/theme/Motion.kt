package com.example.freshtrack.presentation.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.NavBackStackEntry

/**
 * How things move, decided once.
 *
 * Two curves, both Material's emphasised pair: things arriving decelerate
 * into place, things leaving accelerate away. One duration for arriving, a
 * shorter one for leaving, because what is leaving has already been read.
 */
object Motion {
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val ENTER_MS = 300
    const val EXIT_MS = 200
    const val CROSSFADE_MS = 180

    /** How far a screen travels while it fades: a nudge, not a slide. */
    private const val ENTER_OFFSET = 0.12f
    private const val EXIT_OFFSET = 0.08f

    /**
     * Going deeper: the new screen arrives from the right and the old one
     * gives way to the left, slightly, with a fade over both. Coming back is
     * the same in reverse. Tabs are places rather than steps, so switching
     * between them is a crossfade with no direction at all.
     */
    fun enter(scope: AnimatedContentTransitionScope<NavBackStackEntry>, topLevel: Set<String>): EnterTransition =
        if (scope.betweenTabs(topLevel)) fadeIn(tween(CROSSFADE_MS))
        else slideInHorizontally(tween(ENTER_MS, easing = EmphasizedDecelerate)) { (it * ENTER_OFFSET).toInt() } +
            fadeIn(tween(ENTER_MS, easing = EmphasizedDecelerate))

    fun exit(scope: AnimatedContentTransitionScope<NavBackStackEntry>, topLevel: Set<String>): ExitTransition =
        if (scope.betweenTabs(topLevel)) fadeOut(tween(CROSSFADE_MS))
        else slideOutHorizontally(tween(EXIT_MS, easing = EmphasizedAccelerate)) { -(it * EXIT_OFFSET).toInt() } +
            fadeOut(tween(EXIT_MS, easing = EmphasizedAccelerate))

    fun popEnter(scope: AnimatedContentTransitionScope<NavBackStackEntry>, topLevel: Set<String>): EnterTransition =
        if (scope.betweenTabs(topLevel)) fadeIn(tween(CROSSFADE_MS))
        else slideInHorizontally(tween(ENTER_MS, easing = EmphasizedDecelerate)) { -(it * ENTER_OFFSET).toInt() } +
            fadeIn(tween(ENTER_MS, easing = EmphasizedDecelerate))

    fun popExit(scope: AnimatedContentTransitionScope<NavBackStackEntry>, topLevel: Set<String>): ExitTransition =
        if (scope.betweenTabs(topLevel)) fadeOut(tween(CROSSFADE_MS))
        else slideOutHorizontally(tween(EXIT_MS, easing = EmphasizedAccelerate)) { (it * EXIT_OFFSET).toInt() } +
            fadeOut(tween(EXIT_MS, easing = EmphasizedAccelerate))

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs(topLevel: Set<String>): Boolean {
        val from = initialState.destination.route
        val to = targetState.destination.route
        // The splash hands over to the first screen; that is an arrival, not a step.
        return (from in topLevel && to in topLevel) || from == null || to == null || from.startsWith("splash")
    }
}

/**
 * Press feedback for anything tappable that is bigger than a button: the
 * whole surface settles by three percent under the finger and springs back.
 * Ripple alone reads as flat on a card; this is what makes it feel held.
 */
fun Modifier.pressable(interactionSource: MutableInteractionSource? = null): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "press"
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}
