package com.liuli.weather.util

import java.util.Locale

/**
 * 按应用当前语言生成各数据源的 lang 参数，
 * 保证接口返回的天气描述与界面语言一致。
 */
object ApiLang {

    private fun tag(): String =
        AppCtx.context().resources.configuration.locales[0]
            .toLanguageTag().lowercase(Locale.ROOT)

    private fun isChinese() = tag().startsWith("zh")

    private fun isTraditionalChinese() = tag().let {
        it.contains("hant") || it.contains("-tw") || it.contains("-hk") || it.contains("-mo")
    }

    private fun isJapanese() = tag().startsWith("ja")

    /** 彩云天气：仅支持 zh_CN / en_US。 */
    fun caiyun(): String = if (isChinese()) "zh_CN" else "en_US"

    /** 和风天气：zh / zh-hant / en / ja。 */
    fun qweather(): String = when {
        isTraditionalChinese() -> "zh-hant"
        isChinese() -> "zh"
        isJapanese() -> "ja"
        else -> "en"
    }

    /** AccuWeather：zh-cn / zh-tw / en-us / ja-jp。 */
    fun accuWeather(): String = when {
        isTraditionalChinese() -> "zh-tw"
        isChinese() -> "zh-cn"
        isJapanese() -> "ja-jp"
        else -> "en-us"
    }

    /** OpenWeather：zh_cn / zh_tw / en / ja。 */
    fun openWeather(): String = when {
        isTraditionalChinese() -> "zh_tw"
        isChinese() -> "zh_cn"
        isJapanese() -> "ja"
        else -> "en"
    }
}
