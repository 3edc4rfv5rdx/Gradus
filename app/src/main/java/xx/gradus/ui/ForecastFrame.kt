package xx.gradus.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xx.gradus.Forecast
import xx.gradus.Problem
import xx.gradus.R
import xx.gradus.WeatherState
import xx.gradus.WeatherViewModel

/**
 * What the Now and Hourly tabs share: pull down to refresh, a line saying how
 * fresh the forecast is and what went wrong with the last refresh, and in place
 * of a forecast not fetched yet, what to do about it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastFrame(
    viewModel: WeatherViewModel,
    onRequestLocation: () -> Unit,
    onPickPlace: () -> Unit,
    content: LazyListScope.(Forecast) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        // A list even when there is nothing in it: pull-to-refresh needs something to scroll.
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item { StatusLine(state, onRequestLocation, onPickPlace) }
            val forecast = state.forecast
            if (forecast != null) content(forecast)
        }
    }
}

@Composable
private fun StatusLine(state: WeatherState, onRequestLocation: () -> Unit, onPickPlace: () -> Unit) {
    val problem = state.problem
    // Nothing to say while the first fetch is still under way: the spinner says it.
    if (state.forecast == null && problem == null) return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.fetchedAt > 0) {
            val time = DateUtils.formatSameDayTime(
                state.fetchedAt,
                System.currentTimeMillis(),
                java.text.DateFormat.SHORT,
                java.text.DateFormat.SHORT,
            ).toString()
            Text(
                stringResource(R.string.updated_at, time),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (problem != null) {
            Text(
                stringResource(problem.messageRes()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        if (problem == Problem.NO_PERMISSION || (problem == Problem.NO_LOCATION && state.forecast == null)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (problem == Problem.NO_PERMISSION) {
                    Button(onClick = onRequestLocation) { Text(stringResource(R.string.allow)) }
                }
                FilledTonalButton(onClick = onPickPlace) { Text(stringResource(R.string.pick_city)) }
            }
        }
    }
}

private fun Problem.messageRes(): Int = when (this) {
    Problem.NO_PERMISSION -> R.string.problem_no_permission
    Problem.NO_LOCATION -> R.string.problem_no_location
    Problem.NETWORK -> R.string.problem_network
}
