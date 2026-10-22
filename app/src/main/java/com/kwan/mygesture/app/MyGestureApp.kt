package com.kwan.mygesture.app

import android.app.Application
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.kwan.mygesture.accessibility.ActionDispatcher
import com.kwan.mygesture.data.ProfileRepository
import com.kwan.mygesture.motion.MotionStateStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class MyGestureApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MyGestureApp)
            modules(appModule)
        }
    }
}

val appModule = module {
    single { ActionDispatcher(androidContext()) }
    single { MotionStateStore() }
    // The app's only DataStore instance (DataStore requires one per file per process).
    single {
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { androidContext().preferencesDataStoreFile("settings") },
        )
    }
    single { ProfileRepository(get()) }
}
