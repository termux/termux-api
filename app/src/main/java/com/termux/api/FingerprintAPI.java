package com.termux.api;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.JsonWriter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricConstants;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.TermuxApiLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

/**
 * This API allows users to use device fingerprint sensor as an authentication mechanism
 */
public class FingerprintAPI {
    protected static final String TAG             = "FingerprintAPI";
    protected static final String KEY_NAME        = "TermuxFingerprintAPIKey";
    protected static final String KEYSTORE_NAME   = "AndroidKeyStore";

    // milliseconds to wait before canceling
    protected static final int SENSOR_TIMEOUT = 10000;

    // maximum authentication attempts before locked out
    protected static final int MAX_ATTEMPTS   = 5;

    // error constants
    protected static final String ERROR_UNSUPPORTED_OS_VERSION    = "ERROR_UNSUPPORTED_OS_VERSION";
    protected static final String ERROR_NO_HARDWARE               = "ERROR_NO_HARDWARE";
    protected static final String ERROR_NO_ENROLLED_FINGERPRINTS  = "ERROR_NO_ENROLLED_FINGERPRINTS";
    protected static final String ERROR_KEY_GENERATOR             = "ERROR_KEY_GENERATOR";
    protected static final String ERROR_CIPHER                    = "ERROR_CIPHER";
    protected static final String ERROR_TIMEOUT                   = "ERROR_TIMEOUT";
    protected static final String ERROR_TOO_MANY_FAILED_ATTEMPTS  = "ERROR_TOO_MANY_FAILED_ATTEMPTS";
    protected static final String ERROR_LOCKOUT                   = "ERROR_LOCKOUT";

    // fingerprint authentication result constants
    protected static final String AUTH_RESULT_SUCCESS = "AUTH_RESULT_SUCCESS";
    protected static final String AUTH_RESULT_FAILURE = "AUTH_RESULT_FAILURE";
    protected static final String AUTH_RESULT_UNKNOWN = "AUTH_RESULT_UNKNOWN";



    // store result of fingerprint initialization / authentication
    protected static FingerprintResult fingerprintResult = new FingerprintResult();

    // have we posted our result back?
    protected static boolean postedResult = false;


