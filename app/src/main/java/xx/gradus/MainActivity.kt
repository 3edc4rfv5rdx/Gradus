package xx.gradus

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toDrawable
import dev.about.About
import dev.about.AboutConfig
import dev.updater.AboutUpdater
import dev.updater.Updater
import dev.updater.UpdaterConfig
import xx.gradus.ui.GradusTheme
import xx.gradus.ui.HourlyScreen
import xx.gradus.ui.NavLabelStyle
import xx.gradus.ui.NowScreen
import xx.gradus.ui.PlacesDialog
import xx.gradus.ui.SettingsScreen
import xx.gradus.ui.WindowDark
import xx.gradus.ui.WindowLight
import xx.gradus.ui.accentAt
import xx.gradus.ui.fetchedTimeText
import xx.gradus.ui.isDarkTheme

// One description of this app's release, for the silent check at start-up and
// the About dialog's button alike.
val UPDATER_CONFIG = UpdaterConfig(appKey = "gradus", repo = "Gradus")

/** The three tabs, in bottom-bar order. */
private enum class Tab(val labelRes: Int) {
    NOW(R.string.tab_now),
    HOURLY(R.string.tab_hourly),
    SETTINGS(R.string.tab_settings),
}

class MainActivity : ComponentActivity() {

    private val weather: WeatherViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)
        // Before the start-up check, so an update dialog is already in the app's accent.
        applyDialogAccent()
        // Looks for a newer build in this app's own GitHub release and asks
        // before it downloads anything.
        Updater.checkOnStart(this, UPDATER_CONFIG)
        applyWindowTheme()

        setContent {
            val themeMode by AppSettings.themeMode.collectAsState()
            val accentIndex by AppSettings.accentIndex.collectAsState()
            // The window is outside the composition: a theme changed in
            // Settings is carried out to the bars by hand.
            LaunchedEffect(themeMode) { applyWindowTheme() }
            // The shared dialogs are outside it too, and read the accent when they open.
            LaunchedEffect(accentIndex) { applyDialogAccent() }

            GradusTheme(themeMode, accentIndex) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(weather, onAbout = ::showAbout)
                }
            }
        }
    }

    /** Every way back into the app passes here, the first start included. */
    override fun onStart() {
        super.onStart()
        weather.refreshIfStale()
    }

    /**
     * The window background before the first frame and the colour the system
     * bar icons are drawn for, both following the app's theme rather than the
     * system's — the same as in Steps.
     */
    private fun applyWindowTheme() {
        val systemInDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val dark = isDarkTheme(AppSettings.themeMode.value, systemInDark)
        window.setBackgroundDrawable((if (dark) WindowDark else WindowLight).toArgb().toDrawable())
        val style = if (dark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    /** The accent the About and update dialogs are drawn in: the one picked in Settings. */
    private fun applyDialogAccent() {
        About.accent = accentAt(AppSettings.accentIndex.value).toArgb()
    }

    private fun showAbout() {
        About.show(
            this,
            AboutConfig(
                repo = UPDATER_CONFIG.repoName,
                buildDate = BuildConfig.BUILD_DATE,
                updates = AboutUpdater(UPDATER_CONFIG),
                // Open-Meteo's data is CC BY 4.0: the credit goes here.
                extra = listOf(getString(R.string.weather_data_source) to "Open-Meteo.com"),
            ),
        )
    }
}

@Composable
private fun MainScreen(weather: WeatherViewModel, onAbout: () -> Unit) {
    var current by rememberSaveable { mutableStateOf(Tab.NOW) }
    var pickingPlace by rememberSaveable { mutableStateOf(false) }
    val state by weather.state.collectAsState()
    // Whatever the answer, a refresh tells the screen what it now is.
    val askLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { weather.refresh() }
    val requestLocation = { askLocation.launch(LOCATION_PERMISSIONS) }
    val pickPlace = { pickingPlace = true }

    Scaffold(
        topBar = {
            AppTopBar(
                tab = current,
                place = state.place,
                fetchedAt = state.fetchedAt,
                onPickPlace = pickPlace,
                onAbout = onAbout,
            )
        },
        bottomBar = { TabBar(current, onPick = { current = it }) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (current) {
                Tab.NOW -> NowScreen(weather, requestLocation, pickPlace)
                Tab.HOURLY -> HourlyScreen(weather, requestLocation, pickPlace)
                Tab.SETTINGS -> SettingsScreen()
            }
        }
    }

    if (pickingPlace) PlacesDialog(weather, onDismiss = { pickingPlace = false })
}

/** Material's bar is 80dp tall and cannot be made lower; this one is [TAB_BAR_HEIGHT]. */
private val TAB_BAR_HEIGHT = 56.dp

@Composable
private fun TabBar(current: Tab, onPick: (Tab) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceContainer) {
        // The inset first, then the height: on a phone with on-screen back/home buttons
        // the bar stands on top of them instead of under; with gestures the inset is a thin strip.
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(TAB_BAR_HEIGHT),
        ) {
            Tab.entries.forEach { tab ->
                val selected = tab == current
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = selected, role = Role.Tab, onClick = { onPick(tab) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val content = if (selected) scheme.onPrimary else scheme.onSurface
                    Box(
                        Modifier
                            .background(
                                // The picked tab wears the accent chosen in Settings.
                                if (selected) scheme.primary else Color.Transparent,
                                RoundedCornerShape(50),
                            )
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                    ) {
                        CompositionLocalProvider(LocalContentColor provides content) { TabIcon(tab) }
                    }
                    Text(stringResource(tab.labelRes), style = NavLabelStyle, color = scheme.onSurface)
                }
            }
        }
    }
}

/**
 * On the forecast tabs, the place the forecast is for, tapped to pick another;
 * on Settings, the tab's name and the (i) that opens About, as in Steps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(tab: Tab, place: Place?, fetchedAt: Long, onPickPlace: () -> Unit, onAbout: () -> Unit) {
    TopAppBar(
        title = {
            if (tab == Tab.SETTINGS) {
                Text(stringResource(tab.labelRes))
            } else {
                PlaceTitle(place, onPickPlace)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
        actions = {
            if (tab == Tab.SETTINGS) {
                IconButton(onClick = onAbout) {
                    Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.about))
                }
            } else if (fetchedAt > 0) {
                // How fresh the forecast is, in two short lines at the right.
                Column(Modifier.padding(end = 16.dp), horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.updated), style = MaterialTheme.typography.labelMedium)
                    Text(fetchedTimeText(fetchedAt), style = MaterialTheme.typography.titleSmall)
                }
            }
        },
    )
}

/** The place's name over its region, with the mark that says it opens a choice. */
@Composable
private fun PlaceTitle(place: Place?, onClick: () -> Unit) {
    Row(Modifier.clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                place?.name ?: stringResource(R.string.my_location),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val region = place?.region.orEmpty()
            if (region.isNotEmpty()) {
                Text(
                    region,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.places))
    }
}

/**
 * Now wears the launcher figure, half sun and half snowflake, so the tab and the
 * app read as one thing — the way Steps wears its walker.
 */
@Composable
private fun TabIcon(tab: Tab) {
    when (tab) {
        Tab.NOW -> Icon(painterResource(R.drawable.ic_tab_now), contentDescription = null)
        Tab.HOURLY -> Icon(painterResource(R.drawable.ic_tab_hourly), contentDescription = null)
        Tab.SETTINGS -> Icon(Icons.Filled.Settings, contentDescription = null)
    }
}
