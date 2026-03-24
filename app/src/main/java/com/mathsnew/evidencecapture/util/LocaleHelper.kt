// app/src/main/java/com/mathsnew/evidencecapture/util/LocaleHelper.kt
// 新建文件 - Kotlin

package com.mathsnew.evidencecapture.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleHelper {

    private const val PREFS_NAME = "app_prefs"
    private const val KEY_LANGUAGE = "selected_language"

    // 支持的语言列表：(语言代码, strings.xml 中的 name 键)
    val SUPPORTED_LANGUAGES = listOf(
        LanguageItem("zh", "lang_zh_cn", region = null),
        LanguageItem("zh", "lang_zh_tw", region = "TW"),
        LanguageItem("en", "lang_en", region = null),
        LanguageItem("fr", "lang_fr", region = null),
        LanguageItem("de", "lang_de", region = null),
        LanguageItem("ja", "lang_ja", region = null),
        LanguageItem("ko", "lang_ko", region = null),
        LanguageItem("es", "lang_es", region = null),
        LanguageItem("pt", "lang_pt", region = null),
        LanguageItem("ru", "lang_ru", region = null),
        LanguageItem("hi", "lang_hi", region = null),
        LanguageItem("in", "lang_in", region = null)
    )

    data class LanguageItem(
        val language: String,      // ISO 639-1 语言代码
        val nameKey: String,       // strings.xml 中显示名称的 key
        val region: String? = null // 地区代码，如 "TW"
    ) {
        fun toLocale(): Locale = if (region != null)
            Locale(language, region) else Locale(language)

        // 用于 SharedPreferences 存储的唯一标识
        val code: String get() = if (region != null) "${language}_$region" else language
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 保存用户选择的语言代码 */
    fun saveLanguage(context: Context, languageCode: String) {
        getPrefs(context).edit().putString(KEY_LANGUAGE, languageCode).apply()
    }

    /** 读取用户保存的语言代码，默认跟随系统 */
    fun getSavedLanguage(context: Context): String? =
        getPrefs(context).getString(KEY_LANGUAGE, null)

    /** 是否已经手动设置过语言 */
    fun hasLanguageSet(context: Context): Boolean =
        getPrefs(context).contains(KEY_LANGUAGE)

    /**
     * 将 Context 包装为指定语言的 Context
     * 在 Application.attachBaseContext 和 Activity.attachBaseContext 中调用
     */
    fun wrap(context: Context): Context {
        val savedCode = getSavedLanguage(context) ?: return context
        val item = SUPPORTED_LANGUAGES.find { it.code == savedCode } ?: return context
        return updateResources(context, item.toLocale())
    }

    /** 切换语言并返回新 Context，调用后需重启 Activity */
    fun setLocale(context: Context, item: LanguageItem): Context {
        saveLanguage(context, item.code)
        return updateResources(context, item.toLocale())
    }

    private fun updateResources(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}