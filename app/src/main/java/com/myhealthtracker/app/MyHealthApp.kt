package com.myhealthtracker.app

import android.app.Application
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.ktx.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.ktx.Firebase

class MyHealthApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val factory = if (BuildConfigCompat.isDebug(this)) {
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        Firebase.appCheck.installAppCheckProviderFactory(factory)
    }
}

private object BuildConfigCompat {
    // buildConfig is disabled in this module; detect debuggable flag at runtime instead.
    fun isDebug(app: Application): Boolean {
        val flags = app.applicationInfo.flags
        return (flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
