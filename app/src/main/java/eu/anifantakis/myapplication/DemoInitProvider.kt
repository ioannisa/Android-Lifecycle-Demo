package eu.anifantakis.myapplication

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri


/**
 * Step 4 / Takeaway #1: ContentProviders run BEFORE Application.onCreate().
 * It's the same trick App Startup and Firebase use to set themselves up.
 *
 * Register it inside <application> in AndroidManifest.xml:
 *
 *   <provider
 *       android:name=".DemoInitProvider"
 *       android:authorities="${applicationId}.demo-init"
 *       android:exported="false" />
 */
class DemoInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        LaunchTrace.mark("ContentProvider.onCreate — before Application.onCreate!")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}