    /**
     * Handles setup of fingerprint sensor and writes Fingerprint result to console
     */
    static void onReceive(final Context context, final Intent intent) {
        resetFingerprintResult();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            BiometricManager fingerprintManagerCompat = BiometricManager.from(context);
            // make sure we have a valid fingerprint sensor before attempting to launch Fingerprint activity
            if (validateFingerprintSensor(context, fingerprintManagerCompat)) {
                Intent fingerprintIntent = new Intent(context, FingerprintActivity.class);
                fingerprintIntent.putExtras(intent.getExtras());
                fingerprintIntent.setFlags(FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fingerprintIntent);
            } else {
                postFingerprintResult(context, intent, fingerprintResult);
            }
        } else {
            // pre-marshmallow is unsupported
            appendFingerprintError(ERROR_UNSUPPORTED_OS_VERSION);
            postFingerprintResult(context, intent, fingerprintResult);
        }
    }

    /**
     * Writes the result of our fingerprint result to the console
     */
    protected static void postFingerprintResult(Context context, Intent intent, final FingerprintResult result) {
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

                out.name("failed_attempts").value(result.failedAttempts);
                out.name("auth_result").value(result.authResult);
                out.endObject();

                out.flush();
                out.close();
                postedResult = true;
            }
        });
    }

    /**
     * Ensure that we have a fingerprint sensor and that the user has already enrolled fingerprints
     */
    @TargetApi(Build.VERSION_CODES.M)
    protected static boolean validateFingerprintSensor(Context context, BiometricManager biometricManager) {
        boolean result = true;

        if (biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
            Toast.makeText(context, "No fingerprint scanner found!", Toast.LENGTH_SHORT).show();
            appendFingerprintError(ERROR_NO_HARDWARE);
            result = false;
        }
        if (biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE) {
            Toast.makeText(context, "No fingerprint scanner available!", Toast.LENGTH_SHORT).show();
            appendFingerprintError(ERROR_NO_HARDWARE);
            result = false;
        }

        if (biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            Toast.makeText(context, "No fingerprints enrolled", Toast.LENGTH_SHORT).show();
            appendFingerprintError(ERROR_NO_ENROLLED_FINGERPRINTS);
            result = false;
        }
        return result;
    }



    /**
     * Activity that is necessary for authenticating w/ fingerprint sensor
     */
    @TargetApi(Build.VERSION_CODES.M)
    public static class FingerprintActivity extends FragmentActivity{

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            handleFingerprint();
        }

        /**
         * Handle setup and listening of fingerprint sensor
         */
        protected void handleFingerprint() {
            Executor executor = Executors.newSingleThreadExecutor();
            authenticateWithFingerprint(this, getIntent(), executor);
        }

        /**
         * Handles authentication callback from our fingerprint sensor
         */
        protected static void authenticateWithFingerprint(final FragmentActivity context, final Intent intent, final Executor executor) {
            BiometricPrompt biometricPrompt = new BiometricPrompt(context, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    if (errorCode == BiometricConstants.ERROR_LOCKOUT) {
                        appendFingerprintError(ERROR_LOCKOUT);

                        // first time locked out, subsequent auth attempts will fail immediately for a bit
                        if (fingerprintResult.failedAttempts >= MAX_ATTEMPTS) {
                            appendFingerprintError(ERROR_TOO_MANY_FAILED_ATTEMPTS);
                        }
                    }
                    setAuthResult(AUTH_RESULT_FAILURE);
                    postFingerprintResult(context, intent, fingerprintResult);
                    TermuxApiLogger.error(errString.toString());
                }

                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    setAuthResult(AUTH_RESULT_SUCCESS);
                    postFingerprintResult(context, intent, fingerprintResult);
                }

                @Override
                public void onAuthenticationFailed() {
                    addFailedAttempt();
                }
            });

            BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder();
            builder.setTitle(intent.hasExtra("title") ? intent.getStringExtra("title") : "Authenticate");
            builder.setNegativeButtonText(intent.hasExtra("cancel") ? intent.getStringExtra("cancel") : "Cancel");
            if (intent.hasExtra("description")) {
                builder.setDescription(intent.getStringExtra("description"));
            }
            if (intent.hasExtra("subtitle")) {
                builder.setSubtitle(intent.getStringExtra("subtitle"));
            }

            // listen to fingerprint sensor
            biometricPrompt.authenticate(builder.build());

            addSensorTimeout(context, intent, biometricPrompt);
        }

        /**
         * Adds a timeout for our fingerprint sensor which will force a result return if we
         * haven't already received one
         */
        protected static void addSensorTimeout(final Context context, final Intent intent, final BiometricPrompt biometricPrompt) {
            final Handler timeoutHandler = new Handler(Looper.getMainLooper());
            timeoutHandler.postDelayed(() -> {
                if (!postedResult) {
                    appendFingerprintError(ERROR_TIMEOUT);
                    biometricPrompt.cancelAuthentication();
                    postFingerprintResult(context, intent, fingerprintResult);
                }
            }, SENSOR_TIMEOUT);
        }
    }

    /**
     * Clear out previous fingerprint result
     */
    protected static void resetFingerprintResult() {
        fingerprintResult = new FingerprintResult();
        postedResult = false;
    }

    /**
     * Increment failed authentication attempts
     */
    protected static void addFailedAttempt() {
        fingerprintResult.failedAttempts++;
    }

    /**
     * Add an error to our fingerprint result
     */
    protected static void appendFingerprintError(String error) {
        fingerprintResult.errors.add(error);
    }

    /**
     * Set the final result of our authentication
     */
    protected static void setAuthResult(String authResult) {
        fingerprintResult.authResult = authResult;
    }


    /**
     * Simple class to encapsulate information about result of a fingerprint authentication attempt
     */
    static class FingerprintResult {
        public String authResult = AUTH_RESULT_UNKNOWN;
        public int failedAttempts = 0;
        public List<String> errors = new ArrayList<>();
    }
}
