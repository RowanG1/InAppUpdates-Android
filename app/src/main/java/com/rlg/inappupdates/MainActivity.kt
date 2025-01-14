package com.rlg.inappupdates

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.rlg.inappupdates.ui.theme.InAppUpdatesTheme
import de.palm.composestateevents.EventEffect
import kotlinx.coroutines.Dispatchers
import timber.log.Timber

class MainActivity : ComponentActivity() {
    // In-App Update dependencies
    private val clockUtil = ClockUtilImpl() // Clock utility for timing (e.g., throttling update prompts)
    private lateinit var inAppUpdateViewModel: InAppUpdateViewModel // ViewModel to manage update logic
    private lateinit var updateLauncher: ActivityResultLauncher<IntentSenderRequest> // Launcher for update flows

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Enables edge-to-edge UI

        // Initialize ViewModel for in-app updates (replaceable with DI like Hilt in production)
        inAppUpdateViewModel = InAppUpdateViewModel(
            inAppUpdateRepo = InAppUpdateRepoImpl(
                appUpdateManager = AppUpdateManagerFactory.create(this), // AppUpdateManager for managing update flows
                ioDispatcher = Dispatchers.IO,
                promptDataStore = PromptTimestampDataStore(this),
                clockUtil = clockUtil
            ),
            clockUtil = clockUtil,
            defaultDispatcher = Dispatchers.IO
        )

        setupInAppUpdates() // Setup in-app update mechanisms

        setContent {
            val inAppUpdateState by inAppUpdateViewModel.uiState.collectAsState()

            // Handles the install prompt event for downloaded updates
            EventEffect(
                event = inAppUpdateState.showInstallPrompt,
                onConsumed = inAppUpdateViewModel::onShowInstallPromptConsumed,
            ) {
                InAppUpdateSnackbarUtil.showInstallPromptSnackbar(activity = this) {
                    inAppUpdateViewModel.installDownloadedUpdate() // Triggers installation of downloaded update
                }
            }

            // Handles the flexible update download prompt
            EventEffect(
                event = inAppUpdateState.launchFlexibleUpdate,
                onConsumed = inAppUpdateViewModel::onFlexibleUpdateDownloadPromptShown,
            ) {
                InAppUpdateSnackbarUtil.showFlexibleUpdateSnackbar(
                    activity = this,
                    onDownloadClicked = {
                        inAppUpdateViewModel.triggerFlexibleUpdate(updateLauncher) // Initiates flexible update download
                    },
                    onDismissClicked = { Timber.d("Dismiss clicked.") },
                )
            }

            // Standard UI setup (unrelated to in-app updates)
            InAppUpdatesTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    // Setup function for in-app updates
    private fun setupInAppUpdates() {
        updateLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode != RESULT_OK) {
                Timber.e("In-app update flow failed or was canceled.") // Handles update failure/cancellation
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    InAppUpdatesTheme {
        Greeting("Android")
    }
}