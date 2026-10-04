package com.noteVE.domain

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** 根据设置项为 Context 应用指定语言（返回新 Context，不修改全局配置）。 */
object LocaleHelper {

    fun apply(context: Context, language: String): Context {
        val locale = when (language) {
            Settings.LANG_ZH -> Locale.SIMPLIFIED_CHINESE
            Settings.LANG_EN -> Locale.ENGLISH
            else -> return context // 跟随系统
        }
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= 24) config.setLocales(LocaleList(locale))
        else @Suppress("DEPRECATION") config.locale = locale
        return context.createConfigurationContext(config)
    }
}
