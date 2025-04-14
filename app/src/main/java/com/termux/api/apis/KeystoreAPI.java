package com.termux.api.apis;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.security.keystore.UserNotAuthenticatedException;
import android.util.Base64;
import android.util.JsonWriter;

import androidx.annotation.RequiresApi;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.api.util.ResultReturner.ResultJsonWriter;
import com.termux.api.util.ResultReturner.WithInput;
import com.termux.shared.logger.Logger;
import com.termux.shared.settings.preferences.SharedPreferenceUtils;
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStore.Entry;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStore.SecretKeyEntry;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;

import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.stream.Collectors;

public class KeystoreAPI {

    private static final String LOG_TAG = "KeystoreAPI";

    // this is the only provider name that is supported by Android
    private static final String PROVIDER = "AndroidKeyStore";
    private static final String PREFERENCES_PREFIX = "keystore_api__encrypted_data";
    private static final int MAX_AUTH_RETRIES = 1;

    @SuppressLint("NewApi")
    public static void onReceive(TermuxApiReceiver apiReceiver, Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        switch (intent.getStringExtra("command")) {
            case "list":
                listData(apiReceiver, context, intent);
                break;
            case "generate":
                generateKey(apiReceiver, intent);
                break;
            case "delete":
                deleteData(apiReceiver, context, intent);
                break;
            case "sign":
                signData(apiReceiver, intent);
                break;
            case "verify":
                verifyData(apiReceiver, intent);
                break;
            case "encrypt":
                encryptData(apiReceiver, context, intent);
                break;
            case "decrypt":
                decryptData(apiReceiver, context, intent);
                break;
        }
    }

    /**
     * List either the keys inside the keystore or data in shared preferences.<br>
     * Optional intent extras:
     * <ul>
     *     <li>
     *         detailed: if set, shows key parameters (modulus etc.)
     *         or values of shared preferences.
     *     </li>
     *     <li>pref: if set, shows shared preferences instead of key parameters.</li>
     * </ul>
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static void listData(TermuxApiReceiver apiReceiver, final Context context,
                                 final Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, new ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out)
                    throws GeneralSecurityException, IOException, JSONException {
                KeyStore keyStore = getKeyStore();
                Enumeration<String> aliases = keyStore.aliases();
                boolean detailed = (intent.getIntExtra("detailed", 0) == 1);
                boolean pref = (intent.getIntExtra("pref", 0) == 1);

                out.beginArray();
                while (aliases.hasMoreElements()) {
                    out.beginObject();

                    String alias = aliases.nextElement();
                    out.name("alias").value(alias);

                    if (pref) {
                        JSONObject prefsJSON = getPrefsJSON(context, alias);
                        Iterator<String> prefKeys = prefsJSON.keys();
                        out.name("Preferences");
                        out.beginObject();
                        while (prefKeys.hasNext()) {
                            String transientKey = prefKeys.next();
                            out.name(transientKey)
                               .value(detailed ? prefsJSON.getString(transientKey) : "");
                        }
                        out.endObject();
                    } else {
                        Key key = getKey(alias, false);
                        printKey(out, detailed, key,
                                 key instanceof PrivateKey ? getKey(alias, true) : null);
                    }

                    out.endObject();
                }
                out.endArray();
            }
        });
    }

    /**
     * Helper function for printing the parameters of a given key.
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static void printKey(JsonWriter out, boolean detailed, Key key, Key pubKey)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String algorithm = key.getAlgorithm();
        KeyInfo keyInfo = getKeyInfo(key);

        String mode = String.join(",", keyInfo.getBlockModes());
        String padding = String.join(",", keyInfo.getEncryptionPaddings());
        boolean authRequired = keyInfo.isUserAuthenticationRequired();
        int validityDuration = keyInfo.getUserAuthenticationValidityDurationSeconds();

        out.name("algorithm");
        out.beginObject();
        out.name("name").value(algorithm);
        if (!mode.isEmpty()) {
            out.name("block_mode").value(mode);
            out.name("encryption_padding").value(padding);
        }
        out.endObject();
        out.name("size").value(keyInfo.getKeySize());
        out.name("purposes").value(decomposeBinary(keyInfo.getPurposes())
                                                 .stream().map(String::valueOf)
                                                 .collect(Collectors.joining("|")));

        out.name("inside_secure_hardware").value(keyInfo.isInsideSecureHardware());

        out.name("user_authentication");
        out.beginObject();
        out.name("required").value(authRequired);
        out.name("enforced_by_secure_hardware")
                .value(keyInfo.isUserAuthenticationRequirementEnforcedBySecureHardware());
        if (validityDuration >= 0) out.name("validity_duration_seconds")
                .value(validityDuration);
        if (detailed && authRequired) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { //Can't test yet
                out.name("authentication_type")
                   .value(decomposeBinary(keyInfo.getUserAuthenticationType())
                                        .stream().map(String::valueOf)
                                        .collect(Collectors.joining("|")));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                out.name("invalidated_by_new_biometric")
                        .value(keyInfo.isInvalidatedByBiometricEnrollment());
            }
        }
        out.endObject();

        if (detailed && pubKey instanceof RSAPublicKey) {
            RSAPublicKey rsa = (RSAPublicKey) pubKey;
            // convert to hex
            out.name("modulus").value(rsa.getModulus().toString(16));
            out.name("exponent").value(rsa.getPublicExponent().toString(16));
        }
        if (detailed && pubKey instanceof ECPublicKey) {
            ECPublicKey ec = (ECPublicKey) pubKey;
            // convert to hex
            out.name("x").value(ec.getW().getAffineX().toString(16));
            out.name("y").value(ec.getW().getAffineY().toString(16));
        }
    }

    /**
     * Decomposes binary for options (e.g. 3->{1,2}).
     */
    private static ArrayList<Integer> decomposeBinary(int binary) {
        ArrayList<Integer> values = new ArrayList<>();
        int power = 0;
        while (binary != 0) {
            if ((binary & 1) != 0) values.add(1<<power);
            power += 1;
            binary >>= 1;
        }
        return values;
    }

