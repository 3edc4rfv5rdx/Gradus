package xx.gradus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xx.gradus.AppSettings
import xx.gradus.GPS_KEY
import xx.gradus.Place
import xx.gradus.WeatherViewModel
import xx.gradus.R

/**
 * Where the forecast is for: the phone's own position, a saved place, or a new
 * one found by name. While a search has something to show, its results take
 * the list's place; a picked result is saved and shown at once.
 */
@Composable
fun PlacesDialog(viewModel: WeatherViewModel, onDismiss: () -> Unit) {
    val places by AppSettings.places.collectAsState()
    val selected by AppSettings.selectedKey.collectAsState()
    val gpsPlace by AppSettings.gpsPlace.collectAsState()
    val search by viewModel.search.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

    fun close() {
        viewModel.clearSearch()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = ::close,
        title = { Text(stringResource(R.string.places)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        // Back to the list once the field is cleared.
                        if (it.isBlank()) viewModel.clearSearch()
                    },
                    placeholder = { Text(stringResource(R.string.find_city)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) }),
                    trailingIcon = {
                        IconButton(onClick = { viewModel.search(query) }) {
                            Icon(Icons.Filled.Search, stringResource(R.string.find_city))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    when {
                        search.searching -> Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) { CircularProgressIndicator(Modifier.size(32.dp)) }

                        search.failed -> Note(stringResource(R.string.search_failed))
                        search.empty -> Note(stringResource(R.string.nothing_found))
                        search.results.isNotEmpty() -> search.results.forEach { place ->
                            PlaceRow(place.name, place.region, onClick = {
                                viewModel.addAndSelect(place)
                                query = ""
                                close()
                            })
                        }

                        else -> {
                            PlaceRow(
                                name = stringResource(R.string.my_location),
                                region = gpsPlace?.name.orEmpty(),
                                selected = selected == GPS_KEY,
                                leading = { Icon(Icons.Filled.LocationOn, null) },
                                onClick = {
                                    viewModel.select(GPS_KEY)
                                    close()
                                },
                            )
                            places.forEach { place ->
                                HorizontalDivider()
                                SavedPlaceRow(
                                    place = place,
                                    selected = selected == place.key,
                                    onPick = {
                                        viewModel.select(place.key)
                                        close()
                                    },
                                    onRemove = { viewModel.remove(place) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { DialogDismissButton(stringResource(R.string.close), ::close) },
    )
}

@Composable
private fun SavedPlaceRow(place: Place, selected: Boolean, onPick: () -> Unit, onRemove: () -> Unit) {
    PlaceRow(
        name = place.name,
        region = place.region,
        selected = selected,
        onClick = onPick,
        trailing = {
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, stringResource(R.string.remove)) }
        },
    )
}

@Composable
private fun PlaceRow(
    name: String,
    region: String,
    onClick: () -> Unit,
    selected: Boolean? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selected != null) RadioButton(selected = selected, onClick = onClick)
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (region.isNotEmpty()) {
                Text(
                    region,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}
