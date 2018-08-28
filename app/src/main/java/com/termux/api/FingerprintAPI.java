package com.termux.api;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.JsonWriter;
import android.widget.Toast;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.TermuxApiLogger;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

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
            FingerprintManager fingerprintManager = (FingerprintManager)context.getSystemService(Context.FINGERPRINT_SERVICE);

            // make sure we have a valid fingerprint sensor before attempting to launch Fingerprint activity
            if (validateFingerprintSensor(context, fingerprintManager)) {
                Intent fingerprintIntent = new Intent(context, FingerprintActivity.class);
                fingerprintIntent.putExtras(intent.getExtras());
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
    protected static boolean validateFingerprintSensor(Context context, FingerprintManager fingerprintManager) {
        boolean result = true;

        if (!fingerprintManager.isHardwareDetected()) {
            Toast.makeText(context, "No fingerprint scanner found!", Toast.LENGTH_SHORT).show();
            appendFingerprintError(ERROR_NO_HARDWARE);
            result = false;
        }

        if (!fingerprintManager.hasEnrolledFingerprints()) {
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
    public static class FingerprintActivity extends Activity {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            handleFingerprint();
            finish();
        }

        /**
         * Handle setup and listening of fingerprint sensor
         */
        protected void handleFingerprint() {
            FingerprintManager fingerprintManager = (FingerprintManager)getSystemService(Context.FINGERPRINT_SERVICE);
            Cipher cipher = null;
            boolean hasError = false;

            try {
                KeyStore keyStore = KeyStore.getInstance(KEYSTORE_NAME);
                generateKey(keyStore);
                cipher = getCipher();
                keyStore.load(null);
                SecretKey key = (SecretKey) keyStore.getKey(KEY_NAME, null);
                cipher.init(Cipher.ENCRYPT_MODE, key);
            } catch (Exception e) {
                TermuxApiLogger.error(TAG, e);
                hasError = true;
            }

            if (cipher != null && !hasError) {
                authenticateWithFingerprint(this, getIntent(), fingerprintManager, cipher);
            }
        }

        /**
         * Handles authentication callback from our fingerprint sensor
         */
        protected static void authenticateWithFingerprint(final Context context, final Intent intent, final FingerprintManager fingerprintManager, Cipher cipher) {
            FingerprintManager.AuthenticationCallback authenticationCallback = new FingerprintManager.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, CharSequence errString) {
                    if (errorCode == FingerprintManager.FINGERPRINT_ERROR_LOCKOUT) {
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
                public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                    setAuthResult(AUTH_RESULT_SUCCESS);
                    postFingerprintResult(context, intent, fingerprintResult);
                }

                @Override
                public void onAuthenticationFailed() {
                    addFailedAttempt();
                }

                // unused
                @Override
                public void onAuthenticationHelp(int helpCode, CharSequence helpString) { }
            };

            Toast.makeText(context, "Scan fingerprint", Toast.LENGTH_LONG).show();

            // listen to fingerprint sensor
            FingerprintManager.CryptoObject cryptoObject = new FingerprintManager.CryptoObject(cipher);
            final CancellationSignal cancellationSignal = new CancellationSignal();
            fingerprintManager.authenticate(cryptoObject, cancellationSignal, 0, authenticationCallback, null);

            addSensorTimeout(context, intent, cancellationSignal);
        }

        /**
         * Adds a timeout for our fingerprint sensor which will force a result return if we
         * haven't already received one
         */
        protected static void addSensorTimeout(final Context context, final Intent intent, final CancellationSignal cancellationSignal) {
            final Handler timeoutHandler = new Handler(Looper.getMainLooper());
            timeoutHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!postedResult) {
                        appendFingerprintError(ERROR_TIMEOUT);
                        cancellationSignal.cancel();
                        postFingerprintResult(context, intent, fingerprintResult);
                    }
                }
            }, SENSOR_TIMEOUT);
        }

        protected static void generateKey(KeyStore keyStore) {
            try {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_NAME);
                keyStore.load(null);
                keyGenerator.init(
                        new KeyGenParameterSpec.Builder(KEY_NAME,
                                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                                .setUserAuthenticationRequired(true)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                                .build());

                keyGenerator.generateKey();
            } catch (Exception e) {
                TermuxApiLogger.error(TAG, e);
                appendFingerprintError(ERROR_KEY_GENERATOR);
            }
        }

        /**
         * Create the cipher needed for use with our SecretKey
         */
        protected static Cipher getCipher() {
            Cipher cipher = null;
            try {
                cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES
                        + "/" + KeyProperties.BLOCK_MODE_CBC
                        + "/" + KeyProperties.ENCRYPTION_PADDING_PKCS7);
            } catch (Exception e) {
                TermuxApiLogger.error(TAG, e);
                appendFingerprintError(ERROR_CIPHER);
            }
            return cipher;
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
