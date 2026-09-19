package com.example.freshtrack.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.freshtrack.MainActivity
import com.example.freshtrack.R
import com.example.freshtrack.data.repository.ItemRepository
import com.example.freshtrack.presentation.theme.DarkColors
import com.example.freshtrack.presentation.theme.DarkUrgency
import com.example.freshtrack.presentation.theme.LightColors
import com.example.freshtrack.presentation.theme.LightUrgency
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Home-screen widget showing what is about to expire.
 *
 * Reads Room directly rather than waiting on sync: the widget must render the
 * same offline as online, and a guest with no account still gets one.
 *
 * Three layouts, picked by the space the launcher gives: a 2x2 square says
 * how many things need using; a 4x2 lists two of them; anything taller lists
 * five. Every layout keeps the same header — the app's icon, its name, and
 * one action, Add — so it reads as the same widget at every size. Rows open
 * their item; the rest of the widget opens Today. Colours are the app's own
 * palette, light and dark, so it reads as a piece of the app.
 */
class FreshTrackWidget : GlanceAppWidget(), KoinComponent {

    private val itemRepository: ItemRepository by inject()

    /** The real size, so the row count comes from the space the launcher actually gave. */
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read before provideContent: Glance expects a snapshot, not a stream.
        val products = runCatching { itemRepository.observeActiveItems().first() }
            .getOrDefault(emptyList())
        val state = WidgetContent.build(products, LocalDate.now())

