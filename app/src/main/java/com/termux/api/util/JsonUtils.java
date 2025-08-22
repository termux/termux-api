package com.termux.api.util;

import android.util.JsonWriter;

import com.termux.shared.logger.Logger;

import java.io.IOException;

public class JsonUtils {

    public static final String LOG_TAG = "JsonUtils";



    public static void putBooleanValueIfSet(JsonWriter out, String key, Boolean value) {
        if (out == null || key == null || key.isEmpty() || value == null) return;

        try {
            out.name(key).value(value);
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to put \"" + key + "\" with boolean value \"" + value + "\"", e);
        }
    }

    public static void putIntegerIfSet(JsonWriter out, String key, Integer value) {
        if (out == null || key == null || key.isEmpty() || value == null) return;

        try {
            out.name(key).value(value);
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to put \"" + key + "\" with integer value \"" + value + "\"", e);
        }
    }

    public static void putLongIfSet(JsonWriter out, String key, Long value) {
        if (out == null || key == null || key.isEmpty() || value == null) return;

        try {
            out.name(key).value(value);
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to put \"" + key + "\" with long value \"" + value + "\"", e);
        }
    }

    public static void putDoubleIfSet(JsonWriter out, String key, Double value) {
        if (out == null || key == null || key.isEmpty() || value == null) return;

        try {
            out.name(key).value(value);
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to put \"" + key + "\" with double value \"" + value + "\"", e);
        }
    }

    public static void putStringIfSet(JsonWriter out, String key, String value) {
        if (out == null || key == null || key.isEmpty() || value == null) return;

        try {
            out.name(key).value(value);
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to put \"" + key + "\" with string value \"" + value + "\"", e);
        }
    }

}
