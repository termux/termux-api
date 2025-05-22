package com.termux.api.util;

import android.app.PendingIntent;
import android.os.Build;

public class PendingIntentUtils {

    /**
     * Get {@link PendingIntent#FLAG_IMMUTABLE} flag.
     *
     * - https://developer.android.com/guide/components/intents-filters#DeclareMutabilityPendingIntent
     * - https://developer.android.com/about/versions/12/behavior-changes-12#pending-intent-mutability
     */
    public static int getPendingIntentImmutableFlag() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            return PendingIntent.FLAG_IMMUTABLE;
        else
            return 0;
    }

    /**
     * Get {@link PendingIntent#FLAG_MUTABLE} flag.
     *
     * - https://developer.android.com/guide/components/intents-filters#DeclareMutabilityPendingIntent
     * - https://developer.android.com/about/versions/12/behavior-changes-12#pending-intent-mutability
     */
    public static int getPendingIntentMutableFlag() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            return PendingIntent.FLAG_MUTABLE;
        else
            return 0;
    }

}
