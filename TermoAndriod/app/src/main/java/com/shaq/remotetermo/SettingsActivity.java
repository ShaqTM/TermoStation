package com.shaq.remotetermo;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p/>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */

public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content,new SettingsFragment())
                .commit();

    }
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Intent i  = new Intent(TermoService.ACTION_UPDATE_PREFS);
        i.setPackage("com.shaq.remotetermo");
        getApplicationContext().startService(i);
    }
    @Override
    protected void onStop() {
        super.onStop();
        Intent i  = new Intent(TermoService.ACTION_UPDATE_PREFS);
        getApplicationContext().sendBroadcast(i);
    }
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // setupSimplePreferencesScreen();
    }




public static class SettingsFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {
    /**
     * Determines whether to always show the simplified settings UI, where
     * settings are presented in a single list. When false, settings are shown
     * as a master/detail two-pane view on tablets. When true, a single pane is
     * shown on tablets.
     */
    private static final boolean ALWAYS_SIMPLE_PREFS = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_general);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
        Boolean fireBaseEnabled = prefs.getBoolean(getString(R.string.firebase_enable),false);
        Boolean searchByName = prefs.getBoolean(getString(R.string.searchByName),true);
        findPreference(getString(R.string.device_mDNS)).setEnabled(searchByName&&!fireBaseEnabled);
        findPreference(getString(R.string.device_ip)).setEnabled(!searchByName&&!fireBaseEnabled);
        findPreference(getString(R.string.device_port)).setEnabled(!searchByName&&!fireBaseEnabled);
        findPreference(getString(R.string.searchByName)).setEnabled(!fireBaseEnabled);
        findPreference(getString(R.string.searchWiFiOnly)).setEnabled(!fireBaseEnabled);
        findPreference(getString(R.string.searchByName)).setOnPreferenceChangeListener(this);
        findPreference(getString(R.string.firebase_enable)).setOnPreferenceChangeListener(this);

        //     setupActionBar();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
        Boolean fireBaseEnabled = prefs.getBoolean(getString(R.string.firebase_enable),false);
        Boolean searchByName = prefs.getBoolean(getString(R.string.searchByName),true);
        if (preference.getKey().equals(getString(R.string.searchByName))){
            searchByName = (Boolean) value;
        } else if (preference.getKey().equals(getString(R.string.firebase_enable))){
            fireBaseEnabled = (Boolean) value;
        }
        findPreference(getString(R.string.device_mDNS)).setEnabled(searchByName&&!fireBaseEnabled);
        findPreference(getString(R.string.device_ip)).setEnabled(!searchByName&&!fireBaseEnabled);
        findPreference(getString(R.string.device_port)).setEnabled(!searchByName&&!fireBaseEnabled);
        findPreference(getString(R.string.searchByName)).setEnabled(!fireBaseEnabled);
        findPreference(getString(R.string.searchWiFiOnly)).setEnabled(!fireBaseEnabled);
        return true;
    }

}
}