package com.example.freshtrack.presentation.screen.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.example.freshtrack.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val backgroundColor: androidx.compose.ui.graphics.Color,
    val iconBackgroundColor: androidx.compose.ui.graphics.Color
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit = {}
) {
    // Not remembered: the titles are string resources, and a remember lambda is
    // not composition so it cannot read them. Three small objects per
    // recomposition is cheaper than the indirection needed to keep the cache.
    val pages = listOf(
            OnboardingPage(
                title = stringResource(R.string.onboarding_track_title),
                description = stringResource(R.string.onboarding_track_body),
                icon = Icons.Outlined.Inventory2,
                backgroundColor = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                iconBackgroundColor = androidx.compose.ui.graphics.Color(0xFF81C784)
            ),
            OnboardingPage(
                title = stringResource(R.string.onboarding_alerts_title),
                description = stringResource(R.string.onboarding_alerts_body),
                icon = Icons.Outlined.NotificationsActive,
                backgroundColor = androidx.compose.ui.graphics.Color(0xFFFF9800),
                iconBackgroundColor = androidx.compose.ui.graphics.Color(0xFFFFB74D)
            ),
            OnboardingPage(
                title = stringResource(R.string.onboarding_organise_title),
                description = stringResource(R.string.onboarding_organise_body),
                icon = Icons.Outlined.CheckCircle,
                backgroundColor = androidx.compose.ui.graphics.Color(0xFF2196F3),
                iconBackgroundColor = androidx.compose.ui.graphics.Color(0xFF64B5F6)
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp)
        ) {
            // Top Skip Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                AnimatedVisibility(
                    visible = pagerState.currentPage < pages.size - 1,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TextButton(
                        onClick = onSkip,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            stringResource(R.string.onboarding_skip),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                OnboardingPageContent(pages[page])
            }
        }

        // Bottom Controls
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Page Indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pages.forEachIndexed { index, _ ->
                        PageIndicator(
                            isActive = index == pagerState.currentPage,
                            color = when (index) {
                                0 -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                1 -> androidx.compose.ui.graphics.Color(0xFFFF9800)
                                else -> androidx.compose.ui.graphics.Color(0xFF2196F3)
                            }
                        )
                    }
                }

                // Action Button
                Button(
                    onClick = {
                        if (pagerState.currentPage < pages.size - 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    pagerState.currentPage + 1,
                                    animationSpec = tween(durationMillis = 500)
                                )
                            }
                        } else {
                            onComplete()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (pagerState.currentPage) {
                            0 -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                            1 -> androidx.compose.ui.graphics.Color(0xFFFF9800)
                            else -> androidx.compose.ui.graphics.Color(0xFF2196F3)
                        }
                    )
                ) {
                    Text(
                        text = if (pagerState.currentPage < pages.size - 1) stringResource(R.string.onboarding_next) else stringResource(R.string.onboarding_get_started),
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (pagerState.currentPage < pages.size - 1) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon with gradient background
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(page.iconBackgroundColor.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(page.iconBackgroundColor.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(page.backgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = page.icon,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        // Title
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        // Description
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PageIndicator(
    isActive: Boolean,
    color: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .width(if (isActive) 32.dp else 8.dp)
            .height(8.dp)
            .clip(CircleShape)
            .background(
                if (isActive)
                    color
                else
                    MaterialTheme.colorScheme.outlineVariant
            )
    )
}