package com.noteVE.domain

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** 应用级设置：语言与主题（手动切换 / 跟随系统）。 */
object Settings {

    const val LANG_SYSTEM = "system"
    const val LANG_ZH = "zh"
    const val LANG_EN = "en"
    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val SORT_NAME = "name"
    const val SORT_LAST_OPENED = "lastOpened"

    private lateinit var prefs: SharedPreferences
    private val _theme = MutableStateFlow(THEME_SYSTEM)
    private val _language = MutableStateFlow(LANG_SYSTEM)
    private val _sortBy = MutableStateFlow(SORT_NAME)
    private val _sortReverse = MutableStateFlow(false)
    private val _warnPermissionMissing = MutableStateFlow(true)
    private val _dynamicColor = MutableStateFlow(false)
    val theme: StateFlow<String> = _theme
    val language: StateFlow<String> = _language
    val sortBy: StateFlow<String> = _sortBy
    val sortReverse: StateFlow<Boolean> = _sortReverse
    val warnPermissionMissing: StateFlow<Boolean> = _warnPermissionMissing
    /** Material3 动态取色（跟随系统壁纸调色板），仅 Android 12+ 生效。 */
    val dynamicColor: StateFlow<Boolean> = _dynamicColor

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _theme.value = prefs.getString("theme", THEME_SYSTEM) ?: THEME_SYSTEM
        _language.value = prefs.getString("language", LANG_SYSTEM) ?: LANG_SYSTEM
        _sortBy.value = prefs.getString("sortBy", SORT_NAME) ?: SORT_NAME
        _sortReverse.value = prefs.getBoolean("sortReverse", false)
        _warnPermissionMissing.value = prefs.getBoolean("warnPermissionMissing", true)
        _dynamicColor.value = prefs.getBoolean("dynamicColor", false)
    }

    fun setTheme(v: String) { _theme.value = v; prefs.edit().putString("theme", v).apply() }
    fun setLanguage(v: String) { _language.value = v; prefs.edit().putString("language", v).apply() }
    fun setSort(by: String, reverse: Boolean) {
        _sortBy.value = by; _sortReverse.value = reverse
        prefs.edit().putString("sortBy", by).putBoolean("sortReverse", reverse).apply()
    }
    fun setWarnPermissionMissing(v: Boolean) {
        _warnPermissionMissing.value = v
        prefs.edit().putBoolean("warnPermissionMissing", v).apply()
    }
    fun setDynamicColor(v: Boolean) {
        _dynamicColor.value = v
        prefs.edit().putBoolean("dynamicColor", v).apply()
    }

    val currentLanguage: String get() = _language.value

    /** 当前是否中文：手动为 zh 则中文，en 则英文，system 则跟随系统。 */
    fun isZh(): Boolean = when (_language.value) {
        LANG_ZH -> true
        LANG_EN -> false
        else -> Locale.getDefault().language == "zh"
    }
}
