package xx.gradus

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import xx.gradus.ui.SettingsScreen
import xx.gradus.ui.WindowDark
import xx.gradus.ui.WindowLight
import xx.gradus.ui.accentAt
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
                    MainScreen(onAbout = ::showAbout)
                }
            }
        }
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
            ),
        )
    }
}

@Composable
private fun MainScreen(onAbout: () -> Unit) {
    var current by rememberSaveable { mutableStateOf(Tab.NOW) }

    Scaffold(
        topBar = { AppTopBar(tab = current, onAbout = onAbout) },
        bottomBar = {
            // On a phone with on-screen back/home buttons the bar sits under them unless it is
            // given that inset; with gesture navigation the same inset is a thin strip.
            NavigationBar(windowInsets = NavigationBarDefaults.windowInsets) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == current,
                        onClick = { current = tab },
                        icon = { TabIcon(tab) },
                        label = { Text(stringResource(tab.labelRes), style = NavLabelStyle) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (current) {
                Tab.NOW -> NowScreen()
                Tab.HOURLY -> HourlyScreen()
                Tab.SETTINGS -> SettingsScreen()
            }
        }
    }
}

/** The current tab's name; Settings also carries the (i) that opens About, as in Steps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(tab: Tab, onAbout: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(tab.labelRes)) },
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
            }
        },
    )
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
