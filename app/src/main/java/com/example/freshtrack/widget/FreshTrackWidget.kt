package com.example.freshtrack.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.freshtrack.MainActivity
import com.example.freshtrack.data.repository.ProductRepository
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Home-screen widget showing what is about to expire.
 *
 * Reads Room directly rather than waiting on sync: the widget must render the
 * same offline as online, and a guest with no account still gets one.
 */
class FreshTrackWidget : GlanceAppWidget(), KoinComponent {

    private val productRepository: ProductRepository by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read before provideContent: Glance expects a snapshot, not a stream.
        val products = runCatching { productRepository.getAllProducts().first() }
            .getOrDefault(emptyList())
        val state = WidgetContent.build(products)

        provideContent { WidgetBody(state) }
    }

    @Composable
    private fun WidgetBody(state: WidgetContent.State) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                // The whole widget opens the app; a small target on a home
                // screen is easy to miss.
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Text(
                text = "FreshTrack",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = state.headline,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                )
            )

            if (state.isAllClear) {
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = "Nothing needs using up in the next week.",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            } else {
                Spacer(GlanceModifier.height(8.dp))
                state.items.forEach { item -> ItemRow(item) }
            }
        }
    }

    @Composable
    private fun ItemRow(item: WidgetContent.Item) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.name,
                maxLines = 1,
                style = TextStyle(
                    fontSize = 13.sp,
                    color = GlanceTheme.colors.onSurface
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = item.timing,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = if (item.isOverdue) FontWeight.Bold else FontWeight.Normal,
                    // Overdue is the one thing worth colouring; everything else
                    // stays neutral so the emphasis means something. Using the
                    // theme's error colour keeps it readable in light and dark
                    // without hardcoding a pair of hex values.
                    color = if (item.isOverdue) {
                        GlanceTheme.colors.error
                    } else {
                        GlanceTheme.colors.onSurfaceVariant
                    }
                )
            )
        }
    }
}

/** Registers the widget with the system. */
class FreshTrackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FreshTrackWidget()
}
