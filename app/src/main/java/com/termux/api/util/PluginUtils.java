package com.termux.api.util;

import android.content.Context;

import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_API_APP;

/**
 * Based on com.termux.tasker.utils.PluginUtils, reference for more information.
 */
public class PluginUtils {

    /**
     * Try to get the next unique {PendingIntent} request code that isn't already being used by
     * the app and which would create a unique {PendingIntent} that doesn't conflict with that
     * of any other execution commands.
     *
     * @param context The {@link Context} for operations.
     * @return Returns the request code that should be safe to use.
     */
    public synchronized static int getLastPendingIntentRequestCode(final Context context) {
        if (context == null) return TERMUX_API_APP.DEFAULT_VALUE_KEY_LAST_PENDING_INTENT_REQUEST_CODE;

        TermuxAPIAppSharedPreferences preferences = TermuxAPIAppSharedPreferences.build(context);
        if (preferences == null) return TERMUX_API_APP.DEFAULT_VALUE_KEY_LAST_PENDING_INTENT_REQUEST_CODE;

        int lastPendingIntentRequestCode = preferences.getLastPendingIntentRequestCode();

        int nextPendingIntentRequestCode = lastPendingIntentRequestCode + 1;

        if (nextPendingIntentRequestCode == Integer.MAX_VALUE || nextPendingIntentRequestCode < 0)
            nextPendingIntentRequestCode = TERMUX_API_APP.DEFAULT_VALUE_KEY_LAST_PENDING_INTENT_REQUEST_CODE;

        preferences.setLastPendingIntentRequestCode(nextPendingIntentRequestCode);
        return nextPendingIntentRequestCode;
    }

}