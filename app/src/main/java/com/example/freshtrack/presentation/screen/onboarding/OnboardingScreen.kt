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
import androidx.compose.ui.text.font.FontWeight
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
    onComplete: () -> Unit
) {
    val pages = remember {
        listOf(
            OnboardingPage(
                title = "Track Your Products",
                description = "Keep track of all your food, medicines, cosmetics, and beverages in one place. Never let anything go to waste!",
                icon = Icons.Outlined.Inventory2,
                backgroundColor = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                iconBackgroundColor = androidx.compose.ui.graphics.Color(0xFF81C784)
            ),
            OnboardingPage(
                title = "Smart Expiry Alerts",
                description = "Get timely notifications before your products expire. Stay informed and save money by using items on time.",
                icon = Icons.Outlined.NotificationsActive,
                backgroundColor = androidx.compose.ui.graphics.Color(0xFFFF9800),
                iconBackgroundColor = androidx.compose.ui.graphics.Color(0xFFFFB74D)
            ),
            OnboardingPage(
                title = "Stay Organized",
                description = "Organize by categories, scan barcodes, and manage quantities effortlessly. Make expiry tracking simple and stress-free!",
                icon = Icons.Outlined.CheckCircle,
                backgroundColor = androidx.compose.ui.graphics.Color(0xFF2196F3),
                iconBackgroundColor = androidx.compose.ui.graphics.Color(0xFF64B5F6)
            )
        )
    }

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
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                AnimatedVisibility(
                    visible = pagerState.currentPage < pages.size - 1,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TextButton(
                        onClick = onComplete,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Skip",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
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
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (pagerState.currentPage) {
                            0 -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                            1 -> androidx.compose.ui.graphics.Color(0xFFFF9800)
                            else -> androidx.compose.ui.graphics.Color(0xFF2196F3)
                        }
                    )
                ) {
                    Text(
                        text = if (pagerState.currentPage < pages.size - 1) "Next" else "Get Started",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
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
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        // Description
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight.times(1.5f)
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