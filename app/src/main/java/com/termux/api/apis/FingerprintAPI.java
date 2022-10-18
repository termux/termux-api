package com.termux.api.apis;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.JsonWriter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.hardware.fingerprint.FingerprintManagerCompat;
import androidx.fragment.app.FragmentActivity;

import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

/**
 * This API allows users to use device fingerprint sensor as an authentication mechanism
 */
public class FingerprintAPI {

    protected static final String TAG             = "FingerprintAPI";
    protected static final String KEY_NAME        = "TermuxFingerprintAPIKey";
    protected static final String KEYSTORE_NAME   = "AndroidKeyStore";

    protected static int SENSOR_TIMEOUT;

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
    protected static boolean timedOut = false;

    private static final String LOG_TAG = "FingerprintAPI";

    private static final Lock lock = new ReentrantLock();
    private static final Condition condition = lock.newCondition();
    protected static final String EXTRA_LOCK_ACTION = "EXTRA_LOCK_ACTION";

    /**
     * Handles setup of fingerprint sensor and writes Fingerprint result to console
     */
    public static void onReceive(final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");
        SENSOR_TIMEOUT = intent.getIntExtra("authenticationTimeout", 10);
        if (SENSOR_TIMEOUT != -1) SENSOR_TIMEOUT *= 1000;

        resetFingerprintResult();

        FingerprintManagerCompat fingerprintManagerCompat = FingerprintManagerCompat.from(context);
        // make sure we have a valid fingerprint sensor before attempting to launch Fingerprint activity
        if (validateFingerprintSensor(context, fingerprintManagerCompat)) {
            Intent fingerprintIntent = new Intent(context, FingerprintActivity.class);
            fingerprintIntent.putExtras(intent.getExtras());
            fingerprintIntent.setFlags(FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(fingerprintIntent);

            if (intent.getBooleanExtra(EXTRA_LOCK_ACTION, false)) {
                lock.lock();
                try {
                    if (SENSOR_TIMEOUT != -1) {
                        if (!condition.await(SENSOR_TIMEOUT+5000, TimeUnit.MILLISECONDS)) {
                            timedOut = true;
                            Logger.logDebug(LOG_TAG, "Lock timed out");
                        }
                    } else condition.await();
                } catch (InterruptedException e) {
                    // If interrupted, nothing currently
                } finally {
                    lock.unlock();
                }
            }
        } else {
            postFingerprintResult(context, intent, fingerprintResult);
        }
    }

    /**
     * Writes the result of our fingerprint result to the console
     */
    protected static void postFingerprintResult(Context context, Intent intent, final FingerprintResult result) {
        if (intent.getBooleanExtra(EXTRA_LOCK_ACTION, false)) {
            lock.lock();
            try {
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        } else {
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
    }

    /**
     * Ensure that we have a fingerprint sensor and that the user has already enrolled fingerprints
     */
    protected static boolean validateFingerprintSensor(Context context, FingerprintManagerCompat fingerprintManagerCompat) {
        boolean result = true;

        if (!fingerprintManagerCompat.isHardwareDetected()) {
            Toast.makeText(context, "No fingerprint scanner found!", Toast.LENGTH_SHORT).show();
            appendFingerprintError(ERROR_NO_HARDWARE);
            result = false;
        }

        if (!fingerprintManagerCompat.hasEnrolledFingerprints()) {
            Toast.makeText(context, "No fingerprints enrolled", Toast.LENGTH_SHORT).show();
            appendFingerprintError(ERROR_NO_ENROLLED_FINGERPRINTS);
            result = false;
        }
        return result;
    }



    /**
     * Activity that is necessary for authenticating w/ fingerprint sensor
     */
    public static class FingerprintActivity extends FragmentActivity{

        private static final String LOG_TAG = "FingerprintActivity";

        @Override
        public void onCreate(Bundle savedInstanceState) {
            Logger.logDebug(LOG_TAG, "onCreate");

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
            BiometricPrompt biometricPrompt = new BiometricPrompt(context, executor,
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                            if (errorCode == BiometricPrompt.ERROR_LOCKOUT) {
                                appendFingerprintError(ERROR_LOCKOUT);

                                // first time locked out, subsequent auth attempts will fail immediately for a bit
                                if (fingerprintResult.failedAttempts >= MAX_ATTEMPTS) {
                                    appendFingerprintError(ERROR_TOO_MANY_FAILED_ATTEMPTS);
                                }
                            }
                            setAuthResult(AUTH_RESULT_FAILURE);
                            if (timedOut) timedOut = false;
                            else postFingerprintResult(context, intent, fingerprintResult);
                            Logger.logError(LOG_TAG, errString.toString());
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

            boolean[] auths = intent.getBooleanArrayExtra("auths");
            BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder();
            builder.setTitle(intent.hasExtra("title") ?
                             intent.getStringExtra("title") : "Authenticate");
            if (intent.hasExtra("description")) {
                builder.setDescription(intent.getStringExtra("description"));
            }
            if (intent.hasExtra("subtitle")) {
                builder.setSubtitle(intent.getStringExtra("subtitle"));
            }

            if (auths == null || !auths[0] || Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                builder.setNegativeButtonText(intent.hasExtra("cancel") ?
                        intent.getStringExtra("cancel") : "Cancel");
                builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG);
            } else if (!auths[1]) {
                builder.setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL); //Can't test yet
            } else {
                builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG |
                                                 BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            }

            // listen to fingerprint sensor
            biometricPrompt.authenticate(builder.build());

            if (SENSOR_TIMEOUT != -1) {
                addSensorTimeout(context, intent, biometricPrompt);
            }
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
                    if (!intent.getBooleanExtra(EXTRA_LOCK_ACTION, false)) {
                        postFingerprintResult(context, intent, fingerprintResult);
                    }
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
