package com.darkaxt.dualdex.settings

import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.enrpau.dualscreendex.companion.model.InterfaceLanguage

object InterfaceLanguageSettings {
    fun languageTags(setting: InterfaceLanguage): String = when (setting) {
        InterfaceLanguage.AUTO -> ""
        InterfaceLanguage.EN -> "en"
        InterfaceLanguage.FR -> "fr"
        InterfaceLanguage.DE -> "de"
        InterfaceLanguage.IT -> "it"
        InterfaceLanguage.ES -> "es"
    }

    fun apply(setting: InterfaceLanguage) {
        val languageTags = languageTags(setting)
        val update = Runnable {
            if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != languageTags) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTags))
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            update.run()
        } else {
            Handler(Looper.getMainLooper()).post(update)
        }
    }
}
