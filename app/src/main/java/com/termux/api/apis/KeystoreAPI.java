package com.termux.api.apis;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import androidx.annotation.RequiresApi;
import android.util.Base64;
import android.util.JsonWriter;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.api.util.ResultReturner.ResultJsonWriter;
import com.termux.api.util.ResultReturner.WithInput;
import com.termux.shared.logger.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStore.Entry;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Enumeration;

public class KeystoreAPI {

    private static final String LOG_TAG = "KeystoreAPI";

    // this is the only provider name that is supported by Android
    private static final String PROVIDER = "AndroidKeyStore";

    @SuppressLint("NewApi")
    public static void onReceive(TermuxApiReceiver apiReceiver, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        switch (intent.getStringExtra("command")) {
            case "list":
                listKeys(apiReceiver, intent);
                break;
            case "generate":
                generateKey(apiReceiver, intent);
                break;
            case "delete":
                deleteKey(apiReceiver, intent);
                break;
            case "sign":
                signData(apiReceiver, intent);
                break;
            case "verify":
                verifyData(apiReceiver, intent);
                break;
        }
    }

    /**
     * List the keys inside the keystore.<br>
     * Optional intent extras:
     * <ul>
     *     <li>detailed: if set, key parameters (modulus etc.) are included in the response</li>
     * </ul>
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static void listKeys(TermuxApiReceiver apiReceiver, final Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, new ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws GeneralSecurityException, IOException {
                KeyStore keyStore = getKeyStore();
                Enumeration<String> aliases = keyStore.aliases();
                boolean detailed = intent.getBooleanExtra("detailed", false);

                out.beginArray();
                while (aliases.hasMoreElements()) {
                    out.beginObject();

                    String alias = aliases.nextElement();
                    out.name("alias").value(alias);

                    Entry entry = keyStore.getEntry(alias, null);
                    if (entry instanceof PrivateKeyEntry) {
                        printPrivateKey(out, (PrivateKeyEntry) entry, detailed);
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
    private static void printPrivateKey(JsonWriter out, PrivateKeyEntry entry, boolean detailed)
            throws GeneralSecurityException, IOException {
        PrivateKey privateKey = entry.getPrivateKey();
        String algorithm = privateKey.getAlgorithm();
        KeyInfo keyInfo = KeyFactory.getInstance(algorithm).getKeySpec(privateKey, KeyInfo.class);

        PublicKey publicKey = entry.getCertificate().getPublicKey();

        out.name("algorithm").value(algorithm);
        out.name("size").value(keyInfo.getKeySize());

        if (detailed && publicKey instanceof RSAPublicKey) {
            RSAPublicKey rsa = (RSAPublicKey) publicKey;
            // convert to hex
            out.name("modulus").value(rsa.getModulus().toString(16));
            out.name("exponent").value(rsa.getPublicExponent().toString(16));
        }
        if (detailed && publicKey instanceof ECPublicKey) {
            ECPublicKey ec = (ECPublicKey) publicKey;
            // convert to hex
            out.name("x").value(ec.getW().getAffineX().toString(16));
            out.name("y").value(ec.getW().getAffineY().toString(16));
        }

        out.name("inside_secure_hardware").value(keyInfo.isInsideSecureHardware());

        out.name("user_authentication");

        out.beginObject();
        out.name("required").value(keyInfo.isUserAuthenticationRequired());

        out.name("enforced_by_secure_hardware");
        out.value(keyInfo.isUserAuthenticationRequirementEnforcedBySecureHardware());

        int validityDuration = keyInfo.getUserAuthenticationValidityDurationSeconds();
        if (validityDuration >= 0) {
            out.name("validity_duration_seconds").value(validityDuration);
        }
        out.endObject();
    }

    /**
     * Permanently delete a key from the keystore.<br>
     * Required intent extras:
     * <ul>
     *     <li>alias: key alias</li>
     * </ul>
     */
    private static void deleteKey(TermuxApiReceiver apiReceiver, final Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, out -> {
            String alias = intent.getStringExtra("alias");
            // unfortunately this statement does not return anything
            // nor does it throw an exception if the alias does not exist
            getKeyStore().deleteEntry(alias);
        });
    }

    /**
     * Create a new key inside the keystore.<br>
     * Required intent extras:
     * <ul>
     *     <li>alias: key alias</li>
     *     <li>
     *         algorithm: key algorithm, should be one of the KeyProperties.KEY_ALGORITHM_*
     *         values, for example {@link KeyProperties#KEY_ALGORITHM_RSA} or
     *         {@link KeyProperties#KEY_ALGORITHM_EC}.
     *     </li>
     *     <li>
     *         purposes: purposes of this key, should be a combination of
     *         KeyProperties.PURPOSE_*, for example 12 for
     *         {@link KeyProperties#PURPOSE_SIGN}+{@link KeyProperties#PURPOSE_VERIFY}
     *     </li>
     *     <li>
     *         digests: set of hashes this key can be used with, should be an array of
     *         KeyProperties.DIGEST_* values, for example
     *         {@link KeyProperties#DIGEST_SHA256} and {@link KeyProperties#DIGEST_SHA512}
     *     </li>
     *     <li>size: key size, only used for RSA keys</li>
     *     <li>curve: elliptic curve name, only used for EC keys</li>
     *     <li>
     *         userValidity: number of seconds where it is allowed to use this key for signing
     *         after unlocking the device (re-locking and unlocking restarts the timer), if set to 0
     *         this feature is disabled (i.e. the key can be used anytime)
     *     </li>
     * </ul>
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    @SuppressLint("WrongConstant")
    private static void generateKey(TermuxApiReceiver apiReceiver, final Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, out -> {
            String alias = intent.getStringExtra("alias");
            String algorithm = intent.getStringExtra("algorithm");
            int purposes = intent.getIntExtra("purposes", 0);
            String[] digests = intent.getStringArrayExtra("digests");
            int size = intent.getIntExtra("size", 2048);
            String curve = intent.getStringExtra("curve");
            int userValidity = intent.getIntExtra("validity", 0);

            KeyGenParameterSpec.Builder builder =
                    new KeyGenParameterSpec.Builder(alias, purposes);

            builder.setDigests(digests);
            if (algorithm.equals(KeyProperties.KEY_ALGORITHM_RSA)) {
                // only the exponent 65537 is supported for now
                builder.setAlgorithmParameterSpec(
                        new RSAKeyGenParameterSpec(size, RSAKeyGenParameterSpec.F4));
                builder.setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1);
            }

            if (algorithm.equals(KeyProperties.KEY_ALGORITHM_EC)) {
                builder.setAlgorithmParameterSpec(new ECGenParameterSpec(curve));
            }

            if (userValidity > 0) {
                builder.setUserAuthenticationRequired(true);
                builder.setUserAuthenticationValidityDurationSeconds(userValidity);
            }

            KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm, PROVIDER);
            generator.initialize(builder.build());
            generator.generateKeyPair();
        });
    }

    /**
     * Sign a given byte stream. The file is read from stdin and the signature is output to stdout.
     * The output is encoded using base64.<br>
     * Required intent extras:
     * <ul>
     *     <li>alias: key alias</li>
     *     <li>
     *         algorithm: key algorithm and hash combination to use, e.g. SHA512withRSA
     *         (the full list can be found at
     *         <a href="https://developer.android.com/training/articles/keystore#SupportedSignatures">
     *         the Android documentation</a>)
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
     *     <li>alias: key alias</li>
     *     <li>
     *         algorithm: key algorithm and hash combination that was used to create this signature,
     *         e.g. SHA512withRSA (the full list can be found at
     *         <a href="https://developer.android.com/training/articles/keystore#SupportedSignatures">
     *         the Android documentation</a>)
     *     </li>
     *     <li>signature: path of the signature file</li>
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
     * Set up and return the keystore.
     */
    private static KeyStore getKeyStore() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(PROVIDER);
        keyStore.load(null);
        return keyStore;
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
