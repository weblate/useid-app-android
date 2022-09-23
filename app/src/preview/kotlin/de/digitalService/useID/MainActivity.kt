package de.digitalService.useID

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import de.digitalService.useID.analytics.TrackerManager
import de.digitalService.useID.analytics.TrackerManagerType
import de.digitalService.useID.ui.UseIDApp
import de.digitalService.useID.ui.coordinators.AppCoordinator
import de.digitalService.useID.ui.coordinators.AppCoordinatorType
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val logger by getLogger()

    @Inject
    lateinit var appCoordinator: AppCoordinatorType

    @Inject
    lateinit var trackerManager: TrackerManagerType

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            installSplashScreen()
        }
        super.onCreate(savedInstanceState)

        handleNewIntent(intent)

        setContent {
            UseIDApp(appCoordinator, trackerManager)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        intent?.let { handleNewIntent(it) }
    }

    private fun handleNewIntent(intent: Intent) {
        val intentData = intent.data
        if (intent.action == Intent.ACTION_VIEW && intentData != null) {
            appCoordinator.handleDeepLink(intentData)
        }
    }
}
