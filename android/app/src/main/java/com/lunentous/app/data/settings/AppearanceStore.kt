package com.lunentous.app.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** SYSTEM follows the device's light/dark setting (the original,
 * still-default behavior); MOCHA/LATTE pin the app to one Catppuccin
 * palette regardless of the device setting. */
enum class ThemeVariant { SYSTEM, MOCHA, LATTE }

/** Plain (unencrypted) SharedPreferences -- unlike SessionStore, nothing
 * here is sensitive, so there's no reason to pay Keystore's cost for it. */
class AppearanceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("lunentous_appearance", Context.MODE_PRIVATE)

    private val themeVariantFlow = MutableStateFlow(loadThemeVariant())
    val themeVariant: StateFlow<ThemeVariant> = themeVariantFlow

    fun setThemeVariant(variant: ThemeVariant) {
        prefs.edit().putString(KEY_THEME_VARIANT, variant.name).apply()
        themeVariantFlow.value = variant
    }

    private fun loadThemeVariant(): ThemeVariant =
        prefs.getString(KEY_THEME_VARIANT, null)
            ?.let { stored -> runCatching { ThemeVariant.valueOf(stored) }.getOrNull() }
            ?: ThemeVariant.SYSTEM

    companion object {
        private const val KEY_THEME_VARIANT = "theme_variant"
    }
}
