package dev.tlong.traveler.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.tlong.traveler.ui.trips.underWay
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.activity.compose.BackHandler
import dev.tlong.traveler.domain.day
import dev.tlong.traveler.domain.destinations
import dev.tlong.traveler.domain.stayOf
import dev.tlong.traveler.ui.checklist.ChecklistScreen
import dev.tlong.traveler.ui.overview.NotesScreen
import dev.tlong.traveler.ui.checklist.TemplateScreen
import dev.tlong.traveler.ui.checklist.TemplatesScreen
import dev.tlong.traveler.ui.bookings.BookingsScreen
import dev.tlong.traveler.ui.common.LocalContainer
import dev.tlong.traveler.ui.day.DayScreen
import dev.tlong.traveler.ui.history.HistoryScreen
import dev.tlong.traveler.ui.importer.ImportScreen
import dev.tlong.traveler.ui.overview.OverviewScreen
import dev.tlong.traveler.ui.settings.SettingsScreen
import dev.tlong.traveler.ui.settings.AssistantGuideScreen
import dev.tlong.traveler.ui.stay.StayScreen
import dev.tlong.traveler.ui.trips.TripsScreen
import kotlinx.serialization.Serializable

@Serializable data object TripsRoute
@Serializable data class OverviewRoute(val tripId: String, val tab: Int = 0)
@Serializable data class StayRoute(val tripId: String, val stayId: String, val tab: Int = 0)
@Serializable data class DayRoute(val tripId: String, val date: String)
@Serializable data object ImportRoute
@Serializable data class HistoryRoute(val tripId: String)
@Serializable data class BookingsRoute(val tripId: String, val date: String? = null)
@Serializable data class EventsRoute(val tripId: String, val stayId: String? = null)
@Serializable data object SettingsRoute
@Serializable data class ChecklistRoute(val tripId: String, val tab: Int = 0)
@Serializable data class NotesRoute(val tripId: String)
@Serializable data object TemplatesRoute
@Serializable data object AssistantGuideRoute
@Serializable data class TemplateRoute(val id: String)

/** Navigation callbacks the screens share, so none of them holds the controller. */
class Navigator(private val nav: NavHostController) {
    /** Back one screen; from a day the app opened on, with nothing under it, up to its trip. */
    fun back() {
        if (nav.previousBackStackEntry != null) { nav.popBackStack(); return }
        val entry = nav.currentBackStackEntry
        if (entry?.destination?.hasRoute<DayRoute>() == true) nav.navigate(OverviewRoute(entry.toRoute<DayRoute>().tripId)) { popUpTo(nav.graph.id) { inclusive = true } }
        else trips()
    }
    /** The list of trips, alone on the stack: the app may have opened on a trip, with nothing under it. */
    fun trips() = nav.navigate(TripsRoute) { popUpTo(nav.graph.id) { inclusive = true } }
    fun overview(tripId: String, replaceStack: Boolean = false) = nav.navigate(OverviewRoute(tripId)) {
        if (replaceStack) popUpTo(TripsRoute)
    }
    /** The trip page on its Calendar tab, in place of the trip page already on the stack. */
    fun tripCalendar(tripId: String) = nav.navigate(OverviewRoute(tripId, tab = 2)) {
        popUpTo<OverviewRoute> { inclusive = true }
    }
    fun stay(tripId: String, stayId: String, tab: Int = 0) = nav.navigate(StayRoute(tripId, stayId, tab))
    fun day(tripId: String, date: String, replace: Boolean = false) = nav.navigate(DayRoute(tripId, date)) {
        if (replace) nav.currentBackStackEntry?.destination?.id?.let { popUpTo(it) { inclusive = true } }
    }
    fun history(tripId: String) = nav.navigate(HistoryRoute(tripId))
    fun bookings(tripId: String, date: String) = nav.navigate(BookingsRoute(tripId, date))
    fun events(tripId: String, stayId: String? = null) = nav.navigate(EventsRoute(tripId, stayId))
    fun settings() = nav.navigate(SettingsRoute)
    fun checklist(tripId: String, tab: Int = 0) = nav.navigate(ChecklistRoute(tripId, tab))
    fun notes(tripId: String) = nav.navigate(NotesRoute(tripId))
    fun templates() = nav.navigate(TemplatesRoute)
    fun assistantGuide() = nav.navigate(AssistantGuideRoute)
    fun template(id: String) = nav.navigate(TemplateRoute(id))
}

