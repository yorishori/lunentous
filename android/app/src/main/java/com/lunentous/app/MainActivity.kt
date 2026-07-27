package com.lunentous.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lunentous.app.data.auth.SessionStore
import com.lunentous.app.ui.login.LoginScreen
import com.lunentous.app.ui.nav.MainScaffold
import com.lunentous.app.ui.theme.LunentousTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionStore = SessionStore(applicationContext)
        setContent {
            LunentousApp(sessionStore)
        }
    }
}

@Composable
fun LunentousApp(sessionStore: SessionStore) {
    var loggedIn by remember { mutableStateOf(sessionStore.hasSession()) }

    LunentousTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (loggedIn) {
                MainScaffold()
            } else {
                LoginScreen(sessionStore = sessionStore, onLoggedIn = { loggedIn = true })
            }
        }
    }
}
