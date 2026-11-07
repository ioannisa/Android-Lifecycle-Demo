package eu.anifantakis.myapplication

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri

class MyApplication : Application() {

    // Step 4: the system plugs the app's ContextImpl — the "engine" — into the Application
    override fun attachBaseContext(base: Context) {
        LaunchTrace.mark("Application.attachBaseContext(base = ${base.describe()})", showPath = true)
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        LaunchTrace.mark("Application.onCreate")
    }
}
