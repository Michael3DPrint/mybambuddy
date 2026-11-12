package io.github.michael3dprint.mybambuddy

import android.app.Application
import android.content.pm.ApplicationInfo
import io.github.michael3dprint.mybambuddy.di.AppContainer
import io.github.michael3dprint.mybambuddy.util.LogPolicy

/** Application scope: owns the [AppContainer], which holds everything that must outlive a single Activity. */
class PrintDeckApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Detailed logging only in a debuggable build; a release build logs messages without server text or stack traces.
        LogPolicy.verbose = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
