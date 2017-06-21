/*
 * Copyright (C) 2016 Jacob Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.klinker.messenger.fragment.settings;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatDelegate;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.ListView;

import java.util.HashSet;
import java.util.Set;

import xyz.klinker.messenger.R;
import xyz.klinker.messenger.activity.SettingsActivity;
import xyz.klinker.messenger.api.implementation.Account;
import xyz.klinker.messenger.api.implementation.ApiUtils;
import xyz.klinker.messenger.shared.data.ColorSet;
import xyz.klinker.messenger.shared.data.FeatureFlags;
import xyz.klinker.messenger.shared.data.Settings;
import xyz.klinker.messenger.shared.util.ColorUtils;
import xyz.klinker.messenger.shared.util.DensityUtil;
import xyz.klinker.messenger.shared.util.SetUtils;
import xyz.klinker.messenger.view.NotificationAlertsPreference;

/**
 * Fragment for modifying app settings_global.
 */
public class GlobalSettingsFragment extends MaterialPreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.settings_global);
        initBaseTheme();
        initGlobalTheme();
        initFontSize();
        initKeyboardLayout();
        initRounderBubbles();
        initSwipeDelete();
        initNotificationActions();
        initDeliveryReports();
        initSoundEffects();
        initStripUnicode();
        initMmsConfiguration();
        initNotificationHistory();
    }

    @Override
    public void onStop() {
        super.onStop();
        Settings.get(getActivity()).forceUpdate();
    }

    private void initGlobalTheme() {
        findPreference(getString(R.string.pref_global_color_theme))
                .setOnPreferenceChangeListener((preference, o) -> {
                    ColorSet initialColors = ColorSet.getFromString(getActivity(),
                            Settings.get(getActivity()).themeColorString);
                    String colorString = (String) o;
                    new ApiUtils().updateGlobalThemeColor(Account.get(getActivity()).accountId,
                            colorString);

                    ColorSet colors = ColorSet.getFromString(getActivity(), colorString);
                    ColorUtils.animateToolbarColor(getActivity(),
                            initialColors.color, colors.color);
                    ColorUtils.animateStatusBarColor(getActivity(),
                            initialColors.colorDark, colors.colorDark);

                    return true;
                });
    }

    private void initBaseTheme() {
        findPreference(getString(R.string.pref_base_theme))
                .setOnPreferenceChangeListener((preference, o) -> {
                    String newValue = (String) o;
                    if (!newValue.equals("day_night") && !newValue.equals("light")) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    }

                    new ApiUtils().updateBaseTheme(Account.get(getActivity()).accountId,
                            newValue);

                    getActivity().recreate();

                    return true;
                });
    }

    private void initFontSize() {
        findPreference(getString(R.string.pref_font_size))
                .setOnPreferenceChangeListener((preference, o) -> {
                    String size = (String) o;
                    new ApiUtils().updateFontSize(Account.get(getActivity()).accountId,
                            size);

                    return true;
                });
    }

    private void initKeyboardLayout() {
        findPreference(getString(R.string.pref_keyboard_layout))
                .setOnPreferenceChangeListener((preference, o) -> {
                    String layout = (String) o;
                    new ApiUtils().updateKeyboardLayout(Account.get(getActivity()).accountId,
                            layout);

                    return true;
                });
    }

    private void initRounderBubbles() {
        findPreference(getString(R.string.pref_rounder_bubbles))
                .setOnPreferenceChangeListener((preference, o) -> {
                    boolean rounder = (boolean) o;
                    new ApiUtils().updateRounderBubbles(Account.get(getActivity()).accountId,
                            rounder);
                    return true;
                });
    }

    private void initNotificationActions() {
        Preference actions = findPreference(getString(R.string.pref_notification_actions));
        actions.setOnPreferenceChangeListener((preference, o) -> {
            Set<String> options = (Set<String>) o;
            new ApiUtils().updateNotificationActions(Account.get(getActivity()).accountId,
                    SetUtils.stringify(options));
            return true;
        });
    }

    private void initSwipeDelete() {
        findPreference(getString(R.string.pref_swipe_delete))
                .setOnPreferenceChangeListener((preference, o) -> {
                    boolean delete = (boolean) o;
                    new ApiUtils().updateSwipeToDelete(Account.get(getActivity()).accountId,
                            delete);
                    return true;
                });
    }

    private void initDeliveryReports() {
        Preference normal = findPreference(getString(R.string.pref_delivery_reports));
        Preference giffgaff = findPreference(getString(R.string.pref_giffgaff));

        normal.setOnPreferenceChangeListener((preference, o) -> {
                    boolean delivery = (boolean) o;
                    new ApiUtils().updateDeliveryReports(Account.get(getActivity()).accountId,
                            delivery);
                    return true;
                });

        giffgaff.setOnPreferenceChangeListener((preference, o) -> {
                    boolean delivery = (boolean) o;
                    new ApiUtils().updateGiffgaffDeliveryReports(Account.get(getActivity()).accountId,
                            delivery);
                    return true;
                });

        TelephonyManager manager = (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
        String carrierName = manager.getNetworkOperatorName();

        if (carrierName != null && !(carrierName.equalsIgnoreCase("giffgaff") ||
                carrierName.replace(" ", "").equalsIgnoreCase("o2-uk"))) {
            ((PreferenceGroup) findPreference(getString(R.string.pref_notification_category))).removePreference(giffgaff);
        } else {
            ((PreferenceGroup) findPreference(getString(R.string.pref_notification_category))).removePreference(normal);
        }
    }

    private void initStripUnicode() {
        findPreference(getString(R.string.pref_strip_unicode))
                .setOnPreferenceChangeListener((preference, o) -> {
                    boolean strip = (boolean) o;
                    new ApiUtils().updateStripUnicode(Account.get(getActivity()).accountId,
                            strip);
                    return true;
                });
    }

    private void initMmsConfiguration() {
        findPreference(getString(R.string.pref_mms_configuration))
                .setOnPreferenceClickListener(preference -> {
                    SettingsActivity.startMMSSettings(getActivity());
                    return false;
                });
    }

    private void initNotificationHistory() {
        Preference pref = findPreference(getString(R.string.pref_history_in_notifications));
        pref.setOnPreferenceChangeListener((preference, o) -> {
                    boolean history = (boolean) o;
                    new ApiUtils().updateShowHistoryInNotification(Account.get(getActivity()).accountId,
                            history);
                    return true;
                });

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            ((PreferenceCategory) findPreference(getString(R.string.pref_notification_category)))
                    .removePreference(pref);
        }
    }

    private void initSoundEffects() {
        findPreference(getString(R.string.pref_sound_effects))
                .setOnPreferenceChangeListener((preference, o) -> {
                    boolean effects = (boolean) o;
                    new ApiUtils().updateSoundEffects(Account.get(getActivity()).accountId,
                            effects);
                    return true;
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ((NotificationAlertsPreference) findPreference(getString(R.string.pref_alert_types)))
                .handleRingtoneResult(requestCode, resultCode, data);
    }

}
