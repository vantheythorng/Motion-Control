package com.kwan.mygesture.app

import android.app.Application
import com.kwan.mygesture.accessibility.ActionDispatcher
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
}
