package com.termux.api.apis;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.JsonWriter;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.util.ArrayList;
import java.util.List;

import static android.app.Activity.RESULT_OK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

/**
 * This API allows users to use device password prompt (PIN/Pattern/Password) as an authentication mechanism.
 */
public class PasswordAPI {

    protected static final String TAG = "PasswordAPI";
    protected static final int REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 1;
    // milliseconds to wait before canceling the prompt
    protected static final int PROMPT_TIMEOUT = 10000;

    // error constants
    protected static final String ERROR_NO_PASSWORD = "ERROR_NO_PASSWORD";
    protected static final String ERROR_TIMEOUT = "ERROR_TIMEOUT";

    // password authentication result constants
    protected static final String AUTH_RESULT_SUCCESS = "AUTH_RESULT_SUCCESS";
    protected static final String AUTH_RESULT_FAILURE = "AUTH_RESULT_FAILURE";
    protected static final String AUTH_RESULT_UNKNOWN = "AUTH_RESULT_UNKNOWN";

    // store result of password initialization / authentication
    protected static PasswordResult passwordResult = new PasswordResult();

    // have we posted our result back?
    protected static boolean postedResult = false;

    /**
     * Handles setup of the password prompt and writes result to the console.
     */
    public static void onReceive(final Context context, final Intent intent) {
        Logger.logDebug(TAG, "onReceive");

        resetPasswordResult();

        if (validatePasswordPrompt(context)) {
            Intent passwordIntent = new Intent(context, PasswordActivity.class);
            passwordIntent.putExtras(intent.getExtras());
            passwordIntent.setFlags(FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(passwordIntent);
        } else {
            postPasswordResult(context, intent, passwordResult);
        }
    }

    /**
     * Writes the result of our password authentication to the console.
     */
    protected static void postPasswordResult(Context context, Intent intent, final PasswordResult result) {
        ResultReturner.returnData(context, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                out.beginObject();

                out.name("errors");
                out.beginArray();
                for (String error : result.errors) {
                    out.value(error);
                }
                out.endArray();

                out.name("auth_result").value(result.authResult);
                out.endObject();

                out.flush();
                out.close();
                postedResult = true;
            }
        });
    }

    /**
     * Ensure that the device is secured with a password (PIN/Pattern/Password).
     */
    protected static boolean validatePasswordPrompt(Context context) {
        KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        boolean result = true;

        if (keyguardManager == null || !keyguardManager.isDeviceSecure()) {
            appendPasswordError(ERROR_NO_PASSWORD);
            result = false;
        }
        return result;
    }

    /**
     * Activity that handles prompting the user for their device credentials.
     */
    public static class PasswordActivity extends FragmentActivity {

        private static final String TAG = "PasswordActivity";

        @Override
        public void onCreate(Bundle savedInstanceState) {
            Logger.logDebug(TAG, "onCreate");
            super.onCreate(savedInstanceState);
            handlePasswordPrompt();
        }

        /**
         * Handle setup and display of the password prompt.
         */
        protected void handlePasswordPrompt() {
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            String title = getIntent().hasExtra("title") ? getIntent().getStringExtra("title") : "Authenticate";
            String description = getIntent().hasExtra("description") ? getIntent().getStringExtra("description") : "";
            Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(title, description);

            if (intent != null) {
                startActivityForResult(intent, REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS);
                addPromptTimeout();
            } else {
                // No credential is set (should not happen due to prior validation)
                appendPasswordError(ERROR_NO_PASSWORD);
                setAuthResult(AUTH_RESULT_FAILURE);
                postPasswordResult(PasswordActivity.this, getIntent(), passwordResult);
                finish();
            }
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS) {
                if (resultCode == RESULT_OK) {
                    setAuthResult(AUTH_RESULT_SUCCESS);
                } else {
                    setAuthResult(AUTH_RESULT_FAILURE);
                }
                postPasswordResult(PasswordActivity.this, getIntent(), passwordResult);
                finish();
            }
        }

        /**
         * Adds a timeout for the password prompt which will force a result return if we haven't already received one.
         */
        protected void addPromptTimeout() {
            final Handler timeoutHandler = new Handler(Looper.getMainLooper());
            timeoutHandler.postDelayed(() -> {
                if (!postedResult) {
                    appendPasswordError(ERROR_TIMEOUT);
                    setAuthResult(AUTH_RESULT_FAILURE);
                    postPasswordResult(PasswordActivity.this, getIntent(), passwordResult);
                    finish();
                }
            }, PROMPT_TIMEOUT);
        }
    }

    /**
     * Clear out previous password authentication result.
     */
    protected static void resetPasswordResult() {
        passwordResult = new PasswordResult();
        postedResult = false;
    }

    /**
     * Add an error to our password result.
     */
    protected static void appendPasswordError(String error) {
        passwordResult.errors.add(error);
    }

    /**
     * Set the final result of our authentication.
     */
    protected static void setAuthResult(String authResult) {
        passwordResult.authResult = authResult;
    }

    /**
     * Simple class to encapsulate information about the result of a password authentication attempt.
     */
    static class PasswordResult {
        public String authResult = AUTH_RESULT_UNKNOWN;
        public List<String> errors = new ArrayList<>();
    }
}