    /**
     * Permanently delete a key from the keystore or a specified shared preference.<br>
     * Required intent extras:
     * <ul>
     *     <li>alias: key alias.</li>
     *     <li>pref: deletes specified preference of alias instead of key.</li>
     * </ul>
     */
    private static void deleteData(TermuxApiReceiver apiReceiver, final Context context,
                                   final Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, out -> {
            String alias = intent.getStringExtra("alias");
            String pref = intent.getStringExtra("pref");

            if (!"-1".equals(pref)) {
                JSONObject prefsJSON = getPrefsJSON(context, alias);
                prefsJSON.remove(pref);
                setPrefsJSON(context, alias, prefsJSON);
            } else {
                // unfortunately this statement does not return anything
                // nor does it throw an exception if the alias does not exist
                getKeyStore().deleteEntry(alias);
                TermuxAPIAppSharedPreferences.build(context)
                                             .getSharedPreferences()
                                             .edit()
                                             .remove(String.join("__", PREFERENCES_PREFIX, alias))
                                             .commit();
            }
        });
    }

    /**
     * Create a new key inside the keystore.<br>
     * Required intent extras:
     * <ul>
     *     <li>alias: key alias.</li>
     *     <li>
     *         algorithm: key algorithm, should be one of the KeyProperties.KEY_ALGORITHM_*
     *         values, for example {@link KeyProperties#KEY_ALGORITHM_RSA} or
     *         {@link KeyProperties#KEY_ALGORITHM_AES}.
     *     </li>
     *     <li>
     *         mode: encryption block mode, should be one of the KeyProperties.BLOCK_MODE_*
     *         values, for example {@link KeyProperties#BLOCK_MODE_GCM} or
     *         {@link KeyProperties#BLOCK_MODE_CBC}.
     *     </li>
     *     <li>
     *         padding: encryption padding, should be one of the KeyProperties.ENCRYPTION_PADDING_*
     *         values, for example {@link KeyProperties#ENCRYPTION_PADDING_NONE} or
     *         {@link KeyProperties#ENCRYPTION_PADDING_PKCS7}. (the full list of supported Cipher
     *         combinations can be found at
     *         <a href="https://developer.android.com/training/articles/keystore#SupportedCiphers">
     *         the Android documentation</a>).
     *     </li>
     *     <li>size: key size.</li>
     *     <li>
     *         purposes: purposes of this key, should be a combination of
     *         KeyProperties.PURPOSE_*, for example 12 for
     *         {@link KeyProperties#PURPOSE_SIGN}+{@link KeyProperties#PURPOSE_VERIFY}
     *         or 3 for
     *         {@link KeyProperties#PURPOSE_ENCRYPT}+{@link KeyProperties#PURPOSE_DECRYPT}.
     *     </li>
     *     <li>
     *         digests: set of hashes this key can be used with, should be an array of
     *         KeyProperties.DIGEST_* values, for example
     *         {@link KeyProperties#DIGEST_SHA256} and {@link KeyProperties#DIGEST_SHA512}.
     *     </li>
     *     <li>
     *         unlocked: set whether key is only valid if device is unlocked.
     *     </li>
     *     <li>
     *         validity: number of seconds where it is allowed to use this key for signing
     *         after unlocking the device (re-locking and unlocking restarts the timer), if set to
     *         -1 then key requires authentication for every use.
     *     </li>
     *     <li>
     *         invalidate: set whether new biometric enrollments invalidate the key.
     *     </li>
     *     <li>
     *         auth: key authorizations which can enable key access, for example
     *         {@link KeyProperties#AUTH_DEVICE_CREDENTIAL} and
     *         {@link KeyProperties#AUTH_BIOMETRIC_STRONG}.
     *     </li>
     * </ul>
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    @SuppressLint("WrongConstant")
    private static void generateKey(TermuxApiReceiver apiReceiver, final Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, out -> {
            String alias = intent.getStringExtra("alias");
            String algorithm = intent.getStringExtra("algorithm");
            String mode = intent.getStringExtra("mode");
            String padding = intent.getStringExtra("padding");
            int size = intent.getIntExtra("size", 2048);
            int purposes = intent.getIntExtra("purposes", 0);
            String[] digests = intent.getStringArrayExtra("digests");
            boolean unlocked = (intent.getIntExtra("unlocked", 1) == 1);
            int userValidity = intent.getIntExtra("validity", -1);
            boolean invalidate = (intent.getIntExtra("invalidate", 0) == 1);
            int authorizations = intent.getIntExtra("auth", 0);

            KeyGenParameterSpec.Builder builder =
                    new KeyGenParameterSpec.Builder(alias, purposes)
                                           .setKeySize(size)
                                           .setUserAuthenticationRequired((authorizations != 0))
                                           .setUserAuthenticationValidityDurationSeconds(userValidity);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setInvalidatedByBiometricEnrollment(invalidate);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    builder.setUnlockedDeviceRequired(unlocked);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        builder.setUserAuthenticationParameters(userValidity, authorizations); //Can't test yet
                    }
                }
            }
            if (!"-1".equals(mode)) builder.setBlockModes(mode).setEncryptionPaddings(padding);

            if (KeyProperties.BLOCK_MODE_ECB.equals(mode) &&
                    (KeyProperties.ENCRYPTION_PADDING_NONE.equals(padding) ||
                            KeyProperties.ENCRYPTION_PADDING_PKCS7.equals(padding))) {
                builder.setRandomizedEncryptionRequired(false);
            }

            if (KeyProperties.KEY_ALGORITHM_AES.equals(algorithm)) {
                KeyGenerator generator = KeyGenerator.getInstance(algorithm, PROVIDER);
                generator.init(builder.build());
                generator.generateKey();
            } else {
                builder.setDigests(digests);
                if (KeyProperties.KEY_ALGORITHM_RSA.equals(algorithm)) {
                    builder.setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1);
                }
                KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm, PROVIDER);
                generator.initialize(builder.build());
                generator.generateKeyPair();
            }
        });
    }

    /**
     * Sign a given byte stream. The file is read from stdin and the signature is output to stdout.
     * The output is encoded using base64.<br>
     * Required intent extras:
     * <ul>
     *     <li>alias: key alias.</li>
     *     <li>
     *         algorithm: key algorithm and hash combination to use, e.g. SHA512withRSA
     *         (the full list can be found at
     *         <a href="https://developer.android.com/training/articles/keystore#SupportedSignatures">
     *         the Android documentation</a>).
     *     </li>
     * </ul>
     */
    private static void signData(TermuxApiReceiver apiReceiver, final Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, new WithInput() {
            @Override
            public void writeResult(PrintWriter out) throws Exception {
                String alias = intent.getStringExtra("alias");
                String algorithm = intent.getStringExtra("algorithm");
                byte[] input = readStream(in);

                PrivateKeyEntry key = (PrivateKeyEntry) getKeyStore().getEntry(alias, null);
                Signature signature = Signature.getInstance(algorithm);
                signature.initSign(key.getPrivateKey());
                signature.update(input);
                byte[] outputData = signature.sign();

                // we are not allowed to output bytes in this function
                // one option is to encode using base64 which is a plain string
                out.write(Base64.encodeToString(outputData, Base64.NO_WRAP));
            }
        });
    }

    /**
     * Verify a given byte stream along with a signature file.
     * The file is read from stdin, and a "true" or "false" message is printed to the stdout.<br>
     * Required intent extras:
     * <ul>
     *     <li>alias: key alias.</li>
     *     <li>
     *         algorithm: key algorithm and hash combination that was used to create this signature,
     *         e.g. SHA512withRSA (the full list can be found at
     *         <a href="https://developer.android.com/training/articles/keystore#SupportedSignatures">
     *         the Android documentation</a>).
     *     </li>
     *     <li>signature: path of the signature file.</li>
     * </ul>
     */
    private static void verifyData(TermuxApiReceiver apiReceiver, final Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, new WithInput() {
            @Override
            public void writeResult(PrintWriter out) throws GeneralSecurityException, IOException {
                String alias = intent.getStringExtra("alias");
                String algorithm = intent.getStringExtra("algorithm");
                byte[] input = readStream(in);
                File signatureFile = new File(intent.getStringExtra("signature"));

                byte[] signatureData = new byte[(int) signatureFile.length()];
                int read = new FileInputStream(signatureFile).read(signatureData);
                if (signatureFile.length() != read) out.println(false);

                Signature signature = Signature.getInstance(algorithm);
                signature.initVerify(getKeyStore().getCertificate(alias).getPublicKey());
                signature.update(input);
                boolean verified = signature.verify(signatureData);

                out.println(verified);
            }
        });
    }

    /**
     * Encrypt a given byte stream.
     * The data is read from a file or stdin (in that precedence), and the encrypted data is
     * encoded using base64 then output to stdout and/or shared preferences with a given name.
     * Output is of the form [IV.length][IV][Encrypted Data] (IV omitted if IV.length is 0).<br>
     * Required intent extras:
     * <ul>
     *     <li>alias: key alias.</li>
     *     <li>
     *         algorithm: key algorithm, should be of the form 'ALG/MODE/PADDING'
     *         (the full list of supported Cipher combinations can be found at
     *         <a href="https://developer.android.com/training/articles/keystore#SupportedCiphers">
     *         the Android documentation</a>).
     *     </li>
     *     <li>path: the input file containing data to be encrypted.</li>
     *     <li>store: the name of the shared preference to store the encrypted data.</li>
     *     <li>quiet: if set, will not output to stdout.</li>
     * </ul>
     */
    private static void encryptData(TermuxApiReceiver apiReceiver, final Context context,
                                    final Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, new WithInput() {
            @Override
            public void writeResult(PrintWriter out)
                    throws GeneralSecurityException, IOException, JSONException {
                String alias = intent.getStringExtra("alias");
                String algorithm = intent.getStringExtra("algorithm");
                String path = intent.getStringExtra("filepath");
                String store = intent.getStringExtra("store");
                boolean quiet = (intent.getIntExtra("quiet", 0) == 1);

                ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
                byte[] input = "-1".equals(path) ? readStream(in) : readFile(path);

                Cipher cipher = cipherCall(context, intent, Cipher.ENCRYPT_MODE,
                                           alias, algorithm, null);

                byte[] encryptedData = cipher.doFinal(input); Arrays.fill(input, (byte) 0);
                byte[] iv = cipher.getIV();
                if (iv == null) {
                    encrypted.write((byte) 0);
                } else {
                    encrypted.write(iv.length);
                    encrypted.write(iv);
                    Arrays.fill(iv, (byte) 0);
                }
                encrypted.write(encryptedData); Arrays.fill(encryptedData, (byte) 0);

                // we are not allowed to output bytes in this function
                // one option is to encode using base64 which is a plain string
                if (!quiet) out.write(Base64.encodeToString(
                        encrypted.toByteArray(), Base64.NO_WRAP));
                if (!"-1".equals(store)) {
                    JSONObject prefsJSON = getPrefsJSON(context, alias);
                    prefsJSON.put(store, Base64.encodeToString(
                                                    encrypted.toByteArray(), Base64.NO_WRAP));
                    setPrefsJSON(context, alias, prefsJSON);
                }
                encrypted.reset();
            }
        });
    }

    /**
     * Decrypt a given byte stream.
     * The data is read from a file, shared preferences, or stdin (in that precedence), and the
     * decrypted data can be output to stdout. Input is expected in the form
     * [IV.length][IV][Encrypted Data] (IV omitted if IV.length is 0).<br>
     * Required intent extras:
     * <ul>
     *     <li>alias: key alias.</li>
     *     <li>
     *         algorithm: key algorithm, should be of the form 'ALG/MODE/PADDING'
     *         (the full list of supported Cipher combinations can be found at
     *         <a href="https://developer.android.com/training/articles/keystore#SupportedCiphers">
     *         the Android documentation</a>).
     *     </li>
     *     <li>path: the input file containing data to be decrypted.</li>
     *     <li>store: the name of the shared preference containing data to be decrypted.</li>
     *     <li>quiet: if set, will not output to stdout.</li>
     * </ul>
     */
    private static void decryptData(TermuxApiReceiver apiReceiver, final Context context,
                                    final Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, new WithInput() {
            @Override
            public void writeResult(PrintWriter out)
                    throws GeneralSecurityException, IOException, JSONException {
                String alias = intent.getStringExtra("alias");
                String algorithm = intent.getStringExtra("algorithm");
                String path = intent.getStringExtra("filepath");
                String store = intent.getStringExtra("store");
                boolean quiet = (intent.getIntExtra("quiet", 0) == 1);

                ByteArrayOutputStream decrypted = new ByteArrayOutputStream();
                byte[] input;
                if ("-1".equals(path)) {
                    if (!"-1".equals(store)) {
                        JSONObject prefsJSON = getPrefsJSON(context, alias);
                        input = Base64.decode(prefsJSON.getString(store), Base64.NO_WRAP);
                    } else {
                        input = readStream(in);
                    }
                } else {
                    input = readFile(path);
                }

                Cipher cipher = cipherCall(context, intent, Cipher.DECRYPT_MODE, alias, algorithm,
                                           input[0] == 0 ? null : input);

                byte[] decryptedData = cipher.doFinal(input,
                        input[0]+1, input.length-input[0]-1);
                Arrays.fill(input, (byte) 0);
                decrypted.write(decryptedData); Arrays.fill(decryptedData, (byte) 0);

                // we are not allowed to output bytes in this function
                // one option is to encode using base64 which is a plain string
                if (!quiet) out.write(Base64.encodeToString(
                                                decrypted.toByteArray(), Base64.NO_WRAP));
                decrypted.reset();
            }
        });
    }

    /**
     * Tries to initialize cipher and prompts for authentication if timed-out.
     */
    private static Cipher cipherCall(Context context, Intent intent, int mode, String alias,
                                     String algorithm, byte[] input)
            throws GeneralSecurityException, IOException {
        int count = 0;
        boolean[] auths = {true, true};
        Key key = getKey(alias, (mode == Cipher.ENCRYPT_MODE));
        KeyInfo keyInfo = getKeyInfo(key);
        if ("-1".equals(algorithm)) {
            algorithm = String.join("/",
                                    key.getAlgorithm(),
                                    keyInfo.getBlockModes()[0],
                                    keyInfo.getEncryptionPaddings()[0]);
            Logger.logDebug(LOG_TAG, "Cipher algorithm not specified, using: " + algorithm);
        }
        Cipher cipher = Cipher.getInstance(algorithm);

        do {
            try {
                if (input == null) cipher.init(mode, key);
                else {
                    cipher.init(mode, key, getIVSpec(input, algorithm.split("/", 3)[1]));
                }
                return cipher;
            } catch (UserNotAuthenticatedException e) {
                if (count == 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                       ArrayList<Integer> authList = decomposeBinary(keyInfo
                                                                     .getUserAuthenticationType());
                       auths[0] = authList.contains(KeyProperties.AUTH_DEVICE_CREDENTIAL);
                       auths[1] = authList.contains(KeyProperties.AUTH_BIOMETRIC_STRONG);
                    }
                    intent.putExtra("auths", auths);
                    intent.putExtra("title", " ");
                    intent.putExtra("subtitle", "Authentication required for key");
                    intent.putExtra("description", "");
                    intent.putExtra(FingerprintAPI.EXTRA_LOCK_ACTION, true);
                }
                if (count <= MAX_AUTH_RETRIES) {
                    FingerprintAPI.onReceive(context, intent);
                } else {
                    Logger.logError(LOG_TAG, String.valueOf(e));
                    throw e;
                }
            }
        } while (count++ <= MAX_AUTH_RETRIES);

        return null;
    }

    /**
     * Get IV Parameter Spec.
     */
    private static AlgorithmParameterSpec getIVSpec(byte[] input, String mode) {
        switch(mode) {
            case "CBC":
            case "CTR": {
                return new IvParameterSpec(input, 1, input[0]);
            }
            case "GCM": {
                return new GCMParameterSpec(128, input, 1, input[0]);
            }
            default: {
                String e = "Invalid Cipher Block. See: Android keystore#SupportedCiphers";
                Logger.logError(LOG_TAG, e);
                throw new IllegalArgumentException(e);
            }
        }
    }

    /**
     * Get Key.
     */
    private static Key getKey(String alias, boolean encrypt)
            throws GeneralSecurityException, IOException {
        Entry entry = getKeyStore().getEntry(alias, null);
        if (entry instanceof PrivateKeyEntry) {
            return encrypt ? ((PrivateKeyEntry) entry).getCertificate().getPublicKey()
                    : ((PrivateKeyEntry) entry).getPrivateKey();
        } else if (entry instanceof SecretKeyEntry) {
            return ((SecretKeyEntry) entry).getSecretKey();
        } else {
            String e = "Invalid Cipher Algorithm. See: Android keystore#SupportedCiphers";
            Logger.logError(LOG_TAG, e);
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Get KeyInfo
     */
    private static KeyInfo getKeyInfo(Key key)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        String algorithm = key.getAlgorithm();
        return (key instanceof PrivateKey) ?
                KeyFactory.getInstance(algorithm).getKeySpec(key, KeyInfo.class)
                : (KeyInfo) SecretKeyFactory.getInstance(algorithm)
                .getKeySpec((SecretKey) key, KeyInfo.class);
    }

    /**
     * Set up and return the keystore.
     */
    private static KeyStore getKeyStore() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(PROVIDER);
        keyStore.load(null);
        return keyStore;
    }

    /**
     * Set Shared Preferences in JSON for given key alias.
     */
    private static void setPrefsJSON(Context context, String alias, JSONObject value) {
        SharedPreferences preferences = TermuxAPIAppSharedPreferences.build(context)
                                                                     .getSharedPreferences();
        SharedPreferenceUtils.setString(preferences,
                String.join("__", PREFERENCES_PREFIX, alias),
                value.toString(), true);
    }

    /**
     * Get Shared Preferences in JSON for given key alias.
     */
    private static JSONObject getPrefsJSON(Context context, String alias) throws JSONException {
        SharedPreferences preferences = TermuxAPIAppSharedPreferences.build(context)
                                                                     .getSharedPreferences();
        return new JSONObject(SharedPreferenceUtils.getString(preferences,
                String.join("__", PREFERENCES_PREFIX, alias),
                "{}", true));
    }

    /**
     * Read file to byte array.
     */
    private static byte[] readFile(String path) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Files.readAllBytes(Paths.get(path));
        } else {
            File file = new File(path);
            byte[] data = new byte[(int) file.length()];
            BufferedInputStream buffer = new BufferedInputStream(new FileInputStream(file));
            buffer.read(data, 0, data.length);
            buffer.close();
            return data;
        }
    }

    /**
     * Read a given stream to a byte array. Should not be used with large streams.
     */
    private static byte[] readStream(InputStream stream) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = stream.read(buffer)) > 0) {
            byteStream.write(buffer, 0, read);
        }
        return byteStream.toByteArray();
    }

    private static void printErrorMessage(TermuxApiReceiver apiReceiver, Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, out -> out.println("termux-keystore requires at least Android 6.0 (Marshmallow)."));
    }
}