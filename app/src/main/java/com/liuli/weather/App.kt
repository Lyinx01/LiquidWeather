package com.liuli.weather

import android.app.Application
import com.liuli.weather.util.AppCtx

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        AppCtx.init(this)
    }
}
