package dev.tlong.traveler.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tlong.traveler.ui.Navigator
import dev.tlong.traveler.ui.common.SectionTitle
import dev.tlong.traveler.ui.common.rich
import dev.tlong.traveler.ui.overview.BackButton

/** How the app and the assistant's ChatGPT Project fit together, one short entry per job. */
private val GUIDE = listOf(
    "Plan a trip" to "In the project, ask for the trip: where, when, what you like. Open the file it hands back with Traveler.",
    "Change the plan" to "Trip → share icon → save the file. In your trip chat, attach it and say what to change: **\"Revise this trip: move Punta Tombo to Tuesday and drop Cerro Avanzado.\"** Open the file it returns; your own edits are kept.",
    "Dig into one place" to "Start a new chat in the project, attach the trip file and type **/\u2060travel Puerto Madryn**. It shows that stay's dates and activities, then researches with you. Add what you choose from a day's **+** menu.",
    "Find lodging" to "Attach the trip file and ask **\"Find lodging for Puerto Madryn.\"** Open the file it returns, then compare and book from the stay's lodging in Bookings.",
    "Import lists fixes" to "Tap **Copy fix request** on the import screen and paste it into the trip chat.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantGuideScreen(navigator: Navigator) {
    Scaffold(topBar = { TopAppBar(title = { Text("Using the assistant") }, navigationIcon = { BackButton(navigator) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(GUIDE) { (title, body) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionTitle(title)
                    Text(rich(body), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
