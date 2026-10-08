package dev.tlong.traveler.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
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
import dev.tlong.traveler.ui.stay.StayScreen
import dev.tlong.traveler.ui.trips.TripsScreen
import kotlinx.serialization.Serializable

@Serializable data object TripsRoute
@Serializable data class OverviewRoute(val tripId: String)
@Serializable data class StayRoute(val tripId: String, val stayId: String, val tab: Int = 0)
@Serializable data class DayRoute(val tripId: String, val date: String)
@Serializable data object ImportRoute
@Serializable data class HistoryRoute(val tripId: String)
/** [stayId] and [group] (a [dev.tlong.traveler.domain.Bookable.Group] name) open the list filtered to them. */
@Serializable data class BookingsRoute(val tripId: String, val date: String? = null, val stayId: String? = null, val group: String? = null)
@Serializable data object SettingsRoute
@Serializable data class ChecklistRoute(val tripId: String, val tab: Int = 0)
@Serializable data class NotesRoute(val tripId: String)
@Serializable data object TemplatesRoute
@Serializable data class TemplateRoute(val id: String)

/** Navigation callbacks the screens share, so none of them holds the controller. */
class Navigator(private val nav: NavHostController) {
    fun back() { if (!nav.popBackStack()) nav.navigate(TripsRoute) }
    fun trips() = nav.navigate(TripsRoute) { popUpTo(TripsRoute) { inclusive = true } }
    fun overview(tripId: String, replaceStack: Boolean = false) = nav.navigate(OverviewRoute(tripId)) {
        if (replaceStack) popUpTo(TripsRoute)
    }
    fun stay(tripId: String, stayId: String, tab: Int = 0) = nav.navigate(StayRoute(tripId, stayId, tab))
    fun day(tripId: String, date: String, replace: Boolean = false) = nav.navigate(DayRoute(tripId, date)) {
        if (replace) nav.currentBackStackEntry?.destination?.id?.let { popUpTo(it) { inclusive = true } }
    }
    fun history(tripId: String) = nav.navigate(HistoryRoute(tripId))
    fun bookings(tripId: String, date: String? = null, stayId: String? = null, group: String? = null) =
        nav.navigate(BookingsRoute(tripId, date, stayId, group))
    fun settings() = nav.navigate(SettingsRoute)
    fun checklist(tripId: String, tab: Int = 0) = nav.navigate(ChecklistRoute(tripId, tab))
    fun notes(tripId: String) = nav.navigate(NotesRoute(tripId))
    fun templates() = nav.navigate(TemplatesRoute)
    fun template(id: String) = nav.navigate(TemplateRoute(id))
}

@Composable
fun TravelerNav() {
    val nav = rememberNavController()
    val navigator = Navigator(nav)
    val pending by LocalContainer.current.pendingImport.collectAsStateWithLifecycle()

    // Whatever screen is showing, an opened or shared file brings up its preview.
    LaunchedEffect(pending != null) {
        if (pending != null && nav.currentBackStackEntry?.destination?.route?.contains("ImportRoute") != true) nav.navigate(ImportRoute)
    }

    NavHost(nav, startDestination = TripsRoute) {
        composable<TripsRoute> { TripsScreen(navigator) }
        composable<OverviewRoute> { OverviewScreen(it.toRoute<OverviewRoute>().tripId, navigator) }
        composable<StayRoute> { val r = it.toRoute<StayRoute>(); StayScreen(r.tripId, r.stayId, r.tab, navigator) }
        composable<DayRoute> { val r = it.toRoute<DayRoute>(); DayScreen(r.tripId, r.date, navigator) }
        composable<ImportRoute> { ImportScreen(navigator) }
        composable<BookingsRoute> { val r = it.toRoute<BookingsRoute>(); BookingsScreen(r.tripId, r.date, navigator, r.stayId, r.group) }
        composable<HistoryRoute> { HistoryScreen(it.toRoute<HistoryRoute>().tripId, navigator) }
        composable<SettingsRoute> { SettingsScreen(navigator) }
        composable<ChecklistRoute> { val r = it.toRoute<ChecklistRoute>(); ChecklistScreen(r.tripId, r.tab, navigator) }
        composable<NotesRoute> { NotesScreen(it.toRoute<NotesRoute>().tripId, navigator) }
        composable<TemplatesRoute> { TemplatesScreen(navigator) }
        composable<TemplateRoute> { TemplateScreen(it.toRoute<TemplateRoute>().id, navigator) }
    }
}
