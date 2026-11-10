package eu.anifantakis.myapplication

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import eu.anifantakis.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {

    // Step 5: the system plugs a NEW ContextImpl into the Activity — a different one from the Application's
    override fun attachBaseContext(newBase: Context) {
        LaunchTrace.mark("Activity.attachBaseContext(base = ${newBase.describe()})")
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Path = Step 5: H.handleMessage → TransactionExecutor → LaunchActivityItem → performLaunchActivity → … → onCreate
        // "saved state? true" = the Activity is being re-created (rotation, or a warm start)
        LaunchTrace.mark("Activity.onCreate — saved state? ${savedInstanceState != null}", showPath = true)
        enableEdgeToEdge()
        setContent {
            var loaded by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(400.milliseconds)        // pretend we're loading data
                loaded = true
            }
            ReportDrawnWhen { loaded }   // → "Fully drawn …" in Logcat, next to "Displayed …"

            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (loaded) {
                        Greeting(name = "Android", modifier = Modifier.padding(innerPadding))
                    } else {
                        Text(text = "Loading…", modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }

    override fun onRestart() { super.onRestart(); LaunchTrace.mark("Activity.onRestart", showPath = true) }
    override fun onStart()   { super.onStart();   LaunchTrace.mark("Activity.onStart") }
    override fun onResume()  { super.onResume();  LaunchTrace.mark("Activity.onResume — window attached? ${window.decorView.isAttachedToWindow}") }
    override fun onPause()   { LaunchTrace.mark("Activity.onPause"); super.onPause() }
    override fun onStop()    { LaunchTrace.mark("Activity.onStop — surface will be released"); super.onStop() }

    // Never printed when the process is killed (swipe, force stop, low memory).
    // Printed when the system destroys only the Activity: rotation, finish(), "Don't keep activities".
    override fun onDestroy() {
        LaunchTrace.mark("Activity.onDestroy — config change? $isChangingConfigurations · finishing? $isFinishing", showPath = true)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus); LaunchTrace.mark("onWindowFocusChanged($hasFocus)")
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
    MyApplicationTheme {
        Greeting("Android")
    }
}