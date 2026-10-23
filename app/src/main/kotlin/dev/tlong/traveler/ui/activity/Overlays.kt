package dev.tlong.traveler.ui.activity

import dev.tlong.traveler.domain.placementsOf
import dev.tlong.traveler.domain.dates
import dev.tlong.traveler.domain.datesOf
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.tlong.traveler.data.TripSession
import dev.tlong.traveler.domain.Edits
import dev.tlong.traveler.domain.activity
import dev.tlong.traveler.domain.label
import dev.tlong.traveler.domain.stay
import dev.tlong.traveler.domain.toDate
import dev.tlong.traveler.model.Slot
import dev.tlong.traveler.model.Trip
import dev.tlong.traveler.ui.common.offerUndo
import kotlinx.coroutines.launch

data class PlaceRequest(val activityId: String, val move: Edits.ItemRef?, val initialDate: String?, val initialSlot: Slot?)

/** A booking for one suggestion, opened from its sheet; [commitmentId] is set when editing one. */
data class BookingRequest(val activityId: String, val commitmentId: String?)

data class NewEntryRequest(val stayId: String, val date: String?, val slot: Slot?)

/** Which sheet or dialog is open over a stay or day screen. */
@Stable
class Overlays {
    var detail by mutableStateOf<String?>(null)
    var place by mutableStateOf<PlaceRequest?>(null)
    var editing by mutableStateOf<String?>(null)
    var newEntry by mutableStateOf<NewEntryRequest?>(null)
    var booking by mutableStateOf<BookingRequest?>(null)
}

@Composable
fun rememberOverlays() = remember { Overlays() }

fun dayName(date: String) = date.toDate()?.label() ?: date

@Composable
fun OverlayHost(
    session: TripSession,
    trip: Trip,
    overlays: Overlays,
    snackbar: SnackbarHostState,
    onGoToDay: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    fun undoable(message: String) = scope.launch { snackbar.offerUndo(session, message) }

    overlays.detail?.let { id ->
        ActivitySheet(
            session, trip, id,
            onDismiss = { overlays.detail = null },
            onGoToDay = { overlays.detail = null; onGoToDay(it) },
            onPlace = { a -> overlays.place = PlaceRequest(a.id, null, null, null) },
            onEdit = { a -> overlays.editing = a.id },
            onDeleted = { a ->
                overlays.detail = null
                if (session.edit("Delete ${a.name}") { Edits.deleteActivity(it, a.id) }) undoable("Deleted “${a.name}”")
            },
            onMessage = { m -> scope.launch { snackbar.showSnackbar(m) } },
            onBook = { a, c -> overlays.booking = BookingRequest(a.id, c?.id) },
        )
    }

    overlays.booking?.let { req ->
        val a = trip.activity(req.activityId) ?: return@let
        val existing = trip.commitments.firstOrNull { it.id == req.commitmentId }
        val dates = (trip.stay(a.stayId)?.let { trip.datesOf(it) } ?: trip.dates()).map { it.toString() }
        val initial = trip.placementsOf(a.id).firstOrNull()?.date ?: dates.firstOrNull() ?: trip.startDate
        BookingDialog(
            dates, initial, existing, suggestions = trip.activities.filter { it.stayId == a.stayId }, initialLinked = a, travelers = trip.travelers ?: 1,
            onDismiss = { overlays.booking = null },
            onDelete = existing?.let { c -> {
                overlays.booking = null
                if (session.edit("Delete booking") { Edits.deleteBooking(it, c.id) }) undoable("Deleted “${c.title}”")
            } },
        ) { b ->
            overlays.booking = null
            if (saveBooking(session, existing, b)) undoable("Booked “${b.title}” on ${dayName(b.date)}")
        }
    }

    overlays.place?.let { req ->
        val a = trip.activity(req.activityId) ?: return@let
        PlaceSheet(
            trip, a, verb = if (req.move != null) "Move" else "Add",
            initialDate = req.initialDate, initialSlot = req.initialSlot, ignoreRef = req.move,
            onDismiss = { overlays.place = null },
        ) { date, slot, time ->
            overlays.place = null
            val changed = if (req.move != null) {
                session.edit("Move ${a.name}") { Edits.move(it, req.move, date, slot, time) }
            } else {
                session.edit("Add ${a.name}") { Edits.place(it, a.id, date, slot, time) }
            }
            if (changed) undoable("${if (req.move != null) "Moved" else "Added"} “${a.name}” to ${dayName(date)}, ${slot.label.lowercase()}")
        }
    }

    overlays.editing?.let { id ->
        val a = trip.activity(id) ?: return@let
        EditActivityDialog(a, onDismiss = { overlays.editing = null }) { updated ->
            overlays.editing = null
            if (session.edit("Edit ${a.name}") { t -> Edits.updateActivity(t, id) { updated } }) undoable("Saved your changes to “${updated.name}”")
        }
    }

    overlays.newEntry?.let { req ->
        val where = listOfNotNull(trip.stay(req.stayId)?.name, req.date?.let { dayName(it) }, req.slot?.label?.lowercase()).joinToString(" · ")
        NewEntryDialog(where, onDismiss = { overlays.newEntry = null }) { name, note, place, tag ->
            overlays.newEntry = null
            val changed = session.edit("Add $name") { t ->
                val (withEntry, id) = Edits.addCustom(t, req.stayId, name, note, place, tag)
                if (req.date != null) Edits.place(withEntry, id, req.date, req.slot ?: Slot.ALLDAY) else withEntry
            }
            if (changed) undoable("Added “$name”" + (req.date?.let { " to ${dayName(it)}" } ?: " to the activities"))
        }
    }
}
