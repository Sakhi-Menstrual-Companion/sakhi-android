package team.sakhi.android.platform

import androidx.appcompat.app.AppCompatDelegate
import androidx.annotation.StringRes
import androidx.core.os.LocaleListCompat
import java.util.Locale
import team.sakhi.preferences.LanguagePreferenceStore

/**
 * Port of iOS's `Language` enum (`Resources/Localization/LocalizationManager.swift`) --
 * lives on the Android platform side, not shared KMM, exactly like iOS keeps its own copy
 * in the app layer rather than the shared framework. `displayName` is the language's own
 * native script (matches iOS `Language.name`, renamed here to avoid clashing with Kotlin's
 * built-in `Enum.name`); `nativeName` is the English gloss shown in parentheses in the
 * picker (matches iOS `Language.nativeName`, a real but confusingly named pair kept as-is
 * for 1:1 parity).
 */
enum class Language(
    val code: String,
    @StringRes val displayNameRes: Int,
    @StringRes val nativeNameRes: Int,
    val flag: String,
) {
    ENGLISH("en", R.string.platform_language_display_english, R.string.platform_language_native_english, "🇬🇧"),
    HINDI("hi", R.string.platform_language_display_hindi, R.string.platform_language_native_hindi, "🇮🇳"),
    BENGALI("bn", R.string.platform_language_display_bengali, R.string.platform_language_native_bengali, "🇧🇩"),
    TAMIL("ta", R.string.platform_language_display_tamil, R.string.platform_language_native_tamil, "🇮🇳"),
    ;

    companion object {
        fun fromCode(code: String): Language = entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

/**
 * Triggers the real OS-level per-app locale switch (`AppCompatDelegate.setApplicationLocales`)
 * and mirrors the choice into the shared `LanguagePreferenceStore` so KMM-side
 * `SanityLocalizedString.localized(languageCode)` calls and `Locale.getDefault().language`
 * reads (already used by `ContentPageScreen.kt`) both see the new language immediately.
 * Port of iOS's `SanityContentStore.changeLanguage(to:)` + `LocalizationManager.currentLanguage`
 * combined -- Android has no CMS-content re-fetch step (KMM content is read live by
 * language code, not re-localized from a cached snapshot like iOS's Realm-backed store), so
 * this is simpler: set the OS locale, persist the code, done. The OS handles re-localizing
 * `strings.xml` resources and recreating the current activity itself (a standard
 * `Configuration` change), the same "app reloads" behavior iOS's confirmation copy describes.
 */
class AndroidLocaleManager(private val languageStore: LanguagePreferenceStore) {
    val currentLanguage: Language
        get() = Language.fromCode(activeLanguageCode())

    fun setLanguage(language: Language) {
        if (language == currentLanguage) return
        languageStore.setLanguageCode(language.code)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.code))
    }

    fun syncPersistedLanguageWithActiveLocale() {
        val activeLanguageCode = activeLanguageCode()
        if (Language.entries.none { it.code == activeLanguageCode }) return
        if (languageStore.languageCode.value == activeLanguageCode) return
        languageStore.setLanguageCode(activeLanguageCode)
    }

    private fun activeLanguageCode(): String {
        val localeTag = when {
            !AppCompatDelegate.getApplicationLocales().isEmpty ->
                AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag()
            !LocaleListCompat.getAdjustedDefault().isEmpty ->
                LocaleListCompat.getAdjustedDefault()[0]?.toLanguageTag()
            else -> Locale.getDefault().toLanguageTag()
        }.orEmpty()

        return Locale.forLanguageTag(localeTag)
            .language
            .lowercase(Locale.ROOT)
            .ifBlank { LanguagePreferenceStore.DEFAULT_LANGUAGE_CODE }
    }
}
