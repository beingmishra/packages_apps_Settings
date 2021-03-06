/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.connecteddevice.usb;

import static android.net.ConnectivityManager.TETHERING_USB;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.os.Bundle;

import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.widget.RadioButtonPickerFragment;
import com.android.settingslib.widget.CandidateInfo;
import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.RadioButtonPreference;

import com.google.android.collect.Lists;

import java.util.List;

/**
 * Provides options for selecting the default USB mode.
 */
public class UsbDefaultFragment extends RadioButtonPickerFragment {
    @VisibleForTesting
    UsbBackend mUsbBackend;
    @VisibleForTesting
    ConnectivityManager mConnectivityManager;
    @VisibleForTesting
    OnStartTetheringCallback mOnStartTetheringCallback = new OnStartTetheringCallback();
    @VisibleForTesting
    long mPreviousFunctions;
    @VisibleForTesting
    long mCurrentFunctions;
    @VisibleForTesting
    boolean mIsStartTethering = false;

    private UsbConnectionBroadcastReceiver mUsbReceiver;

    @VisibleForTesting
    UsbConnectionBroadcastReceiver.UsbConnectionListener mUsbConnectionListener =
            (connected, functions, powerRole, dataRole) -> {
                if (mIsStartTethering) {
                    mCurrentFunctions = functions;
                    refresh(functions);
                }
            };

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mUsbBackend = new UsbBackend(context);
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
        mUsbReceiver = new UsbConnectionBroadcastReceiver(context, mUsbConnectionListener,
                mUsbBackend);
        getSettingsLifecycle().addObserver(mUsbReceiver);
        mCurrentFunctions = mUsbBackend.getDefaultUsbFunctions();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        getPreferenceScreen().addPreference(new FooterPreference.Builder(getActivity()).setTitle(
                R.string.usb_default_info).build());
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.USB_DEFAULT;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.usb_default_fragment;
    }

    @Override
    protected List<? extends CandidateInfo> getCandidates() {
        List<CandidateInfo> ret = Lists.newArrayList();
        for (final long option : UsbDetailsFunctionsController.FUNCTIONS_MAP.keySet()) {
            final String title = getContext().getString(
                    UsbDetailsFunctionsController.FUNCTIONS_MAP.get(option));
            final String key = UsbBackend.usbFunctionsToString(option);

            // Only show supported functions
            if (mUsbBackend.areFunctionsSupported(option)) {
                ret.add(new CandidateInfo(true /* enabled */) {
                    @Override
                    public CharSequence loadLabel() {
                        return title;
                    }

                    @Override
                    public Drawable loadIcon() {
                        return null;
                    }

                    @Override
                    public String getKey() {
                        return key;
                    }
                });
            }
        }
        return ret;
    }

    @Override
    protected String getDefaultKey() {
        return UsbBackend.usbFunctionsToString(mUsbBackend.getDefaultUsbFunctions());
    }

    @Override
    protected boolean setDefaultKey(String key) {
        long functions = UsbBackend.usbFunctionsFromString(key);
        mPreviousFunctions = mUsbBackend.getCurrentFunctions();
        if (!Utils.isMonkeyRunning()) {
            if (functions == UsbManager.FUNCTION_RNDIS) {
                // We need to have entitlement check for usb tethering, so use API in
                // ConnectivityManager.
                mIsStartTethering = true;
                mConnectivityManager.startTethering(TETHERING_USB, true /* showProvisioningUi */,
                        mOnStartTetheringCallback);
            } else {
                mIsStartTethering = false;
                mCurrentFunctions = functions;
                mUsbBackend.setDefaultUsbFunctions(functions);
            }

        }
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        mUsbBackend.setDefaultUsbFunctions(mCurrentFunctions);
    }

    @VisibleForTesting
    final class OnStartTetheringCallback extends
            ConnectivityManager.OnStartTetheringCallback {

        @Override
        public void onTetheringStarted() {
            super.onTetheringStarted();
            // Set default usb functions again to make internal data persistent
            mCurrentFunctions = UsbManager.FUNCTION_RNDIS;
            mUsbBackend.setDefaultUsbFunctions(UsbManager.FUNCTION_RNDIS);
        }

        @Override
        public void onTetheringFailed() {
            super.onTetheringFailed();
            mUsbBackend.setDefaultUsbFunctions(mPreviousFunctions);
            updateCandidates();
        }
    }

    private void refresh(long functions) {
        final PreferenceScreen screen = getPreferenceScreen();
        for (long option : UsbDetailsFunctionsController.FUNCTIONS_MAP.keySet()) {
            final RadioButtonPreference pref =
                    screen.findPreference(UsbBackend.usbFunctionsToString(option));
            if (pref != null) {
                final boolean isSupported = mUsbBackend.areFunctionsSupported(option);
                pref.setEnabled(isSupported);
                if (isSupported) {
                    pref.setChecked(functions == option);
                }
            }
        }
    }
}