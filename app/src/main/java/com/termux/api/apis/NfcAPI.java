package com.termux.api.apis;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcBarcode;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.nfc.tech.TagTechnology;
import android.os.Bundle;
import android.util.JsonWriter;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Function;

import com.termux.api.util.PendingIntentUtils;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class NfcException extends RuntimeException {
    NfcException() {

    }

    NfcException(String message) {
        super(message);
    }
}

class UnsupportedTechnology extends NfcException {}
class NoConnectionException extends NfcException {}
class WrongTechnologyException extends NfcException {
    WrongTechnologyException(@NonNull String expected, @NonNull String found) {
        super(String.format("%s expected, but %s found", expected, found));
    }
}
class NfcUnavailableException extends NfcException {}
class ActivityFinishingException extends NfcException {}
class TagNullException extends NfcException {}

class ArgumentException extends NfcException {
    ArgumentException() {
        super();
    }

    ArgumentException(String message) {
        super(message);
    }
}
class ArgNumberException extends ArgumentException {
    ArgNumberException(int expected, int found) {
        super(String.format("%d expected, found %d", expected, found));
    }
}
class InvalidHexException extends ArgumentException {
    InvalidHexException(char c) {
        super(String.format("%c", c));
    }
}
class InvalidHexLengthException extends ArgumentException {}
class InvalidCommandException extends ArgumentException {}

class Utils {
    static void checkTechnologyNonNull(@Nullable TagTechnology technology) throws NoConnectionException {
        if (technology == null) {
            throw new NoConnectionException();
        }
    }

    static void checkTagNonNull(@Nullable Tag tag) throws TagNullException {
        if (tag == null) {
            throw new TagNullException();
        }
    }

    static <T extends TagTechnology> @NonNull T liftTagTechnology(@Nullable TagTechnology technology, @NonNull Class<T> tClass)
            throws NoConnectionException, WrongTechnologyException
    {
        checkTechnologyNonNull(technology);

        try {
            T techLifted = tClass.cast(technology);
            assert techLifted != null;
            return techLifted;
        }
        catch (ClassCastException e) {
            throw new WrongTechnologyException(tClass.getSimpleName(), technology.getClass().getSimpleName());
        }
    }

    static void checkIntEqual(int expectedLength, int argLength) throws ArgNumberException {
        if (expectedLength != argLength) {
            throw new ArgNumberException(expectedLength, argLength);
        }
    }

    static byte parseHexOne(char c) throws ArgumentException {
        if ('0' <= c && c <= '9')
            return (byte)((byte)c - '0');
        if ('a' <= c && c <= 'f')
            return (byte)((byte)c - 'a' + 10);
        if ('A' <= c && c <= 'F')
            return (byte)((byte)c - 'A' + 10);
        throw new InvalidHexException(c);
    }

    static byte parseHexTwo(char high, char low) throws ArgumentException {
        return (byte)((parseHexOne(high) << 4) | parseHexOne(low));
    }

    /**
     * Parse hex representation of byte[] array.
     * @param hex the hex representation, e.g. "DEADBEEF"
     * @return the byte[] array, e.g. {0xde, 0xad, 0xbe, 0xef}.
     * @throws ArgumentException if it fails to parse.
     */
    static byte[] parseHex(@NonNull String hex) throws ArgumentException {
        if (hex.length()%2 != 0 || hex.isEmpty()) {
            throw new InvalidHexLengthException();
        }

        byte[] result = new byte[hex.length()/2];
        for (int i=0; i<hex.length(); i+=2) {
            result[i/2] = parseHexTwo(hex.charAt(i), hex.charAt(i+1));
        }

        return result;
    }

    static int parseInt(String number) throws ArgumentException {
        try {
            return Integer.parseInt(number);
        }
        catch (NumberFormatException e) {
            throw new ArgumentException(e.getMessage());
        }
    }

    static String formatHexOne(byte b) {
        return String.format("%02X", b);
    }

    /**
     * Format byte array into hex string, e.g. {0xde, 0xad, 0xbe, 0xef} -> "DEADBEEF"
     * @param data the byte array to be formatted
     * @return the formatted hex representation
     */
    static String formatHex(@NonNull byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b: data) {
            sb.append(Utils.formatHexOne(b));
        }
        return sb.toString();
    }


    /**
     * Collect "arg1" "arg2" "arg3" ... from intent extras,
     * where the prefix "arg" can be specified.
     *
     * @param intent the intent
     * @param argPrefix the prefix, usually "arg"
     *
     * @return collected arguments
     */
    static String[] collectNumberedArgs(Intent intent, @NonNull String argPrefix) {
        List<String> result = new ArrayList<>();

        for (int index=1; true; ++index) {
            String argName = argPrefix + index;
            String argValue = intent.getStringExtra(argName);
            if (argValue == null) {
                break;
            }
            result.add(argValue);
        }

        return result.toArray(new String[0]);
    }
}

/**
 * Return value of wrapped NFC API call.
 */
class CallResult {
    @Nullable Object mData;

    CallResult(@Nullable Object data) {
        mData = data;
    }

    static CallResult successWithString(@NonNull String data) {
        return new CallResult(data);
    }

    static CallResult successWithHex(@NonNull byte[] data) {
        return new CallResult(Utils.formatHex(data));
    }

    static CallResult successWithInt(int data) {
        return new CallResult(data);
    }

    static CallResult successWithBool(boolean data) {
        return new CallResult(data);
    }

    static CallResult success() {
        return new CallResult(null);
    }
}

/**
 * Single abstract method for NFC API invocation.
 */
interface TagTechnologyClosure {
    CallResult call() throws IOException, android.nfc.FormatException, NfcException;
}

public class NfcAPI {

    private static final String LOG_TAG = "NfcAPI";

