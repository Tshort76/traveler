package dev.tlong.traveler.ui.map

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tlong.traveler.domain.mapAreas
import dev.tlong.traveler.model.Trip
import java.util.Locale

/** Saves each stay's street map for use without a signal, and shows how far that has got. */
@Composable
fun OfflineMapsDialog(trip: Trip, online: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val states by OfflineMaps.states.collectAsStateWithLifecycle()
    val state = states[trip.id]
    val stays = remember(trip) { trip.mapAreas().size }
    val metered = remember(online) { (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered }
    LaunchedEffect(trip.id) { OfflineMaps.refresh(context, trip) }
    val saving = state is OfflineMaps.State.Saving

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Offline maps") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Saves the terrain around each stay's activities (water, parks, built-up areas, about a quarter mile of detail) " +
                        "so the Activities map works without a signal. Use Google Maps for streets and directions.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                when (state) {
                    is OfflineMaps.State.Saving -> {
                        Text("Saving ${state.stayName} (${state.index} of ${state.count}) · ${mb(state.bytes)}", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator({ state.fraction }, Modifier.fillMaxWidth())
                        Text("This carries on if you close this window.", style = MaterialTheme.typography.bodySmall)
                    }
                    is OfflineMaps.State.Saved -> Text(
                        "✓ Saved for ${state.stays} of $stays stays · ${mb(state.bytes)}" +
                            if (state.stays < stays) ". Save again to add the rest." else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is OfflineMaps.State.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    null -> Text(if (stays == 0) "No stay has places with coordinates yet." else "Not saved yet ($stays stays).", style = MaterialTheme.typography.bodyMedium)
                }
                if (!online) Text("You're offline. Connect to save maps.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                else if (metered && !saving) Text("You're on mobile data; wifi is better for this.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            if (!saving && stays > 0) TextButton(onClick = { OfflineMaps.save(context, trip) }, enabled = online) {
                Text(if (state is OfflineMaps.State.Saved) "Update maps" else "Save maps")
            }
        },
        dismissButton = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                if (saving) TextButton(onClick = { OfflineMaps.remove(context, trip.id) }) { Text("Stop and remove") }
                else if (state is OfflineMaps.State.Saved) TextButton(onClick = { OfflineMaps.remove(context, trip.id) }) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

private fun mb(bytes: Long) = String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
