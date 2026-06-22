package com.myhealthtracker.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.myhealthtracker.app.di.AppContainer

class MyHealthApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.initCelebrations(this)

        try {
            // Ensure Firebase is initialized before App Check
            FirebaseApp.initializeApp(this)
            
            val factory: AppCheckProviderFactory = if (BuildConfigCompat.isDebug(this)) {
                try {
                    val debugFactoryClass = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                    val getInstanceMethod = debugFactoryClass.getMethod("getInstance")
                    getInstanceMethod.invoke(null) as AppCheckProviderFactory
                } catch (e: Exception) {
                    Log.e("MyHealthApp", "Could not load DebugAppCheckProviderFactory via reflection", e)
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                }
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
