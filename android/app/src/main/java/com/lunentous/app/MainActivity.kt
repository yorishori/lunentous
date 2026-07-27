package com.lunentous.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lunentous.app.data.sync.outbox.SyncScheduler
import com.lunentous.app.di.AppContainer
import com.lunentous.app.ui.nav.MainScaffold
import com.lunentous.app.ui.theme.LunentousTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as LunentousApplication).container
        setContent {
            LunentousApp(container)
        }
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
fun LunentousApp(container: AppContainer) {
    LunentousTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            MainScaffold(container = container)
        }
    }
}
