package com.iiordanov.bVNC;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.undatech.remoteClientUi.R;

public class GlobalPreferencesFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        getPreferenceManager().setSharedPreferencesName(Constants.generalSettingsTag);
        setPreferencesFromResource(R.xml.global_preferences, s);
        if (Utils.isVnc(getContext())) {
            addPreferencesFromResource(R.xml.global_preferences_vnc);
        } else if (Utils.isRdp(getContext())) {
            addPreferencesFromResource(R.xml.global_preferences_rdp);
        } else if (Utils.isSpice(getContext())) {
            addPreferencesFromResource(R.xml.global_preferences_spice);
        }

        Preference nightMode = findPreference(Constants.nightModeTag);
        if (nightMode != null) {
            nightMode.setOnPreferenceChangeListener((preference, newValue) -> {
                try {
                    AppCompatDelegate.setDefaultNightMode(Integer.parseInt((String) newValue));
                } catch (NumberFormatException e) {
                    return false;
                }
                return true;
            });
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if ("openDefaultConnectionSettings".equals(preference.getKey())) {
            Utils.openDefaultConnectionSettings(requireContext());
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }
}
