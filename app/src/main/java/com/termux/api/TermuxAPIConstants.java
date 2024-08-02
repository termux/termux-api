package com.termux.api;

import static com.termux.shared.termux.TermuxConstants.TERMUX_API_PACKAGE_NAME;
import static com.termux.shared.termux.TermuxConstants.TERMUX_PACKAGE_NAME;

public class TermuxAPIConstants {

    /**
     * Termux:API Receiver name.
     */
    public static final String TERMUX_API_RECEIVER_NAME = TERMUX_API_PACKAGE_NAME + ".TermuxApiReceiver"; // Default to "com.termux.api.TermuxApiReceiver"

    /** The Uri authority for Termux:API app file shares */
    public static final String TERMUX_API_FILE_SHARE_URI_AUTHORITY = TERMUX_PACKAGE_NAME + ".sharedfiles"; // Default: "com.termux.sharedfiles"

    public static final String TERMUX_API_CRON_ALARM_SCHEME = "com.termux.api.cron.alarm";

    public static final String TERMUX_API_CRON_CONSTRAINT_SCHEME = "com.termux.api.cron.constraint";

    public static final String TERMUX_API_CRON_EXECUTION_RESULT_SCHEME = "com.termux.api.cron.exec";
}
