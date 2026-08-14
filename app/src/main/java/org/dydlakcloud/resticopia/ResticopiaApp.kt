package org.dydlakcloud.resticopia

import android.app.Application
import timber.log.Timber

/**
 * Main Application class for Resticopia
 *
 * Initializes logging and global resources if required
 */
class ResticopiaApp: Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}