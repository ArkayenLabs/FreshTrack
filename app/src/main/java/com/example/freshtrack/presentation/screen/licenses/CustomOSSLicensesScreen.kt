package com.example.freshtrack.presentation.screen.licenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri

data class License(
    val library: String,
    val license: String,
    val url: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomOSSLicensesScreen(
    onNavigateBack: () -> Unit
) {
    val licenses = remember {
        listOf(
            License(
                library = "Jetpack Compose",
                license = "Apache License 2.0",
                url = "https://developer.android.com/jetpack/compose"
            ),
            License(
                library = "Material3",
                license = "Apache License 2.0",
                url = "https://m3.material.io/"
            ),
            License(
                library = "Kotlin",
                license = "Apache License 2.0",
                url = "https://kotlinlang.org/"
            ),
            License(
                library = "Room Database",
                license = "Apache License 2.0",
                url = "https://developer.android.com/jetpack/androidx/releases/room"
            ),
            License(
                library = "Koin",
                license = "Apache License 2.0",
                url = "https://insert-koin.io/"
            ),
            License(
                library = "Navigation Compose",
                license = "Apache License 2.0",
                url = "https://developer.android.com/jetpack/compose/navigation"
            ),
            License(
                library = "Coroutines",
                license = "Apache License 2.0",
                url = "https://github.com/Kotlin/kotlinx.coroutines"
            ),
            License(
                library = "AndroidX Core",
                license = "Apache License 2.0",
                url = "https://developer.android.com/jetpack/androidx"
            ),
            License(
                library = "Lifecycle Components",
                license = "Apache License 2.0",
                url = "https://developer.android.com/jetpack/androidx/releases/lifecycle"
            )
        )
    }

    var expandedLicense by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Open Source Licenses",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header info
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Built with Open Source",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "FreshTrack uses ${licenses.size} open source libraries",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // License items
            items(licenses) { license ->
                val context = LocalContext.current
                LicenseCard(
                    license = license,
                    isExpanded = expandedLicense == license.library,
                    onToggleExpand = {
                        expandedLicense = if (expandedLicense == license.library) {
                            null
                        } else {
                            license.library
                        }
                    },
                    onOpenUrl = { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                )
            }

            // Footer
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Thank you to all open source contributors ❤️",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun LicenseCard(
    license: License,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = license.library,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = license.license,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded)
                        Icons.Default.ArrowBack
                    else
                        Icons.Default.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isExpanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Apache License 2.0",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = getLicenseText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight.times(1.4f)
                )
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { onOpenUrl(license.url) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("View Full License")
                }
            }
        }
    }
}

fun getLicenseText(): String {
    return """
        Licensed under the Apache License, Version 2.0 (the "License");
        you may not use this file except in compliance with the License.
        
        Unless required by applicable law or agreed to in writing, software
        distributed under the License is distributed on an "AS IS" BASIS,
        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        
        See the License for the specific language governing permissions and
        limitations under the License.
    """.trimIndent()
}