        provideContent {
            GlanceTheme(colors = WidgetColors.providers()) {
                WidgetBody(state)
            }
        }
    }

    @Composable
    private fun WidgetBody(state: WidgetContent.State) {
        val size = LocalSize.current
        val compact = size.width < COMPACT_BELOW
        // Header, its spacer and the headline take about 84dp; each row 30.
        val rows = ((size.height.value - 84) / 30).toInt().coerceIn(1, WidgetContent.MAX_ITEMS)
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .appWidgetBackground()
                .systemCornerRadius()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Header(compact)
            Spacer(GlanceModifier.height(if (compact) 6.dp else 8.dp))
            when {
                state.isEmptyKitchen -> EmptyKitchen(compact)
                state.isAllClear -> AllClear(state, compact, rows)
                compact -> Count(state)
                else -> Rows(state, rows)
            }
        }
    }

    /** Icon, name, one action. The action has a full 48dp target; the mark inside it is smaller. */
    @Composable
    private fun Header(compact: Boolean) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = GlanceModifier.size(20.dp)
            )
            if (!compact) {
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = LocalContext.current.getString(R.string.app_name),
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            Box(
                modifier = GlanceModifier
                    .size(40.dp)
                    .clickable(actionStartActivity<MainActivity>(actionParametersOf(NAVIGATE_TO to "add"))),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(30.dp)
                        .cornerRadius(15.dp)
                        .background(GlanceTheme.colors.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_add),
                        contentDescription = LocalContext.current.getString(R.string.widget_add),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                        modifier = GlanceModifier.size(18.dp)
                    )
                }
            }
        }
    }

    /** 2x2: one number and what it means. */
    @Composable
    private fun Count(state: WidgetContent.State) {
        val context = LocalContext.current
        Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
            Text(
                text = state.urgentCount.toString(),
                style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface)
            )
            Text(
                text = context.resources.getQuantityString(R.plurals.widget_need_using, state.urgentCount),
                maxLines = 2,
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
            )
        }
    }

    @Composable
    private fun Rows(state: WidgetContent.State, rows: Int) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = state.headline,
                maxLines = 1,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface)
            )
            Spacer(GlanceModifier.height(6.dp))
            val shown = state.items.take(rows)
            shown.forEach { item -> ItemRow(item) }
            val more = state.urgentCount - shown.size
            val spare = rows - shown.size
            when {
                more > 0 -> Text(
                    text = LocalContext.current.resources.getQuantityString(R.plurals.widget_more, more, more),
                    style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    modifier = GlanceModifier.padding(top = 2.dp)
                )
                // Room to spare: what is coming up next, so a widget with one
                // urgent item is not mostly blank.
                spare >= 2 && state.upcoming.isNotEmpty() -> {
                    Text(
                        text = LocalContext.current.getString(R.string.widget_coming_up),
                        style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
                        modifier = GlanceModifier.padding(top = 4.dp)
                    )
                    state.upcoming.take(spare - 1).forEach { item -> ItemRow(item) }
                }
            }
        }
    }

    /** A dot for how close it is, the name, and when. The whole row opens the item. */
    @Composable
    private fun ItemRow(item: WidgetContent.Item) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(30.dp)
                .clickable(actionStartActivity<MainActivity>(actionParametersOf(OPEN_ITEM to item.id))),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(8.dp)
                    .cornerRadius(4.dp)
                    .background(WidgetColors.urgency(item.urgency))
            ) {}
            Spacer(GlanceModifier.width(10.dp))
            Text(
                text = item.name,
                maxLines = 1,
                style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurface),
                modifier = GlanceModifier.defaultWeight()
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = item.timing,
                maxLines = 1,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = if (item.isOverdue) FontWeight.Bold else FontWeight.Normal,
                    color = if (item.isOverdue) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
    }

    /** Nothing is close. Say so, and say what is next, so the widget is never a blank tick. */
    @Composable
    private fun AllClear(state: WidgetContent.State, compact: Boolean, rows: Int) {
        val context = LocalContext.current
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_check),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    modifier = GlanceModifier.size(18.dp)
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = context.getString(R.string.widget_all_clear),
                    maxLines = if (compact) 2 else 1,
                    style = TextStyle(fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface)
                )
            }
            state.nextUp?.let { next ->
                Spacer(GlanceModifier.height(if (compact) 4.dp else 8.dp))
                if (compact) {
                    Text(
                        text = context.getString(R.string.widget_next_up, next.name, next.timing),
                        maxLines = 2,
                        style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
                        modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>(actionParametersOf(OPEN_ITEM to next.id)))
                    )
                } else {
                    Text(
                        text = context.getString(R.string.widget_coming_up),
                        style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                    state.upcoming.take(rows - 1).forEach { item -> ItemRow(item) }
                }
            }
        }
    }

    /** First run: the widget's one job is to get the first item in. */
    @Composable
    private fun EmptyKitchen(compact: Boolean) {
        val context = LocalContext.current
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = context.getString(R.string.widget_empty_title),
                maxLines = 2,
                style = TextStyle(fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface)
            )
            if (!compact) {
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = context.getString(R.string.widget_empty_body),
                    maxLines = 2,
                    style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
                )
            }
        }
    }

    /** The launcher's own radius on 12+, so the widget matches its neighbours; the app's large shape before that. */
    private fun GlanceModifier.systemCornerRadius(): GlanceModifier =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) cornerRadius(android.R.dimen.system_app_widget_background_radius)
        else cornerRadius(24.dp)

    companion object {
        /** Narrower than this (a 2-cell width) and there is room for a number, not a list. */
        val COMPACT_BELOW = 200.dp

        /** Intent extras read by [MainActivity]. */
        val OPEN_ITEM = ActionParameters.Key<String>(MainActivity.EXTRA_OPEN_ITEM)
        val NAVIGATE_TO = ActionParameters.Key<String>(MainActivity.EXTRA_NAVIGATE_TO)
    }
}

/** Registers the widget with the system. */
class FreshTrackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FreshTrackWidget()
}

/**
 * The app's own palette, light and dark, so the widget reads as a piece of
 * the app rather than of whichever launcher it sits on. The urgency dots use
 * the same values as the app's urgency pills.
 */
private object WidgetColors {
    fun providers(): ColorProviders = ColorProviders(light = LightColors, dark = DarkColors)

    fun urgency(level: WidgetContent.Urgency): ColorProvider = when (level) {
        WidgetContent.Urgency.OVERDUE -> ColorProvider(day = LightUrgency.expired, night = DarkUrgency.expired)
        WidgetContent.Urgency.TODAY -> ColorProvider(day = LightUrgency.critical, night = DarkUrgency.critical)
        WidgetContent.Urgency.SOON -> ColorProvider(day = LightUrgency.warning, night = DarkUrgency.warning)
        WidgetContent.Urgency.LATER -> ColorProvider(day = LightUrgency.safe, night = DarkUrgency.safe)
    }
}