@Composable
fun TravelerNav() {
    val container = LocalContainer.current
    // Where the app opens: today's day of the trip under way, else that trip, else the list. Decided before
    // anything is drawn, so nothing flashes past. Saved as "", "<trip>" or "<trip> <date>".
    var opening by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (opening != null) return@LaunchedEffect
        val today = LocalDate.now()
        val id = container.store.summaries.first().underWay(today)?.id?.takeIf { container.pendingImport.value == null }
        val trip = id?.let { container.session(it)?.trip?.value }
        val inStay = trip?.day(today.toString())?.let { trip.stayOf(it) }?.let { s -> trip.destinations.any { it.id == s.id } } == true
        opening = when { id == null -> ""; inStay -> "$id $today"; else -> id }
    }
    val start = opening ?: return Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))

    val nav = rememberNavController()
    val navigator = Navigator(nav)
    val pending by container.pendingImport.collectAsStateWithLifecycle()

    // Whatever screen is showing, an opened or shared file brings up its preview.
    LaunchedEffect(pending != null) {
        if (pending != null && nav.currentBackStackEntry?.destination?.route?.contains("ImportRoute") != true) nav.navigate(ImportRoute)
    }

    val startRoute: Any = start.split(" ").let { p -> when { start.isEmpty() -> TripsRoute; p.size == 2 -> DayRoute(p[0], p[1]); else -> OverviewRoute(start) } }
    NavHost(nav, startDestination = startRoute) {
        composable<TripsRoute> { TripsScreen(navigator) }
        composable<OverviewRoute> { val r = it.toRoute<OverviewRoute>(); OverviewScreen(r.tripId, navigator, r.tab) }
        composable<StayRoute> { val r = it.toRoute<StayRoute>(); StayScreen(r.tripId, r.stayId, r.tab, navigator) }
        composable<DayRoute> { val r = it.toRoute<DayRoute>(); DayScreen(r.tripId, r.date, navigator) }
        composable<ImportRoute> { ImportScreen(navigator) }
        composable<BookingsRoute> { val r = it.toRoute<BookingsRoute>(); BookingsScreen(r.tripId, r.date, navigator) }
        composable<EventsRoute> { val r = it.toRoute<EventsRoute>(); BookingsScreen(r.tripId, null, navigator, r.stayId, events = true) }
        composable<HistoryRoute> { HistoryScreen(it.toRoute<HistoryRoute>().tripId, navigator) }
        composable<SettingsRoute> { SettingsScreen(navigator) }
        composable<ChecklistRoute> { val r = it.toRoute<ChecklistRoute>(); ChecklistScreen(r.tripId, r.tab, navigator) }
        composable<NotesRoute> { NotesScreen(it.toRoute<NotesRoute>().tripId, navigator) }
        composable<TemplatesRoute> { TemplatesScreen(navigator) }
        composable<AssistantGuideRoute> { AssistantGuideScreen(navigator) }
        composable<TemplateRoute> { TemplateScreen(it.toRoute<TemplateRoute>().id, navigator) }
    }
    // Back from a day the app opened on goes up to its trip rather than out of the app.
    val entry by nav.currentBackStackEntryAsState()
    BackHandler(enabled = entry?.destination?.hasRoute<DayRoute>() == true && nav.previousBackStackEntry == null) { navigator.back() }
}
