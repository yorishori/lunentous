package com.lunentous.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.lunentous.app.data.sync.outbox.SyncScheduler
import com.lunentous.app.di.AppContainer
import com.lunentous.app.ui.nav.DeepLinkTarget
import com.lunentous.app.ui.nav.MainScaffold
import com.lunentous.app.ui.nav.parseDeepLink
import com.lunentous.app.ui.theme.LunentousTheme

/** singleTop in the manifest means a widget/shortcut/share tap while the
 * app is already running reuses this instance via onNewIntent rather than
 * stacking a second one -- deepLinkTarget is a Compose State so updating
 * it there recomposes the already-running content just like onCreate's
 * initial value does. */
class MainActivity : ComponentActivity() {
    private var deepLinkTarget by mutableStateOf<DeepLinkTarget?>(null)
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as LunentousApplication).container
        deepLinkTarget = parseDeepLink(intent)
        requestNotificationPermissionIfNeeded()
        setContent {
            LunentousApp(container, deepLinkTarget, onDeepLinkConsumed = { deepLinkTarget = null })
        }
    }

    /** Only meaningful on API 33+ (POST_NOTIFICATIONS didn't exist before
     * that, and notifications just worked without asking) -- see
     * ReminderNotifier, which silently skips posting until this is
     * granted rather than blocking on it. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkTarget = parseDeepLink(intent)
    }

    /** App-foreground sync trigger (see SyncScheduler) -- a single-Activity
     * app makes this a reasonable proxy for "the app came to the
     * foreground" without pulling in a separate ProcessLifecycleOwner
     * dependency just for this. */
    override fun onResume() {
        super.onResume()
        SyncScheduler.triggerOutboxSync(this)
    }
}

/**
 * No login gate -- the app is fully usable standalone, with all data
 * local-only, and connecting to a server is an optional action from the
 * Settings screen rather than a precondition for using anything else.
 */
@Composable
fun LunentousApp(container: AppContainer, deepLinkTarget: DeepLinkTarget?, onDeepLinkConsumed: () -> Unit) {
    LunentousTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            MainScaffold(container = container, deepLinkTarget = deepLinkTarget, onDeepLinkConsumed = onDeepLinkConsumed)
        }
    }
}