    public static void onReceive(final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        Intent newIntent = new Intent(context, NfcActivity.class);
        Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }
        newIntent.putExtras(extras).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(newIntent);
    }


    public static class NfcActivity extends AppCompatActivity {
        // Closure for the first API call that is delayed after the discovery of tag.
        private TagTechnologyClosure mDelayedClosure;

        // The result of connect()
        private @Nullable TagTechnology mTechnology;

        // The last discovered tag
        private Tag mTag;

        private NfcAdapter mAdapter;

        private static final String LOG_TAG = "NfcActivity";

        // start of wrapper for quit
        private final static String METHOD_NAME_QUIT = "quit";

        private TagTechnologyClosure parseArgsQuit(final @NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(1, args.length);
            return this::callQuit;
        }

        private CallResult callQuit() {
            finish();
            return CallResult.success();
        }
        // end of wrapper for quit

        // start of wrappers for abstract class TagTechnology
        // doc: https://developer.android.com/reference/android/nfc/tech/TagTechnology
        final static String CLASS_NAME_TAG_TECHNOLOGY = "TagTechnology";

        // start of wrapper for TagTechnology::isConnected
        private final static String METHOD_NAME_TAG_TECHNOLOGY_IS_CONNECTED = "isConnected";

        private TagTechnologyClosure parseArgsTagTechnologyIsConnected(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callTagTechnologyIsConnected;
        }

        private CallResult callTagTechnologyIsConnected() {
            Utils.checkTechnologyNonNull(mTechnology);
            boolean response = mTechnology.isConnected();
            return CallResult.successWithBool(response);
        }
        // end of wrapper for TagTechnology::isConnected

        // start of wrapper for TagTechnology::close
        private final static String METHOD_NAME_TAG_TECHNOLOGY_CLOSE = "close";

        private TagTechnologyClosure parseArgsTagTechnologyClose(final @NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callTagTechnologyClose;
        }

        private CallResult callTagTechnologyClose() throws IOException {
            Utils.checkTechnologyNonNull(mTechnology);
            mTechnology.close();
            return CallResult.success();
        }
        // end of wrapper for TagTechnology::close

        // start of wrapper for TagTechnology::getTag
        private final static String METHOD_NAME_TAG_TECHNOLOGY_GET_TAG = "getTag";

        private TagTechnologyClosure parseArgsTagTechnologyGetTag(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callTagTechnologyGetTag;
        }

        private CallResult callTagTechnologyGetTag() {
            Utils.checkTagNonNull(mTag);
            String description = mTag.toString();
            return CallResult.successWithString(description);
        }
        // end of wrappers for abstract class TagTechnology

        // start of wrappers for class NfcA
        // doc: https://developer.android.com/reference/android/nfc/tech/NfcA
        private static final String CLASS_NAME_NFC_A = "NfcA";

        // start of wrapper for NfcA::connect
        private final static String METHOD_NAME_NFC_A_CONNECT = "connect";

        private TagTechnologyClosure parseArgsNfcAConnect(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNfcAConnect;
        }

        private CallResult callNfcAConnect() throws IOException {
            return callTagTechnologyConnect(NfcA::get);
        }
        // end of wrapper for NfcA::connect

        // start of wrapper for NfcA::getAtqa
        private final static String METHOD_NAME_NFC_A_GET_ATQA = "getAtqa";

        private TagTechnologyClosure parseArgsNfcAGetAtqa(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNfcAGetAtqa;
        }

        private CallResult callNfcAGetAtqa() {
            @NonNull NfcA nfcA = Utils.liftTagTechnology(mTechnology, NfcA.class);
            byte[] response = nfcA.getAtqa();
            return CallResult.successWithHex(response);
        }
        // end of wrapper for NfcA::getAtqa

        // start of wrapper for NfcA::getMaxTransceiveLength
        private final static String METHOD_NAME_NFC_A_GET_MAX_TRANSCEIVE_LENGTH = "getMaxTransceiveLength";

        private TagTechnologyClosure parseArgsNfcAGetMaxTransceiveLength(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNfcAGetMaxTransceiveLength;
        }

        private CallResult callNfcAGetMaxTransceiveLength() {
            @NonNull NfcA nfcA = Utils.liftTagTechnology(mTechnology, NfcA.class);
            int response = nfcA.getMaxTransceiveLength();
            return CallResult.successWithInt(response);
        }
        // end of wrapper for NfcA::getMaxTransceiveLength

        // start of wrapper for NfcA::getSak
        private final static String METHOD_NAME_NFC_A_GET_SAK = "getSak";

        private TagTechnologyClosure parseArgsNfcAGetSak(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNfcAGetSak;
        }

        private CallResult callNfcAGetSak() {
            @NonNull NfcA nfcA = Utils.liftTagTechnology(mTechnology, NfcA.class);
            short response = nfcA.getSak();
            return CallResult.successWithInt(response);
        }
        // end of wrapper for NfcA::getSak

        // start of wrapper for NfcA::getTimeout
        private final static String METHOD_NAME_NFC_A_GET_TIMEOUT = "getTimeout";

        private TagTechnologyClosure parseArgsNfcAGetTimeout(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNfcAGetTimeout;
        }

        private CallResult callNfcAGetTimeout() {
            @NonNull NfcA nfcA = Utils.liftTagTechnology(mTechnology, NfcA.class);
            int response = nfcA.getTimeout();
            return CallResult.successWithInt(response);
        }
        // end of wrapper for NfcA::getTimeout

        // start of wrapper for NfcA::setTimeout
        private final static String METHOD_NAME_NFC_A_SET_TIMEOUT = "setTimeout";

        private TagTechnologyClosure parseArgsNfcASetTimeout(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);
            int timeout = Utils.parseInt(args[args.length-1]);
            return () -> callNfcASetTimeout(timeout);
        }

        private CallResult callNfcASetTimeout(int timeout) {
            @NonNull NfcA nfcA = Utils.liftTagTechnology(mTechnology, NfcA.class);
            nfcA.setTimeout(timeout);
            return CallResult.success();
        }
        // end of wrapper for NfcA::setTimeout

        // start of wrapper for NfcA::transceive
        private final static String METHOD_NAME_NFC_A_TRANSCEIVE = "transceive";

        private TagTechnologyClosure parseArgsNfcATransceive(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);
            @NonNull byte[] data = Utils.parseHex(args[args.length-1]);
            return () -> callNfcATransceive(data);
        }

        private CallResult callNfcATransceive(byte[] data) throws IOException {
            @NonNull NfcA nfcA = Utils.liftTagTechnology(mTechnology, NfcA.class);
            byte[] response = nfcA.transceive(data);
            return CallResult.successWithHex(response);
        }
        // end of wrapper for NfcA::transceive
        // end of wrappers for class NfcA

        // start of wrappers for class NfcB
        // doc: https://developer.android.com/reference/android/nfc/tech/NfcB
        private static final String CLASS_NAME_NFC_B = "NfcB";

        // start of wrapper for NfcB::connect
        private final static String METHOD_NAME_NFC_B_CONNECT = "connect";

        private TagTechnologyClosure parseArgsNfcBConnect(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNfcBConnect;
        }

        private CallResult callNfcBConnect() throws IOException {
            return callTagTechnologyConnect(NfcB::get);
        }
        // end of wrapper for NfcB::connect

        // start of wrapper for NfcB::getApplicationData
        private final static String METHOD_NAME_NFC_B_GET_APPLICATION_DATA = "getApplicationData";

        private TagTechnologyClosure parseArgsNfcBGetApplicationData(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNfcBGetApplicationData;
        }

        private CallResult callNfcBGetApplicationData() {
            @NonNull NfcB nfcB = Utils.liftTagTechnology(mTechnology, NfcB.class);
            byte[] response = nfcB.getApplicationData();
            return CallResult.successWithHex(response);
        }
        // end of wrapper for NfcB::getApplicationData

        // start of wrapper for NfcB::getMaxTransceiveLength
        private final static String METHOD_NAME_NFC_B_GET_MAX_TRANSCEIVE_LENGTH = "getMaxTransceiveLength";

        private TagTechnologyClosure parseArgsNfcBGetMaxTransceiveLength(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNfcBGetMaxTransceiveLength;
        }

        private CallResult callNfcBGetMaxTransceiveLength() {
            @NonNull NfcB nfcB = Utils.liftTagTechnology(mTechnology, NfcB.class);
            int response = nfcB.getMaxTransceiveLength();
            return CallResult.successWithInt(response);
        }
        // end of wrapper for NfcB::getMaxTransceiveLength

        // start of wrapper for NfcB::getProtocolInfo
        private final static String METHOD_NAME_NFC_B_GET_PROTOCOL_INFO = "getProtocolInfo";

        private TagTechnologyClosure parseArgsNfcBGetProtocolInfo(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNfcBGetProtocolInfo;
        }

        private CallResult callNfcBGetProtocolInfo() {
            @NonNull NfcB nfcB = Utils.liftTagTechnology(mTechnology, NfcB.class);
            byte[] response = nfcB.getProtocolInfo();
            return CallResult.successWithHex(response);
        }
        // end of wrapper for NfcB::getProtocolInfo

        // start of wrapper for NfcB::transceive
        private final static String METHOD_NAME_NFC_B_TRANSCEIVE = "transceive";

        private TagTechnologyClosure parseArgsNfcBTransceive(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);
            @NonNull byte[] data = Utils.parseHex(args[args.length-1]);
            return () -> callNfcBTransceive(data);
        }

        private CallResult callNfcBTransceive(byte[] data) throws IOException {
            @NonNull NfcB nfcB = Utils.liftTagTechnology(mTechnology, NfcB.class);
            byte[] response = nfcB.transceive(data);
            return CallResult.successWithHex(response);
        }
        // end of wrapper for NfcB::transceive
        // end of wrappers for class NfcB

        // start of wrappers for class NfcF
        // doc: https://developer.android.com/reference/android/nfc/tech/NfcF
        private static final String CLASS_NAME_NFC_F = "NfcF";

        // start of wrapper for NfcF::connect
        private final static String METHOD_NAME_NFC_F_CONNECT = "connect";

        private TagTechnologyClosure parseArgsNfcFConnect(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNfcFConnect;
        }

        private CallResult callNfcFConnect() throws IOException {
            return callTagTechnologyConnect(NfcF::get);
        }
        // end of wrapper for NfcF::connect

        // start of wrapper for NfcF::getManufacturer
        private final static String METHOD_NAME_NFC_F_GET_MANUFACTURER = "getManufacturer";

        private TagTechnologyClosure parseArgsNfcFGetManufacturer(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNfcFGetManufacturer;
        }

        private CallResult callNfcFGetManufacturer() {
            @NonNull NfcF nfcF = Utils.liftTagTechnology(mTechnology, NfcF.class);
            byte[] response = nfcF.getManufacturer();
            return CallResult.successWithHex(response);
        }
        // end of wrapper for NfcF::getManufacturer

        // start of wrapper for NfcF::getMaxTransceiveLength
        private final static String METHOD_NAME_NFC_F_GET_MAX_TRANSCEIVE_LENGTH = "getMaxTransceiveLength";

        private TagTechnologyClosure parseArgsNfcFGetMaxTransceiveLength(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNfcFGetMaxTransceiveLength;
        }

        private CallResult callNfcFGetMaxTransceiveLength() {
            @NonNull NfcF nfcF = Utils.liftTagTechnology(mTechnology, NfcF.class);
            int response = nfcF.getMaxTransceiveLength();
            return CallResult.successWithInt(response);
        }
        // end of wrapper for NfcF::getMaxTransceiveLength

        // start of wrapper for NfcF::getSystemCode
        private final static String METHOD_NAME_NFC_F_GET_SYSTEM_CODE = "getSystemCode";

        private TagTechnologyClosure parseArgsNfcFGetSystemCode(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNfcFGetSystemCode;
        }

        private CallResult callNfcFGetSystemCode() {
            @NonNull NfcF nfcF = Utils.liftTagTechnology(mTechnology, NfcF.class);
            byte[] response = nfcF.getSystemCode();
            return CallResult.successWithHex(response);
        }
        // end of wrapper for NfcF::getSystemCode

        // start of wrapper for NfcF::getTimeout
        private final static String METHOD_NAME_NFC_F_GET_TIMEOUT = "getTimeout";

        private TagTechnologyClosure parseArgsNfcFGetTimeout(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNfcFGetTimeout;
        }

        private CallResult callNfcFGetTimeout() {
            @NonNull NfcF nfcF = Utils.liftTagTechnology(mTechnology, NfcF.class);
            int response = nfcF.getTimeout();
            return CallResult.successWithInt(response);
        }
        // end of wrapper for NfcF::getTimeout

        // start of wrapper for NfcF::setTimeout
        private final static String METHOD_NAME_NFC_F_SET_TIMEOUT = "setTimeout";

        private TagTechnologyClosure parseArgsNfcFSetTimeout(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);
            int timeout = Utils.parseInt(args[args.length-1]);
            return () -> callNfcFSetTimeout(timeout);
        }

        private CallResult callNfcFSetTimeout(int timeout) {
            @NonNull NfcF nfcF = Utils.liftTagTechnology(mTechnology, NfcF.class);
            nfcF.setTimeout(timeout);
            return CallResult.success();
        }
        // end of wrapper for NfcF::setTimeout

        // start of wrapper for NfcF::transceive
        private final static String METHOD_NAME_NFC_F_TRANSCEIVE = "transceive";

        private TagTechnologyClosure parseArgsNfcFTransceive(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);
            @NonNull byte[] data = Utils.parseHex(args[args.length-1]);
            return () -> callNfcFTransceive(data);
        }

        private CallResult callNfcFTransceive(byte[] data) throws IOException {
            @NonNull NfcF nfcF = Utils.liftTagTechnology(mTechnology, NfcF.class);
            byte[] response = nfcF.transceive(data);
            return CallResult.successWithHex(response);
        }
        // end of wrapper for NfcF::transceive
        // end of wrappers for class NfcF

        // start of wrappers for class NfcV
        // doc: https://developer.android.com/reference/android/nfc/tech/NfcV
        private static final String CLASS_NAME_NFC_V = "NfcV";

        // start of wrapper for NfcV::connect
        private final static String METHOD_NAME_NFC_V_CONNECT = "connect";

        private TagTechnologyClosure parseArgsNfcVConnect(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNfcVConnect;
        }

        private CallResult callNfcVConnect() throws IOException {
            return callTagTechnologyConnect(NfcV::get);
        }
        // end of wrapper for NfcV::connect

        // start of wrapper for NfcV::getDsfId
        private final static String METHOD_NAME_NFC_V_GET_DSF_ID = "getDsfId";

        private TagTechnologyClosure parseArgsNfcVGetDsfId(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNfcVGetDsfId;
        }

        private CallResult callNfcVGetDsfId() {
            @NonNull NfcV nfcV = Utils.liftTagTechnology(mTechnology, NfcV.class);
            byte response = nfcV.getDsfId();
            return CallResult.successWithString(Utils.formatHexOne(response));
        }
        // end of wrapper for NfcV::getDsfId

        // start of wrapper for NfcV::getMaxTransceiveLength
        private final static String METHOD_NAME_NFC_V_GET_MAX_TRANSCEIVE_LENGTH = "getMaxTransceiveLength";

        private TagTechnologyClosure parseArgsNfcVGetMaxTransceiveLength(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNfcVGetMaxTransceiveLength;
        }

        private CallResult callNfcVGetMaxTransceiveLength() {
            @NonNull NfcV nfcV = Utils.liftTagTechnology(mTechnology, NfcV.class);
            int response = nfcV.getMaxTransceiveLength();
            return CallResult.successWithInt(response);
        }
        // end of wrapper for NfcV::getMaxTransceiveLength

        // start of wrapper for NfcV::getResponseFlags
        private final static String METHOD_NAME_NFC_V_GET_RESPONSE_FLAGS = "getResponseFlags";

        private TagTechnologyClosure parseArgsNfcVGetResponseFlags(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNfcVGetResponseFlags;
        }

        private CallResult callNfcVGetResponseFlags() {
            @NonNull NfcV nfcV = Utils.liftTagTechnology(mTechnology, NfcV.class);
            byte response = nfcV.getResponseFlags();
            return CallResult.successWithString(Utils.formatHexOne(response));
        }
        // end of wrapper for NfcV::getResponseFlags

        // start of wrapper for NfcV::transceive
        private final static String METHOD_NAME_NFC_V_TRANSCEIVE = "transceive";

        private TagTechnologyClosure parseArgsNfcVTransceive(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);
            @NonNull byte[] data = Utils.parseHex(args[args.length-1]);
            return () -> callNfcVTransceive(data);
        }

        private CallResult callNfcVTransceive(byte[] data) throws IOException {
            @NonNull NfcV nfcV = Utils.liftTagTechnology(mTechnology, NfcV.class);
            byte[] response = nfcV.transceive(data);
            return CallResult.successWithHex(response);
        }
        // end of wrapper for NfcV::transceive
        // end of wrappers for class NfcV

        // start of wrappers for class IsoDep
        // doc: https://developer.android.com/reference/android/nfc/tech/IsoDep
        private static final String CLASS_NAME_ISO_DEP = "IsoDep";

        // start of wrapper for IsoDep::connect
        private final static String METHOD_NAME_ISO_DEP_CONNECT = "connect";

        private TagTechnologyClosure parseArgsIsoDepConnect(final @NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callIsoDepConnect;
        }

        private CallResult callIsoDepConnect() throws IOException {
            return callTagTechnologyConnect(IsoDep::get);
        }
        // end of wrapper for IsoDep::connect

        // start of wrapper for IsoDep::getHiLayerResponse
        private final static String METHOD_NAME_ISO_DEP_GET_HI_LAYER_RESPONSE = "getHiLayerResponse";

        private TagTechnologyClosure parseArgsIsoDepGetHiLayerResponse(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callIsoDepGetHiLayerResponse;
        }

        private CallResult callIsoDepGetHiLayerResponse() {
            @NonNull IsoDep isoDep = Utils.liftTagTechnology(mTechnology, IsoDep.class);
            byte[] response = isoDep.getHiLayerResponse();
            return CallResult.successWithHex(response);
        }
        // end of wrapper for IsoDep::getHiLayerResponse

        // start of wrapper for IsoDep::getHistoricalBytes
        private final static String METHOD_NAME_ISO_DEP_GET_HISTORICAL_BYTES = "getHistoricalBytes";

        private TagTechnologyClosure parseArgsIsoDepGetHistoricalBytes(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callGetHistoricalBytes;
        }

        private CallResult callGetHistoricalBytes() {
            @NonNull IsoDep isoDep = Utils.liftTagTechnology(mTechnology, IsoDep.class);
            byte[] response = isoDep.getHistoricalBytes();
            return CallResult.successWithHex(response);
        }
        // end of wrapper for IsoDep::getHistoricalBytes

        // start of wrapper for IsoDep::getMaxTransceiveLength
        private final static String METHOD_NAME_ISO_DEP_GET_MAX_TRANSCEIVE_LENGTH = "getMaxTransceiveLength";

        private TagTechnologyClosure parseArgsIsoDepGetMaxTransceiveLength(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callGetMaxTransceiveLength;
        }

        private CallResult callGetMaxTransceiveLength() {
            @NonNull IsoDep isoDep = Utils.liftTagTechnology(mTechnology, IsoDep.class);
            int response = isoDep.getMaxTransceiveLength();
            return CallResult.successWithInt(response);
        }
        // end of wrapper for IsoDep::getMaxTransceiveLength


        // start of wrapper for IsoDep::getTimeout
        private final static String METHOD_NAME_ISO_DEP_GET_TIMEOUT = "getTimeout";

        private TagTechnologyClosure parseArgsIsoDepGetTimeout(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callGetTimeout;
        }

        private CallResult callGetTimeout() {
            @NonNull IsoDep isoDep = Utils.liftTagTechnology(mTechnology, IsoDep.class);
            int response = isoDep.getTimeout();
            return CallResult.successWithInt(response);
        }
        // end of wrapper for IsoDep::getTimeout

        // start of wrapper for IsoDep::isExtendedLengthApduSupported
        private final static String METHOD_NAME_ISO_DEP_IS_EXTENDED_LENGTH_APDU_SUPPORTED = "isExtendedLengthApduSupported";

        private TagTechnologyClosure parseArgsIsoDepIsExtendedLengthApduSupported(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callIsExtendedLengthApduSupported;
        }

        private CallResult callIsExtendedLengthApduSupported() {
            @NonNull IsoDep isoDep = Utils.liftTagTechnology(mTechnology, IsoDep.class);
            boolean response = isoDep.isExtendedLengthApduSupported();
            return CallResult.successWithBool(response);
        }
        // end of wrapper for IsoDep::isExtendedLengthApduSupported

        // start of wrapper for IsoDep::setTimeout
        private final static String METHOD_NAME_ISO_DEP_SET_TIMEOUT = "setTimeout";

        private TagTechnologyClosure parseArgsIsoDepSetTimeout(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);
            int timeout = Utils.parseInt(args[args.length-1]);
            return () -> callSetTimeout(timeout);
        }

        private CallResult callSetTimeout(int timeout) {
            @NonNull IsoDep isoDep = Utils.liftTagTechnology(mTechnology, IsoDep.class);
            isoDep.setTimeout(timeout);
            return CallResult.success();
        }
        // end of wrapper for IsoDep::setTimeout

        // start of wrapper for IsoDep::transceive
        private final static String METHOD_NAME_ISO_DEP_TRANSCEIVE = "transceive";

        private TagTechnologyClosure parseArgsIsoDepTransceive(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);

            @NonNull byte[] requestData = Utils.parseHex(args[args.length-1]);
            return () -> callIsoDepTransceive(requestData);
        }

        private CallResult callIsoDepTransceive(@NonNull byte[] requestData) throws NfcException, IOException, IllegalStateException {
            @NonNull IsoDep isoDep = Utils.liftTagTechnology(mTechnology, IsoDep.class);
            @NonNull byte[] response = isoDep.transceive(requestData);
            return CallResult.successWithHex(response);
        }
        // end of wrapper for IsoDep::transceive
        // end of wrappers for class IsoDep

        // start of wrappers for class Ndef
        // doc: https://developer.android.com/reference/android/nfc/tech/Ndef
        private static final String CLASS_NAME_NDEF = "Ndef";

        // start of wrapper for Ndef::connect
        private final static String METHOD_NAME_NDEF_CONNECT = "connect";

        private TagTechnologyClosure parseArgsNdefConnect(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNdefConnect;
        }

        private CallResult callNdefConnect() throws IOException {
            return callTagTechnologyConnect(Ndef::get);
        }
        // end of wrapper for Ndef::connect

        // start of wrapper for Ndef::canMakeReadOnly
        private final static String METHOD_NAME_NDEF_CAN_MAKE_READ_ONLY = "canMakeReadOnly";

        private TagTechnologyClosure parseArgsNdefCanMakeReadOnly(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNdefCanMakeReadOnly;
        }

        private CallResult callNdefCanMakeReadOnly() {
            @NonNull Ndef ndef = Utils.liftTagTechnology(mTechnology, Ndef.class);
            boolean response = ndef.canMakeReadOnly();
            return CallResult.successWithBool(response);
        }
        // end of wrapper for Ndef::canMakeReadOnly

        // start of wrapper for Ndef::getCachedNdefMessage
        private final static String METHOD_NAME_NDEF_GET_CACHED_NDEF_MESSAGE = "getCachedNdefMessage";

        private TagTechnologyClosure parseArgsNdefGetCachedNdefMessage(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNdefGetCachedNdefMessage;
        }

        private CallResult callNdefGetCachedNdefMessage() {
            @NonNull Ndef ndef = Utils.liftTagTechnology(mTechnology, Ndef.class);
            NdefMessage msg = ndef.getCachedNdefMessage();
            return CallResult.successWithString(msg.toString());
        }
        // end of wrapper for Ndef::getCachedNdefMessage

        // start of wrapper for Ndef::getMaxSize
        private final static String METHOD_NAME_NDEF_GET_MAX_SIZE = "getMaxSize";

        private TagTechnologyClosure parseArgsNdefGetMaxSize(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNdefGetMaxSize;
        }

        private CallResult callNdefGetMaxSize() {
            @NonNull Ndef ndef = Utils.liftTagTechnology(mTechnology, Ndef.class);
            int response = ndef.getMaxSize();
            return CallResult.successWithInt(response);
        }
        // end of wrapper for Ndef::getMaxSize

        // start of wrapper for Ndef::getNdefMessage
        private final static String METHOD_NAME_NDEF_GET_NDEF_MESSAGE = "getNdefMessage";

        private TagTechnologyClosure parseArgsNdefGetNdefMessage(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNdefGetNdefMessage;
        }

        private CallResult callNdefGetNdefMessage() throws IOException, android.nfc.FormatException {
            @NonNull Ndef ndef = Utils.liftTagTechnology(mTechnology, Ndef.class);
            NdefMessage msg = ndef.getNdefMessage();
            return CallResult.successWithString(msg.toString());
        }
        // end of wrapper for Ndef::getNdefMessage

        // start of wrapper for Ndef::getType
        private final static String METHOD_NAME_NDEF_GET_TYPE = "getType";

        private TagTechnologyClosure parseArgsNdefGetType(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNdefGetType;
        }

        private CallResult callNdefGetType() {
            @NonNull Ndef ndef = Utils.liftTagTechnology(mTechnology, Ndef.class);
            String response = ndef.getType();
            return CallResult.successWithString(response);
        }
        // end of wrapper for Ndef::getType

        // start of wrapper for Ndef::isWritable
        private final static String METHOD_NAME_NDEF_IS_WRITABLE = "isWritable";

        private TagTechnologyClosure parseArgsNdefIsWritable(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNdefIsWritable;
        }

        private CallResult callNdefIsWritable() {
            @NonNull Ndef ndef = Utils.liftTagTechnology(mTechnology, Ndef.class);
            boolean response = ndef.isWritable();
            return CallResult.successWithBool(response);
        }
        // end of wrapper for Ndef::isWritable

        // start of wrapper for Ndef::makeReadOnly
        private final static String METHOD_NAME_NDEF_MAKE_READ_ONLY = "makeReadOnly";

        private TagTechnologyClosure parseArgsNdefMakeReadOnly(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNdefMakeReadOnly;
        }

        private CallResult callNdefMakeReadOnly() throws IOException {
            @NonNull Ndef ndef = Utils.liftTagTechnology(mTechnology, Ndef.class);
            boolean result = ndef.makeReadOnly();
            return CallResult.successWithBool(result);
        }
        // end of wrapper for Ndef::makeReadOnly

        // start of wrapper for Ndef::writeNdefMessage
        private final static String METHOD_NAME_NDEF_WRITE_NDEF_MESSAGE = "writeNdefMessage";

        private TagTechnologyClosure parseArgsNdefWriteNdefMessage(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);
            @NonNull byte[] msgHex = Utils.parseHex(args[args.length-1]);
            return () -> callNdefWriteNdefMessage(msgHex);
        }

        private CallResult callNdefWriteNdefMessage(@NonNull byte[] msgHex) throws FormatException, IOException {
            @NonNull Ndef ndef = Utils.liftTagTechnology(mTechnology, Ndef.class);
            ndef.writeNdefMessage(new NdefMessage(msgHex));
            return CallResult.success();
        }
        // end of wrapper for Ndef::writeNdefMessage
        // end of wrappers for class Ndef

        // start of wrappers for class MifareClassic
        // doc: https://developer.android.com/reference/android/nfc/tech/MifareClassic
        private final static String CLASS_NAME_MIFARE_CLASSIC = "MifareClassic";

        // start of wrapper for MifareClassic::authenticateSectorWithKeyA
        private final static String METHOD_NAME_MIFARE_CLASSIC_AUTHENTICATE_SECTOR_WITH_KEY_A = "authenticateSectorWithKeyA";

        private TagTechnologyClosure parseArgsMifareClassicAuthenticateSectorWithKeyA(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 2, args.length);
            int sectorIndex = Utils.parseInt(args[args.length - 2]);
            @NonNull byte[] key = Utils.parseHex(args[args.length - 1]);
            return () -> callMifareClassicAuthenticateSectorWithKeyA(sectorIndex, key);
        }

        private CallResult callMifareClassicAuthenticateSectorWithKeyA(int sectorIndex, @NonNull byte[] key) throws IOException {
            @NonNull MifareClassic mifareClassic = Utils.liftTagTechnology(mTechnology, MifareClassic.class);
            boolean result = mifareClassic.authenticateSectorWithKeyA(sectorIndex, key);
            return CallResult.successWithBool(result);
        }
        // end of wrapper for MifareClassic::authenticateSectorWithKeyA

        // start of wrapper for MifareClassic::authenticateSectorWithKeyB
        private final static String METHOD_NAME_MIFARE_CLASSIC_AUTHENTICATE_SECTOR_WITH_KEY_B = "authenticateSectorWithKeyB";

        private TagTechnologyClosure parseArgsMifareClassicAuthenticateSectorWithKeyB(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 2, args.length);
            int sectorIndex = Utils.parseInt(args[args.length - 2]);
            @NonNull byte[] key = Utils.parseHex(args[args.length - 1]);
            return () -> callMifareClassicAuthenticateSectorWithKeyB(sectorIndex, key);
        }

        private CallResult callMifareClassicAuthenticateSectorWithKeyB(int sectorIndex, @NonNull byte[] key) throws IOException {
            @NonNull MifareClassic mifareClassic = Utils.liftTagTechnology(mTechnology, MifareClassic.class);
            boolean result = mifareClassic.authenticateSectorWithKeyB(sectorIndex, key);
            return CallResult.successWithBool(result);
        }
        // end of wrapper for MifareClassic::authenticateSectorWithKeyB

        // start of wrapper for MifareClassic::blockToSector
        private final static String METHOD_NAME_MIFARE_CLASSIC_BLOCK_TO_SECTOR = "blockToSector";

        private TagTechnologyClosure parseArgsMifareClassicBlockToSector(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);
            int blockIndex = Utils.parseInt(args[args.length - 1]);
            return () -> callMifareClassicBlockToSector(blockIndex);
        }

        private CallResult callMifareClassicBlockToSector(int blockIndex) {
            @NonNull MifareClassic mifareClassic = Utils.liftTagTechnology(mTechnology, MifareClassic.class);
            int response = mifareClassic.blockToSector(blockIndex);
            return CallResult.successWithInt(response);
        }
        // end of wrapper for MifareClassic::blockToSector

        // start of wrapper for MifareClassic::connect (inherited from TagTechnology)
        private final static String METHOD_NAME_MIFARE_CLASSIC_CONNECT = "connect";

        private TagTechnologyClosure parseArgsMifareClassicConnect(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callMifareClassicConnect;
        }

        private CallResult callMifareClassicConnect() throws IOException {
            return callTagTechnologyConnect(MifareClassic::get);
        }
        // end of wrapper for MifareClassic::connect

        // start of wrapper for MifareClassic::decrement
        private final static String METHOD_NAME_MIFARE_CLASSIC_DECREMENT = "decrement";

        private TagTechnologyClosure parseArgsMifareClassicDecrement(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 2, args.length);
            int blockIndex = Utils.parseInt(args[args.length - 2]);
            int value = Utils.parseInt(args[args.length - 1]);
            return () -> callMifareClassicDecrement(blockIndex, value);
        }

        private CallResult callMifareClassicDecrement(int blockIndex, int value) throws IOException {
            @NonNull MifareClassic mifareClassic = Utils.liftTagTechnology(mTechnology, MifareClassic.class);
            mifareClassic.decrement(blockIndex, value);
            return CallResult.success();
        }
        // end of wrapper for MifareClassic::decrement

        // start of wrapper for MifareClassic::getBlockCount
        private final static String METHOD_NAME_MIFARE_CLASSIC_GET_BLOCK_COUNT = "getBlockCount";

        private TagTechnologyClosure parseArgsMifareClassicGetBlockCount(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callMifareClassicGetBlockCount;
        }

        private CallResult callMifareClassicGetBlockCount() {
            @NonNull MifareClassic mifareClassic = Utils.liftTagTechnology(mTechnology, MifareClassic.class);
            int response = mifareClassic.getBlockCount();
            return CallResult.successWithInt(response);
        }
        // end of wrapper for MifareClassic::getBlockCount

        // start of wrapper for MifareClassic::getBlockCountInSector
        private final static String METHOD_NAME_MIFARE_CLASSIC_GET_BLOCK_COUNT_IN_SECTOR = "getBlockCountInSector";

        private TagTechnologyClosure parseArgsMifareClassicGetBlockCountInSector(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);
            int sectorIndex = Utils.parseInt(args[args.length - 1]);
            return () -> callMifareClassicGetBlockCountInSector(sectorIndex);
        }

        private CallResult callMifareClassicGetBlockCountInSector(int sectorIndex) {
            @NonNull MifareClassic mifareClassic = Utils.liftTagTechnology(mTechnology, MifareClassic.class);
            int response = mifareClassic.getBlockCountInSector(sectorIndex);
            return CallResult.successWithInt(response);
        }
        // end of wrapper for MifareClassic::getBlockCountInSector

        // start of wrapper for MifareClassic::getMaxTransceiveLength
        private final static String METHOD_NAME_MIFARE_CLASSIC_GET_MAX_TRANSCEIVE_LENGTH = "getMaxTransceiveLength";

        private TagTechnologyClosure parseArgsMifareClassicGetMaxTransceiveLength(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callMifareClassicGetMaxTransceiveLength;
        }

        private CallResult callMifareClassicGetMaxTransceiveLength() {
            @NonNull MifareClassic mifareClassic = Utils.liftTagTechnology(mTechnology, MifareClassic.class);
            int response = mifareClassic.getMaxTransceiveLength();
            return CallResult.successWithInt(response);
        }
        // end of wrapper for MifareClassic::getMaxTransceiveLength

        // start of wrapper for MifareClassic::getSectorCount
        private final static String METHOD_NAME_MIFARE_CLASSIC_GET_SECTOR_COUNT = "getSectorCount";

        private TagTechnologyClosure parseArgsMifareClassicGetSectorCount(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callMifareClassicGetSectorCount;
        }

        private CallResult callMifareClassicGetSectorCount() {
            @NonNull MifareClassic mifareClassic = Utils.liftTagTechnology(mTechnology, MifareClassic.class);
            int response = mifareClassic.getSectorCount();
            return CallResult.successWithInt(response);
        }
        // end of wrapper for MifareClassic::getSectorCount

        // start of wrapper for MifareClassic::getSize
        private final static String METHOD_NAME_MIFARE_CLASSIC_GET_SIZE = "getSize";

        private TagTechnologyClosure parseArgsMifareClassicGetSize(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callMifareClassicGetSize;
        }

        private CallResult callMifareClassicGetSize() {
            @NonNull MifareClassic mifareClassic = Utils.liftTagTechnology(mTechnology, MifareClassic.class);
            int response = mifareClassic.getSize();
            return CallResult.successWithInt(response);
        }
        // end of wrapper for MifareClassic::getSize

        // start of wrapper for MifareClassic::getTimeout
        private final static String METHOD_NAME_MIFARE_CLASSIC_GET_TIMEOUT = "getTimeout";

        private TagTechnologyClosure parseArgsMifareClassicGetTimeout(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callMifareClassicGetTimeout;
        }

        private CallResult callMifareClassicGetTimeout() {
            @NonNull MifareClassic mifareClassic = Utils.liftTagTechnology(mTechnology, MifareClassic.class);
            int response = mifareClassic.getTimeout();
            return CallResult.successWithInt(response);
        }
        // end of wrapper for MifareClassic::getTimeout

        // start of wrapper for MifareClassic::getType
        private final static String METHOD_NAME_MIFARE_CLASSIC_GET_TYPE = "getType";

        private TagTechnologyClosure parseArgsMifareClassicGetType(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callMifareClassicGetType;
        }

        private CallResult callMifareClassicGetType() {
            @NonNull MifareClassic mifareClassic = Utils.liftTagTechnology(mTechnology, MifareClassic.class);
            int response = mifareClassic.getType();
            return CallResult.successWithInt(response);
        }
        // end of wrapper for MifareClassic::getType

        // start of wrapper for MifareClassic::increment
        private final static String METHOD_NAME_MIFARE_CLASSIC_INCREMENT = "increment";

        private TagTechnologyClosure parseArgsMifareClassicIncrement(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 2, args.length);
            int blockIndex = Utils.parseInt(args[args.length - 2]);
            int value = Utils.parseInt(args[args.length - 1]);
            return () -> callMifareClassicIncrement(blockIndex, value);
        }

        private CallResult callMifareClassicIncrement(int blockIndex, int value) throws IOException {
            @NonNull MifareClassic mifareClassic = Utils.liftTagTechnology(mTechnology, MifareClassic.class);
            mifareClassic.increment(blockIndex, value);
            return CallResult.success();
        }
        // end of wrapper for MifareClassic::increment

        // start of wrapper for MifareClassic::readBlock
        private final static String METHOD_NAME_MIFARE_CLASSIC_READ_BLOCK = "readBlock";

        private TagTechnologyClosure parseArgsMifareClassicReadBlock(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);
            int blockIndex = Utils.parseInt(args[args.length - 1]);
            return () -> callMifareClassicReadBlock(blockIndex);
        }

        private CallResult callMifareClassicReadBlock(int blockIndex) throws IOException {
            @NonNull MifareClassic mifareClassic = Utils.liftTagTechnology(mTechnology, MifareClassic.class);
            byte[] response = mifareClassic.readBlock(blockIndex);
            return CallResult.successWithHex(response);
        }
        // end of wrapper for MifareClassic::readBlock

        // start of wrapper for MifareClassic::restore
        private final static String METHOD_NAME_MIFARE_CLASSIC_RESTORE = "restore";

        private TagTechnologyClosure parseArgsMifareClassicRestore(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);
            int blockIndex = Utils.parseInt(args[args.length-1]);
            return () -> callMifareClassicRestore(blockIndex);
        }

        private CallResult callMifareClassicRestore(int blockIndex) throws IOException {
            @NonNull MifareClassic mifareClassic = Utils.liftTagTechnology(mTechnology, MifareClassic.class);
            mifareClassic.restore(blockIndex);
            return CallResult.success();
        }
        // end of wrapper for MifareClassic::restore

        // start of wrapper for MifareClassic::sectorToBlock
        private final static String METHOD_NAME_MIFARE_CLASSIC_SECTOR_TO_BLOCK = "sectorToBlock";

        private TagTechnologyClosure parseArgsMifareClassicSectorToBlock(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);
            int sectorIndex = Utils.parseInt(args[args.length - 1]);
            return () -> callMifareClassicSectorToBlock(sectorIndex);
        }

        private CallResult callMifareClassicSectorToBlock(int sectorIndex) {
            @NonNull MifareClassic mifareClassic = Utils.liftTagTechnology(mTechnology, MifareClassic.class);
            int response = mifareClassic.sectorToBlock(sectorIndex);
            return CallResult.successWithInt(response);
        }
        // end of wrapper for MifareClassic::sectorToBlock

        // start of wrapper for MifareClassic::setTimeout
        private final static String METHOD_NAME_MIFARE_CLASSIC_SET_TIMEOUT = "setTimeout";

        private TagTechnologyClosure parseArgsMifareClassicSetTimeout(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);
            int timeout = Utils.parseInt(args[args.length - 1]);
            return () -> callMifareClassicSetTimeout(timeout);
        }

        private CallResult callMifareClassicSetTimeout(int timeout) {
            @NonNull MifareClassic mifareClassic = Utils.liftTagTechnology(mTechnology, MifareClassic.class);
            mifareClassic.setTimeout(timeout);
            return CallResult.success();
        }
        // end of wrapper for MifareClassic::setTimeout

        // start of wrapper for MifareClassic::transceive
        private final static String METHOD_NAME_MIFARE_CLASSIC_TRANSCEIVE = "transceive";

        private TagTechnologyClosure parseArgsMifareClassicTransceive(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);
            @NonNull byte[] data = Utils.parseHex(args[args.length - 1]);
            return () -> callMifareClassicTransceive(data);
        }

        private CallResult callMifareClassicTransceive(@NonNull byte[] data) throws IOException {
            @NonNull MifareClassic mifareClassic = Utils.liftTagTechnology(mTechnology, MifareClassic.class);
            byte[] response = mifareClassic.transceive(data);
            return CallResult.successWithHex(response);
        }
        // end of wrapper for MifareClassic::transceive


        // start of wrapper for MifareClassic::transfer
        private final static String METHOD_NAME_MIFARE_CLASSIC_TRANSFER = "transfer";

        private TagTechnologyClosure parseArgsMifareClassicTransfer(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);
            int blockIndex = Utils.parseInt(args[args.length - 1]);
            return () -> callMifareClassicTransfer(blockIndex);
        }

        private CallResult callMifareClassicTransfer(int blockIndex) throws IOException {
            @NonNull MifareClassic mifareClassic = Utils.liftTagTechnology(mTechnology, MifareClassic.class);
            mifareClassic.transfer(blockIndex);
            return CallResult.success();
        }
        // end of wrapper for MifareClassic::transfer

        // start of wrapper for MifareClassic::writeBlock
        private final static String METHOD_NAME_MIFARE_CLASSIC_WRITE_BLOCK = "writeBlock";

        private TagTechnologyClosure parseArgsMifareClassicWriteBlock(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 2, args.length);
            int blockIndex = Utils.parseInt(args[args.length - 2]);
            @NonNull byte[] data = Utils.parseHex(args[args.length - 1]);
            return () -> callMifareClassicWriteBlock(blockIndex, data);
        }

        private CallResult callMifareClassicWriteBlock(int blockIndex, @NonNull byte[] data) throws IOException {
            @NonNull MifareClassic mifareClassic = Utils.liftTagTechnology(mTechnology, MifareClassic.class);
            mifareClassic.writeBlock(blockIndex, data);
            return CallResult.success();
        }
        // end of wrapper for MifareClassic::writeBlock
        // end of wrappers for class MifareClassic

        // start of wrappers for class MifareUltralight
        // doc: https://developer.android.com/reference/android/nfc/tech/MifareUltralight

        private final static String CLASS_NAME_MIFARE_ULTRALIGHT = "MifareUltralight";

        // start of wrapper for MifareUltralight::connect (inherited from TagTechnology)
        private final static String METHOD_NAME_MIFARE_ULTRALIGHT_CONNECT = "connect";

        private TagTechnologyClosure parseArgsMifareUltralightConnect(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callMifareUltralightConnect;
        }

        private CallResult callMifareUltralightConnect() throws IOException {
            return callTagTechnologyConnect(MifareUltralight::get);
        }
        // end of wrapper for MifareUltralight::connect

        // start of wrapper for MifareUltralight::getMaxTransceiveLength
        private final static String METHOD_NAME_MIFARE_ULTRALIGHT_GET_MAX_TRANSCEIVE_LENGTH = "getMaxTransceiveLength";

        private TagTechnologyClosure parseArgsMifareUltralightGetMaxTransceiveLength(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callMifareUltralightGetMaxTransceiveLength;
        }

        private CallResult callMifareUltralightGetMaxTransceiveLength() {
            @NonNull MifareUltralight mifareUltralight = Utils.liftTagTechnology(mTechnology, MifareUltralight.class);
            int response = mifareUltralight.getMaxTransceiveLength();
            return CallResult.successWithInt(response);
        }
        // end of wrapper for MifareUltralight::getMaxTransceiveLength

        // start of wrapper for MifareUltralight::getTimeout
        private final static String METHOD_NAME_MIFARE_ULTRALIGHT_GET_TIMEOUT = "getTimeout";

        private TagTechnologyClosure parseArgsMifareUltralightGetTimeout(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callMifareUltralightGetTimeout;
        }

        private CallResult callMifareUltralightGetTimeout() {
            @NonNull MifareUltralight mifareUltralight = Utils.liftTagTechnology(mTechnology, MifareUltralight.class);
            int response = mifareUltralight.getTimeout();
            return CallResult.successWithInt(response);
        }
        // end of wrapper for MifareUltralight::getTimeout

        // start of wrapper for MifareUltralight::getType
        private final static String METHOD_NAME_MIFARE_ULTRALIGHT_GET_TYPE = "getType";

        private TagTechnologyClosure parseArgsMifareUltralightGetType(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callMifareUltralightGetType;
        }

        private CallResult callMifareUltralightGetType() {
            @NonNull MifareUltralight mifareUltralight = Utils.liftTagTechnology(mTechnology, MifareUltralight.class);
            int response = mifareUltralight.getType();
            return CallResult.successWithInt(response);
        }
        // end of wrapper for MifareUltralight::getType

        // start of wrapper for MifareUltralight::readPages
        private final static String METHOD_NAME_MIFARE_ULTRALIGHT_READ_PAGES = "readPages";

        private TagTechnologyClosure parseArgsMifareUltralightReadPages(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);
            int pageOffset = Utils.parseInt(args[args.length - 1]);
            return () -> callMifareUltralightReadPages(pageOffset);
        }

        private CallResult callMifareUltralightReadPages(int pageOffset) throws IOException {
            @NonNull MifareUltralight mifareUltralight = Utils.liftTagTechnology(mTechnology, MifareUltralight.class);
            byte[] response = mifareUltralight.readPages(pageOffset);
            return CallResult.successWithHex(response);
        }
        // end of wrapper for MifareUltralight::readPages

        // start of wrapper for MifareUltralight::setTimeout
        private final static String METHOD_NAME_MIFARE_ULTRALIGHT_SET_TIMEOUT = "setTimeout";

        private TagTechnologyClosure parseArgsMifareUltralightSetTimeout(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);
            int timeout = Utils.parseInt(args[args.length - 1]);
            return () -> callMifareUltralightSetTimeout(timeout);
        }

        private CallResult callMifareUltralightSetTimeout(int timeout) {
            @NonNull MifareUltralight mifareUltralight = Utils.liftTagTechnology(mTechnology, MifareUltralight.class);
            mifareUltralight.setTimeout(timeout);
            return CallResult.success();
        }
        // end of wrapper for MifareUltralight::setTimeout

        // start of wrapper for MifareUltralight::transceive
        private final static String METHOD_NAME_MIFARE_ULTRALIGHT_TRANSCEIVE = "transceive";

        private TagTechnologyClosure parseArgsMifareUltralightTransceive(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);
            @NonNull byte[] data = Utils.parseHex(args[args.length - 1]);
            return () -> callMifareUltralightTransceive(data);
        }

        private CallResult callMifareUltralightTransceive(@NonNull byte[] data) throws IOException {
            @NonNull MifareUltralight mifareUltralight = Utils.liftTagTechnology(mTechnology, MifareUltralight.class);
            byte[] response = mifareUltralight.transceive(data);
            return CallResult.successWithHex(response);
        }
        // end of wrapper for MifareUltralight::transceive

        // start of wrapper for MifareUltralight::writePage
        private final static String METHOD_NAME_MIFARE_ULTRALIGHT_WRITE_PAGE = "writePage";

        private TagTechnologyClosure parseArgsMifareUltralightWritePage(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 2, args.length);
            int pageOffset = Utils.parseInt(args[args.length - 2]);
            @NonNull byte[] data = Utils.parseHex(args[args.length - 1]);
            return () -> callMifareUltralightWritePage(pageOffset, data);
        }

        private CallResult callMifareUltralightWritePage(int pageOffset, @NonNull byte[] data) throws IOException {
            @NonNull MifareUltralight mifareUltralight = Utils.liftTagTechnology(mTechnology, MifareUltralight.class);
            mifareUltralight.writePage(pageOffset, data);
            return CallResult.success();
        }
        // end of wrapper for MifareUltralight::writePage
        // end of wrappers for class MifareUltralight


        // start of wrappers for class NfcBarcode
        // doc: https://developer.android.com/reference/android/nfc/tech/NfcBarcode
        private final static String CLASS_NAME_NFC_BARCODE = "NfcBarcode";

        // start of wrapper for NfcBarcode::connect (inherited from TagTechnology)
        private final static String METHOD_NAME_NFC_BARCODE_CONNECT = "connect";

        private TagTechnologyClosure parseArgsNfcBarcodeConnect(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNfcBarcodeConnect;
        }

        private CallResult callNfcBarcodeConnect() throws IOException {
            return callTagTechnologyConnect(NfcBarcode::get);
        }
        // end of wrapper for NfcBarcode::connect

        // start of wrapper for NfcBarcode::getBarcode
        private final static String METHOD_NAME_NFC_BARCODE_GET_BARCODE = "getBarcode";

        private TagTechnologyClosure parseArgsNfcBarcodeGetBarcode(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNfcBarcodeGetBarcode;
        }

        private CallResult callNfcBarcodeGetBarcode() {
            @NonNull NfcBarcode nfcBarcode = Utils.liftTagTechnology(mTechnology, NfcBarcode.class);
            byte[] response = nfcBarcode.getBarcode();
            return CallResult.successWithHex(response);
        }
        // end of wrapper for NfcBarcode::getBarcode

        // start of wrapper for NfcBarcode::getType
        private final static String METHOD_NAME_NFC_BARCODE_GET_TYPE = "getType";

        private TagTechnologyClosure parseArgsNfcBarcodeGetType(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNfcBarcodeGetType;
        }

        private CallResult callNfcBarcodeGetType() {
            @NonNull NfcBarcode nfcBarcode = Utils.liftTagTechnology(mTechnology, NfcBarcode.class);
            int response = nfcBarcode.getType();
            return CallResult.successWithInt(response);
        }
        // end of wrapper for NfcBarcode::getType
        // end of wrappers for class NfcBarcode

        // start of wrappers for class NdefFormatable
        // doc: https://developer.android.com/reference/android/nfc/tech/NdefFormatable
        private final static String CLASS_NAME_NDEF_FORMATABLE = "NdefFormatable";

        // start of wrapper for NdefFormatable::connect (inherited from TagTechnology)
        private final static String METHOD_NAME_NDEF_FORMATABLE_CONNECT = "connect";

        private TagTechnologyClosure parseArgsNdefFormatableConnect(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2, args.length);
            return this::callNdefFormatableConnect;
        }

        private CallResult callNdefFormatableConnect() throws IOException {
            return callTagTechnologyConnect(NdefFormatable::get);
        }
        // end of wrapper for NdefFormatable::connect

        // start of wrapper for NdefFormatable::format
        private final static String METHOD_NAME_NDEF_FORMATABLE_FORMAT = "format";

        private TagTechnologyClosure parseArgsNdefFormatableFormat(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);
            @NonNull byte[] ndefMessageHex = Utils.parseHex(args[args.length - 1]);
            return () -> callNdefFormatableFormat(ndefMessageHex);
        }

        private CallResult callNdefFormatableFormat(@NonNull byte[] ndefMessageHex) throws IOException, FormatException {
            @NonNull NdefFormatable ndefFormatable = Utils.liftTagTechnology(mTechnology, NdefFormatable.class);
            ndefFormatable.format(new NdefMessage(ndefMessageHex));
            return CallResult.success();
        }
        // end of wrapper for NdefFormatable::format

        // start of wrapper for NdefFormatable::formatReadOnly
        private final static String METHOD_NAME_NDEF_FORMATABLE_FORMAT_READ_ONLY = "formatReadOnly";

        private TagTechnologyClosure parseArgsNdefFormatableFormatReadOnly(@NonNull String[] args) throws ArgumentException {
            Utils.checkIntEqual(2 + 1, args.length);
            @NonNull byte[] ndefMessageHex = Utils.parseHex(args[args.length - 1]);
            return () -> callNdefFormatableFormatReadOnly(ndefMessageHex);
        }

        private CallResult callNdefFormatableFormatReadOnly(@NonNull byte[] ndefMessageHex) throws IOException, FormatException {
            @NonNull NdefFormatable ndefFormatable = Utils.liftTagTechnology(mTechnology, NdefFormatable.class);
            ndefFormatable.formatReadOnly(new NdefMessage(ndefMessageHex));
            return CallResult.success();
        }
        // end of wrapper for NdefFormatable::formatReadOnly
        // end of wrappers for class NdefFormatable

        // helper function for subclasses' connect()
        private <T extends TagTechnology> CallResult callTagTechnologyConnect(Function<Tag, T> tagGetter) throws IOException {
            Utils.checkTagNonNull(mTag);
            mTechnology = tagGetter.apply(mTag);
            if (mTechnology == null) {
                throw new UnsupportedTechnology();
            }
            mTechnology.connect();
            return CallResult.success();
        }

        TagTechnologyClosure parseClosureFromArgs(@NonNull String[] args) throws ArgumentException {
            if (args.length == 0) {
                throw new InvalidCommandException();
            }

            switch (args[0]) {
                case METHOD_NAME_QUIT:
                    return parseArgsQuit(args);

                case CLASS_NAME_TAG_TECHNOLOGY: {
                    if (args.length < 2) {
                        throw new InvalidCommandException();
                    }
                    switch (args[1]) {
                        case METHOD_NAME_TAG_TECHNOLOGY_CLOSE:
                            return parseArgsTagTechnologyClose(args);
                        case METHOD_NAME_TAG_TECHNOLOGY_IS_CONNECTED:
                            return parseArgsTagTechnologyIsConnected(args);
                        case METHOD_NAME_TAG_TECHNOLOGY_GET_TAG:
                            return parseArgsTagTechnologyGetTag(args);
                        default:
                            throw new InvalidCommandException();
                    }
                }

                case CLASS_NAME_NFC_A: {
                    if (args.length < 2) {
                        throw new InvalidCommandException();
                    }
                    switch (args[1]) {
                        case METHOD_NAME_NFC_A_CONNECT:
                            return parseArgsNfcAConnect(args);
                        case METHOD_NAME_NFC_A_GET_ATQA:
                            return parseArgsNfcAGetAtqa(args);
                        case METHOD_NAME_NFC_A_GET_MAX_TRANSCEIVE_LENGTH:
                            return parseArgsNfcAGetMaxTransceiveLength(args);
                        case METHOD_NAME_NFC_A_GET_SAK:
                            return parseArgsNfcAGetSak(args);
                        case METHOD_NAME_NFC_A_GET_TIMEOUT:
                            return parseArgsNfcAGetTimeout(args);
                        case METHOD_NAME_NFC_A_SET_TIMEOUT:
                            return parseArgsNfcASetTimeout(args);
                        case METHOD_NAME_NFC_A_TRANSCEIVE:
                            return parseArgsNfcATransceive(args);
                        default:
                            throw new InvalidCommandException();
                    }
                }

                case CLASS_NAME_NFC_B: {
                    if (args.length < 2) {
                        throw new InvalidCommandException();
                    }
                    switch (args[1]) {
                        case METHOD_NAME_NFC_B_CONNECT:
                            return parseArgsNfcBConnect(args);
                        case METHOD_NAME_NFC_B_GET_APPLICATION_DATA:
                            return parseArgsNfcBGetApplicationData(args);
                        case METHOD_NAME_NFC_B_GET_MAX_TRANSCEIVE_LENGTH:
                            return parseArgsNfcBGetMaxTransceiveLength(args);
                        case METHOD_NAME_NFC_B_GET_PROTOCOL_INFO:
                        case METHOD_NAME_NFC_B_TRANSCEIVE:
                            return parseArgsNfcBTransceive(args);
                        default:
                            throw new InvalidCommandException();
                    }
                }

                case CLASS_NAME_NFC_F: {
                    if (args.length < 2) {
                        throw new InvalidCommandException();
                    }
                    switch (args[1]) {
                        case METHOD_NAME_NFC_F_CONNECT:
                            return parseArgsNfcFConnect(args);
                        case METHOD_NAME_NFC_F_GET_MANUFACTURER:
                            return parseArgsNfcFGetManufacturer(args);
                        case METHOD_NAME_NFC_F_GET_MAX_TRANSCEIVE_LENGTH:
                            return parseArgsNfcFGetMaxTransceiveLength(args);
                        case METHOD_NAME_NFC_F_GET_SYSTEM_CODE:
                            return parseArgsNfcFGetSystemCode(args);
                        case METHOD_NAME_NFC_F_GET_TIMEOUT:
                            return parseArgsNfcFGetTimeout(args);
                        case METHOD_NAME_NFC_F_SET_TIMEOUT:
                            return parseArgsNfcFSetTimeout(args);
                        case METHOD_NAME_NFC_F_TRANSCEIVE:
                            return parseArgsNfcFTransceive(args);
                        default:
                            throw new InvalidCommandException();
                    }
                }

                case CLASS_NAME_NFC_V: {
                    if (args.length < 2) {
                        throw new InvalidCommandException();
                    }
                    switch (args[1]) {
                        case METHOD_NAME_NFC_V_CONNECT:
                            return parseArgsNfcVConnect(args);
                        case METHOD_NAME_NFC_V_GET_DSF_ID:
                            return parseArgsNfcVGetDsfId(args);
                        case METHOD_NAME_NFC_V_GET_MAX_TRANSCEIVE_LENGTH:
                            return parseArgsNfcVGetMaxTransceiveLength(args);
                        case METHOD_NAME_NFC_V_GET_RESPONSE_FLAGS:
                            return parseArgsNfcVGetResponseFlags(args);
                        case METHOD_NAME_NFC_V_TRANSCEIVE:
                            return parseArgsNfcVTransceive(args);
                        default:
                            throw new InvalidCommandException();
                    }
                }

                case CLASS_NAME_ISO_DEP: {
                    if (args.length < 2) {
                        throw new InvalidCommandException();
                    }
                    switch (args[1]) {
                        case METHOD_NAME_ISO_DEP_CONNECT:
                            return parseArgsIsoDepConnect(args);
                        case METHOD_NAME_ISO_DEP_GET_HI_LAYER_RESPONSE:
                            return parseArgsIsoDepGetHiLayerResponse(args);
                        case METHOD_NAME_ISO_DEP_GET_HISTORICAL_BYTES:
                            return parseArgsIsoDepGetHistoricalBytes(args);
                        case METHOD_NAME_ISO_DEP_GET_MAX_TRANSCEIVE_LENGTH:
                            return parseArgsIsoDepGetMaxTransceiveLength(args);
                        case METHOD_NAME_ISO_DEP_GET_TIMEOUT:
                            return parseArgsIsoDepGetTimeout(args);
                        case METHOD_NAME_ISO_DEP_IS_EXTENDED_LENGTH_APDU_SUPPORTED:
                            return parseArgsIsoDepIsExtendedLengthApduSupported(args);
                        case METHOD_NAME_ISO_DEP_SET_TIMEOUT:
                            return parseArgsIsoDepSetTimeout(args);
                        case METHOD_NAME_ISO_DEP_TRANSCEIVE:
                            return parseArgsIsoDepTransceive(args);
                        default:
                            throw new InvalidCommandException();
                    }
                }

                case CLASS_NAME_NDEF: {
                    if (args.length < 2) {
                        throw new InvalidCommandException();
                    }
                    switch (args[1]) {
                        case METHOD_NAME_NDEF_CAN_MAKE_READ_ONLY:
                            return parseArgsNdefCanMakeReadOnly(args);
                        case METHOD_NAME_NDEF_CONNECT:
                            return parseArgsNdefConnect(args);
                        case METHOD_NAME_NDEF_GET_CACHED_NDEF_MESSAGE:
                            return parseArgsNdefGetCachedNdefMessage(args);
                        case METHOD_NAME_NDEF_GET_MAX_SIZE:
                            return parseArgsNdefGetMaxSize(args);
                        case METHOD_NAME_NDEF_GET_NDEF_MESSAGE:
                            return parseArgsNdefGetNdefMessage(args);
                        case METHOD_NAME_NDEF_GET_TYPE:
                            return parseArgsNdefGetType(args);
                        case METHOD_NAME_NDEF_IS_WRITABLE:
                            return parseArgsNdefIsWritable(args);
                        case METHOD_NAME_NDEF_MAKE_READ_ONLY:
                            return parseArgsNdefMakeReadOnly(args);
                        case METHOD_NAME_NDEF_WRITE_NDEF_MESSAGE:
                            return parseArgsNdefWriteNdefMessage(args);
                        default:
                            throw new InvalidCommandException();
                    }
                }

                case CLASS_NAME_MIFARE_CLASSIC: {
                    if (args.length < 2) {
                        throw new InvalidCommandException();
                    }
                    switch (args[1]) {
                        case METHOD_NAME_MIFARE_CLASSIC_AUTHENTICATE_SECTOR_WITH_KEY_A:
                            return parseArgsMifareClassicAuthenticateSectorWithKeyA(args);
                        case METHOD_NAME_MIFARE_CLASSIC_AUTHENTICATE_SECTOR_WITH_KEY_B:
                            return parseArgsMifareClassicAuthenticateSectorWithKeyB(args);
                        case METHOD_NAME_MIFARE_CLASSIC_BLOCK_TO_SECTOR:
                            return parseArgsMifareClassicBlockToSector(args);
                        case METHOD_NAME_MIFARE_CLASSIC_CONNECT:
                            return parseArgsMifareClassicConnect(args);
                        case METHOD_NAME_MIFARE_CLASSIC_DECREMENT:
                            return parseArgsMifareClassicDecrement(args);
                        case METHOD_NAME_MIFARE_CLASSIC_GET_BLOCK_COUNT:
                            return parseArgsMifareClassicGetBlockCount(args);
                        case METHOD_NAME_MIFARE_CLASSIC_GET_BLOCK_COUNT_IN_SECTOR:
                            return parseArgsMifareClassicGetBlockCountInSector(args);
                        case METHOD_NAME_MIFARE_CLASSIC_GET_MAX_TRANSCEIVE_LENGTH:
                            return parseArgsMifareClassicGetMaxTransceiveLength(args);
                        case METHOD_NAME_MIFARE_CLASSIC_GET_SECTOR_COUNT:
                            return parseArgsMifareClassicGetSectorCount(args);
                        case METHOD_NAME_MIFARE_CLASSIC_GET_SIZE:
                            return parseArgsMifareClassicGetSize(args);
                        case METHOD_NAME_MIFARE_CLASSIC_GET_TIMEOUT:
                            return parseArgsMifareClassicGetTimeout(args);
                        case METHOD_NAME_MIFARE_CLASSIC_GET_TYPE:
                            return parseArgsMifareClassicGetType(args);
                        case METHOD_NAME_MIFARE_CLASSIC_INCREMENT:
                            return parseArgsMifareClassicIncrement(args);
                        case METHOD_NAME_MIFARE_CLASSIC_READ_BLOCK:
                            return parseArgsMifareClassicReadBlock(args);
                        case METHOD_NAME_MIFARE_CLASSIC_RESTORE:
                            return parseArgsMifareClassicRestore(args);
                        case METHOD_NAME_MIFARE_CLASSIC_SECTOR_TO_BLOCK:
                            return parseArgsMifareClassicSectorToBlock(args);
                        case METHOD_NAME_MIFARE_CLASSIC_SET_TIMEOUT:
                            return parseArgsMifareClassicSetTimeout(args);
                        case METHOD_NAME_MIFARE_CLASSIC_TRANSCEIVE:
                            return parseArgsMifareClassicTransceive(args);
                        case METHOD_NAME_MIFARE_CLASSIC_TRANSFER:
                            return parseArgsMifareClassicTransfer(args);
                        case METHOD_NAME_MIFARE_CLASSIC_WRITE_BLOCK:
                            return parseArgsMifareClassicWriteBlock(args);
                        default:
                            throw new InvalidCommandException();
                    }
                }

                case CLASS_NAME_MIFARE_ULTRALIGHT: {
                    if (args.length < 2) {
                        throw new InvalidCommandException();
                    }
                    switch (args[1]) {
                        case METHOD_NAME_MIFARE_ULTRALIGHT_CONNECT:
                            return parseArgsMifareUltralightConnect(args);
                        case METHOD_NAME_MIFARE_ULTRALIGHT_GET_MAX_TRANSCEIVE_LENGTH:
                            return parseArgsMifareUltralightGetMaxTransceiveLength(args);
                        case METHOD_NAME_MIFARE_ULTRALIGHT_GET_TIMEOUT:
                            return parseArgsMifareUltralightGetTimeout(args);
                        case METHOD_NAME_MIFARE_ULTRALIGHT_GET_TYPE:
                            return parseArgsMifareUltralightGetType(args);
                        case METHOD_NAME_MIFARE_ULTRALIGHT_READ_PAGES:
                            return parseArgsMifareUltralightReadPages(args);
                        case METHOD_NAME_MIFARE_ULTRALIGHT_SET_TIMEOUT:
                            return parseArgsMifareUltralightSetTimeout(args);
                        case METHOD_NAME_MIFARE_ULTRALIGHT_TRANSCEIVE:
                            return parseArgsMifareUltralightTransceive(args);
                        case METHOD_NAME_MIFARE_ULTRALIGHT_WRITE_PAGE:
                            return parseArgsMifareUltralightWritePage(args);
                        default:
                            throw new InvalidCommandException();
                    }
                }

                case CLASS_NAME_NFC_BARCODE: {
                    if (args.length < 2) {
                        throw new InvalidCommandException();
                    }
                    switch (args[1]) {
                        case METHOD_NAME_NFC_BARCODE_CONNECT:
                            return parseArgsNfcBarcodeConnect(args);
                        case METHOD_NAME_NFC_BARCODE_GET_BARCODE:
                            return parseArgsNfcBarcodeGetBarcode(args);
                        case METHOD_NAME_NFC_BARCODE_GET_TYPE:
                            return parseArgsNfcBarcodeGetType(args);
                        default:
                            throw new InvalidCommandException();
                    }
                }

                case CLASS_NAME_NDEF_FORMATABLE: {
                    if (args.length < 2) {
                        throw new InvalidCommandException();
                    }
                    switch (args[1]) {
                        case METHOD_NAME_NDEF_FORMATABLE_CONNECT:
                            return parseArgsNdefFormatableConnect(args);
                        case METHOD_NAME_NDEF_FORMATABLE_FORMAT:
                            return parseArgsNdefFormatableFormat(args);
                        case METHOD_NAME_NDEF_FORMATABLE_FORMAT_READ_ONLY:
                            return parseArgsNdefFormatableFormatReadOnly(args);
                        default:
                            throw new InvalidCommandException();
                    }
                }

                default:
                    throw new InvalidCommandException();
            }

        }

        TagTechnologyClosure parseClosureFromIntent(Intent intent) throws ArgumentException {
            @NonNull String[] args = Utils.collectNumberedArgs(intent, "arg");
            return parseClosureFromArgs(args);
        }


        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            Logger.logDebug(LOG_TAG, "onCreate");
            super.onCreate(savedInstanceState);

            Intent intent = this.getIntent();
            if (intent == null) {
                finish();
                return;
            }

            assert intent.hasExtra("socket_input");
            assert intent.hasExtra("socket_output");

            try {
                mDelayedClosure = parseClosureFromIntent(intent);
            }
            catch (ArgumentException e) {
                postException(e);
                finish();
                return;
            }

            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
            if (adapter == null || !adapter.isEnabled()) {
                postException(new NfcUnavailableException());
                finish();
                return;
            }
            mAdapter = adapter;
        }

        @Override
        protected void onResume() {
            Logger.logVerbose(LOG_TAG, "onResume");
            super.onResume();

            // - https://developer.android.com/develop/connectivity/nfc/advanced-nfc#foreground-dispatch
            Intent intentNew = new Intent(this, NfcActivity.class).addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intentNew,
                    PendingIntentUtils.getPendingIntentMutableFlag());
            IntentFilter[] intentFilter = new IntentFilter[]{
                    new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
                    new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
                    new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)};
            mAdapter.enableForegroundDispatch(this, pendingIntent, intentFilter, null);
        }

        private void invokeClosure(TagTechnologyClosure closure) {
            try {
                CallResult result = closure.call();
                postResult(result);
            } catch (Exception e) {
                postException(e);
            }
        }

        private void consumeClosure() {
            assert mDelayedClosure != null;
            invokeClosure(mDelayedClosure);
            mDelayedClosure = null;
        }

        @Override
        protected void onNewIntent(Intent intent) {
            Logger.logDebug(LOG_TAG, "onNewIntent");
            super.onNewIntent(intent);

            if (isFinishing()) {
                postException(new ActivityFinishingException());
                return;
            }

            String action = intent.getAction();
            if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                    || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)
                    || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

                assert tag != null;
                mTag = tag;
                if (mDelayedClosure != null) {
                    consumeClosure();
                }
                return;
            }

            assert intent.hasExtra("socket_input");
            assert intent.hasExtra("socket_output");

            setIntent(intent);
            TagTechnologyClosure closure;
            try {
                closure = parseClosureFromIntent(intent);
            } catch (ArgumentException e) {
                postException(e);
                return;
            }
            invokeClosure(closure);
        }

        @Override
        protected void onPause() {
            Logger.logDebug(LOG_TAG, "onPause");
            mAdapter.disableForegroundDispatch(this);
            super.onPause();
        }

        @Override
        protected void onDestroy() {
            Logger.logDebug(LOG_TAG, "onDestroy");
            super.onDestroy();
        }

        void postResult(final CallResult result) {
            ResultReturner.returnData(getApplicationContext(), getIntent(), new ResultReturner.ResultJsonWriter() {
                @Override
                public void writeJson(JsonWriter out) throws Exception {
                    Logger.logDebug(LOG_TAG, "postResult");
                    out.beginObject();

                    JsonWriter resultWriter = out.name("result");
                    Object obj = result.mData;
                    if (obj == null) {
                        resultWriter.nullValue();
                    } else if (obj instanceof String) {
                        resultWriter.value((String)obj);
                    } else if (obj instanceof Integer) {
                        resultWriter.value((Integer)obj);
                    } else if (obj instanceof Boolean) {
                        resultWriter.value((Boolean)obj);
                    } else {
                        assert false : "Unexpected invalid result type: " + obj.getClass().getName();
                    }
                    
                    out.endObject();
                }
            });
        }

        void postException(final Exception e) {
            ResultReturner.returnData(getApplicationContext(), getIntent(), new ResultReturner.ResultJsonWriter() {
                @Override
                public void writeJson(JsonWriter out) throws Exception {
                    out.beginObject();
                    out.name("exceptionType").value(e.getClass().getSimpleName());
                    out.name("exceptionMessage").value(e.getMessage());
                    out.endObject();
                }
            });
        }
    }

}