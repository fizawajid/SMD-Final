package com.example.finalproject

import android.app.Application
import android.util.Log
import com.example.finalproject.SyncInitializer

class SafeMeApplication : Application() {

    companion object {
        private const val TAG = "SafeMeApplication"
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "🚀 SafeMe Application Starting...")

        // Initialize automatic alert sync system
        SyncInitializer.initialize(this)

        Log.d(TAG, "✅ SafeMe Application Initialized")
    }
}

// Don't forget to register this in AndroidManifest.xml:
// <application
//     android:name=".SafeMeApplication"
//     ...
// >