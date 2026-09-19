package com.liuli.weather.util

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.StringRes

/** 应用级 Context 持有者：供工具类与数据层解析本地化字符串。 */
@SuppressLint("StaticFieldLeak")
object AppCtx {

    private var applicationContext: Context? = null

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    fun context(): Context = requireNotNull(applicationContext) { "AppCtx 未初始化" }

    fun str(@StringRes resId: Int): String = context().getString(resId)

    fun str(@StringRes resId: Int, vararg args: Any): String = context().getString(resId, *args)
}
