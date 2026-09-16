package com.termux.api.apis;

import android.content.Context;
import android.content.Intent;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import com.termux.shared.android.SettingsProviderUtils;
import com.termux.shared.android.SettingsProviderUtils.SettingNamespace;

import android.provider.Settings;

public class SettingAPI {

    private static final String LOG_TAG = "SettingAPI";

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

		ResultReturner.returnData(apiReceiver, intent, out -> {
			String key = intent.getStringExtra("key");
			switch (intent.getStringExtra("action")) {
				case "get":
					String namespace = intent.getStringExtra("namespace").toUpperCase();
					SettingNamespace settingNamespace = SettingNamespace.valueOf(namespace);
					Object settingValue = SettingsProviderUtils.getSettingsValue(context, settingNamespace, SettingsProviderUtils.SettingType.STRING, key, "");
					out.write((String)settingValue + "\n");
					break;
				case "put":
					Settings.System.putString(context.getContentResolver(), key, intent.getStringExtra("value"));
					break;
			}
        });
    }
}
