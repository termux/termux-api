package com.termux.api;

import com.termux.shared.logger.Logger;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class TermuxAccessibilityService extends AccessibilityService {

    private static final String LOG_TAG = "AccessibilityService";

	public static TermuxAccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Logger.logDebug(LOG_TAG, "onServiceConnected");
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
