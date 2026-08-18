package com.myhealthtracker.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.myhealthtracker.app.di.AppContainer
import com.myhealthtracker.app.util.MealImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

class MyHealthApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)

        // Remove on-device meal images no longer referenced by any meal doc. Gate on a
        // NON-EMPTY snapshot for a signed-in user: the meals StateFlow starts empty and the
        // first emission may precede the Firestore load, so sweeping on empty would wrongly
        // delete valid images. Deletions also clean their own image at delete time (Task 9),
        // so skipping the sweep for a genuinely zero-meal user is safe.
        CoroutineScope(Dispatchers.IO).launch {
            AppContainer.mealRepository.meals
                .filter { AppContainer.currentUid() != null && it.isNotEmpty() }
                .take(1)
                .collect { meals ->
                    val referenced = meals.mapNotNull { it.localImagePath }.toSet()
                    MealImageStore.sweepOrphans(MealImageStore.dir(this@MyHealthApp), referenced)
                }
        }

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
