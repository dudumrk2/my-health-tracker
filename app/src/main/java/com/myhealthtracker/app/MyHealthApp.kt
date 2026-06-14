package com.myhealthtracker.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class MyHealthApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        try {
            // Ensure Firebase is initialized before App Check
            FirebaseApp.initializeApp(this)
            
            val factory = if (BuildConfigCompat.isDebug(this)) {
                DebugAppCheckProviderFactory.getInstance()
            } else {
                PlayIntegrityAppCheckProviderFactory.getInstance()
            }
            
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(factory)
            Log.d("MyHealthApp", "App Check initialized successfully")
        } catch (e: Exception) {
            Log.e("MyHealthApp", "Failed to initialize App Check: ${e.message}", e)
        }
    }
}

private object BuildConfigCompat {
    // buildConfig is disabled in this module; detect debuggable flag at runtime instead.
    fun isDebug(app: Application): Boolean {
        val flags = app.applicationInfo.flags
        return (flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
