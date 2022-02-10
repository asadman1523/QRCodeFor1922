package com.jack.qrcodefor1922

import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import com.jack.qrcodefor1922.ui.MainActivity.Companion.PREFKEY


class SettingsPreference : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        preferenceManager.sharedPreferencesName = PREFKEY
        setPreferencesFromResource(R.xml.preference_main, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundResource(R.color.preference_bg_color)
    }
}