package com.termux.api.apis;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
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
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import com.termux.api.R;
import com.termux.api.util.PendingIntentUtils;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

class NfcException extends RuntimeException {
    NfcException() {

    }

    NfcException(String message) {
        super(message);
    }
}

class UnsupportedTechnology extends NfcException {
}

class NoConnectionException extends NfcException {
}

class WrongTechnologyException extends NfcException {
    WrongTechnologyException(@NonNull String expected, @NonNull String found) {
        super(String.format("%s expected, but %s found", expected, found));
    }
}
class TagNullException extends NfcException {
}

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

class InvalidHexLengthException extends ArgumentException {
}

class InvalidCommandException extends ArgumentException {
}

class Utils {
    static <K0, K1, V> Map<K1, V> getOrCreate2dMap(@NonNull Map<K0, Map<K1, V>> map, @NonNull K0 key) {
        Map<K1, V> result = map.get(key);
        if (result != null) {
            return result;
        }

        result = new HashMap<>();
        map.put(key, result);
        return result;
    }

    static <K0, K1, V> @Nullable V get2dMap(@NonNull Map<K0, Map<K1, V>> map, @NonNull K0 k0, @NonNull K1 k1) {
        Map<K1, V> map1 = map.get(k0);
        if (map1 == null) {
            return null;
        }
        return map1.get(k1);
    }

    static Object invokePublicMethod(@NonNull Method method, @Nullable Object obj, @Nullable Object... args) throws Throwable {
        try {
            return method.invoke(obj, args);
        } catch (IllegalAccessException e) {
            assert false : "Unexpected exception: " + e;
            return null;
        }
    }

    static void printLnFlush(PrintWriter out, @NonNull String s) {
        out.println(s);
        out.flush();
    }

    static JSONObject throwableToJson(@NonNull Throwable e) {
        JSONObject obj = new JSONObject();
        try {
            obj.put(JSONConstant.KEY_OP_STATUS, JSONConstant.VALUE_OP_ERROR);
            obj.put(JSONConstant.KEY_OP_ERROR_TYPE, e.getClass().getName());
            obj.put(JSONConstant.KEY_OP_ERROR_MESSAGE, e.getMessage());
        } catch (JSONException ex) {
            assert false : ex;
        }
        return obj;
    }
    static void checkTechnologyNonNull(@Nullable TagTechnology technology) throws NoConnectionException {
        if (technology == null) {
            throw new NoConnectionException();
        }
    }

    static byte parseHexOne(char c) throws ArgumentException {
        if ('0' <= c && c <= '9')
            return (byte) ((byte) c - '0');
        if ('a' <= c && c <= 'f')
            return (byte) ((byte) c - 'a' + 10);
        if ('A' <= c && c <= 'F')
            return (byte) ((byte) c - 'A' + 10);
        throw new InvalidHexException(c);
    }

    static byte parseHexTwo(char high, char low) throws ArgumentException {
        return (byte) ((parseHexOne(high) << 4) | parseHexOne(low));
    }

    /**
     * Parse hex representation of byte[] array.
     *
     * @param hex the hex representation, e.g. "DEADBEEF"
     * @return the byte[] array, e.g. {0xde, 0xad, 0xbe, 0xef}.
     * @throws ArgumentException if it fails to parse.
     */
    static byte[] parseHex(@NonNull String hex) throws ArgumentException {
        if (hex.length() % 2 != 0 || hex.isEmpty()) {
            throw new InvalidHexLengthException();
        }

        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            result[i / 2] = parseHexTwo(hex.charAt(i), hex.charAt(i + 1));
        }

        return result;
    }

    static String formatHexOne(byte b) {
        return String.format("%02X", b);
    }

    /**
     * Format byte array into hex string, e.g. {0xde, 0xad, 0xbe, 0xef} -> "DEADBEEF"
     *
     * @param data the byte array to be formatted
     * @return the formatted hex representation
     */
    static String formatHex(@NonNull byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(Utils.formatHexOne(b));
        }
        return sb.toString();
    }
}

/**
 * Return value of wrapped NFC API call.
 */


class JSONConstant {
    static final String KEY_OP_STATUS = "status";
    static final String VALUE_OP_SUCCESS = "success";
    static final String KEY_OP_RESULT = "result";
    static final String VALUE_OP_ERROR = "error";
    static final String KEY_OP_ERROR_TYPE = "exceptionType";
    static final String KEY_OP_ERROR_MESSAGE = "exceptionMessage";

    static final String KEY_OP_NAME = "op";
    static final String VALUE_OP_API = "api";
    static final String VALUE_OP_DISCOVER_TAG = "discoverTag";

    static final String KEY_CLASS_NAME = "class";
    static final String KEY_METHOD_NAME = "method";
    static final String KEY_ARGS = "args";

    static final String KEY_BYTES_FORMAT = "format";
    static final String VALUE_BYTES_FORMAT_HEX = "hex";
    static final String VALUE_BYTES_FORMAT_RAW = "raw";

    static final String KEY_BYTES_VALUE = "value";

    static final String KEY_TAG_ID = "id";
    static final String KEY_TAG_TECH_LIST = "techList";

    static final String KEY_NDEF_RECORD_ID = "id";
    static final String KEY_NDEF_RECORD_PAYLOAD = "payload";
    static final String KEY_NDEF_RECORD_TNF = "tnf";
    static final String KEY_NDEF_RECORD_TYPE = "type";
}

class MaybeVoidResult {
    final boolean mIsVoid;
    final Object mResult;

    MaybeVoidResult(boolean isVoid, Object result) {
        assert (!isVoid) || (result == null);
        mIsVoid = isVoid;
        mResult = result;
    }

    static MaybeVoidResult theVoid() {
        return new MaybeVoidResult(true, null);
    }

    static MaybeVoidResult nonVoid(@Nullable Object result) {
        return new MaybeVoidResult(false, result);
    }

    JSONObject toJson() {
        assert (!mIsVoid) || (mResult == null);
        assert mResult == null || mResult instanceof String || mResult instanceof Integer || mResult instanceof Boolean || mResult instanceof JSONObject || mResult instanceof JSONArray : mResult.getClass().getName();

        JSONObject obj = new JSONObject();
        try {
            obj.put(JSONConstant.KEY_OP_STATUS, JSONConstant.VALUE_OP_SUCCESS);
            if (!mIsVoid) {
                obj.put(JSONConstant.KEY_OP_RESULT, mResult == null ? JSONObject.NULL : mResult);
            }
        } catch (JSONException e) {
            assert false : "Unexpected JSON exception: " + e;
        }
        return obj;
    }
}

interface ApiInvoker {
    // no argument, return JSON type
    MaybeVoidResult invoke(@NonNull Object[] args) throws Throwable;
}

class Parser {
    interface t {
        // parse JSON types to Java types
        Object parse(Object arg) throws ArgumentException;
    }

    static private <T> T checkedCast(Object obj, Class<T> cls) {
        if (cls.isInstance(obj)) {
            return (T) obj;
        }
        throw new ArgumentException();
    }

    static private Boolean parseBoolean(Object arg) throws ArgumentException {
        return checkedCast(arg, Boolean.class);
    }

    static private Short parseShort(Object arg) throws ArgumentException {
        int val = checkedCast(arg, Integer.class);
        if (!(Short.MIN_VALUE <= val && val <= Short.MAX_VALUE)) {
            throw new ArgumentException();
        }

        return (short) val;
    }

    static private Integer parseInt(Object arg) throws ArgumentException {
        return checkedCast(arg, Integer.class);
    }

    static private byte[] parseByteArray(Object arg) throws ArgumentException {
        JSONObject jsonObject = checkedCast(arg, JSONObject.class);
        String format;
        String value;

        try {
            format = jsonObject.getString(JSONConstant.KEY_BYTES_FORMAT);
            value = jsonObject.getString(JSONConstant.KEY_BYTES_VALUE);
        } catch (JSONException e) {
            throw new ArgumentException(e.getMessage());
        }

        switch (format) {
            case JSONConstant.VALUE_BYTES_FORMAT_HEX:
                return Utils.parseHex(value);
            case JSONConstant.VALUE_BYTES_FORMAT_RAW:
                return value.getBytes(StandardCharsets.ISO_8859_1);
            default:
                throw new ArgumentException();
        }
    }

    static private NdefMessage parseNdefMessage(Object arg) throws ArgumentException {
        byte[] bytes = parseByteArray(arg);
        try {
            return new NdefMessage(bytes);
        } catch (FormatException e) {
            throw new ArgumentException(e.getMessage());
        }
    }

    static t getArgParser(@NonNull Class<?> argType) {
        if (argType == boolean.class) {
            return Parser::parseBoolean;
        }
        if (argType == short.class) {
            return Parser::parseShort;
        }
        if (argType == int.class) {
            return Parser::parseInt;
        }
        if (argType == byte[].class) {
            return Parser::parseByteArray;
        }
        if (argType == NdefMessage.class) {
            return Parser::parseNdefMessage;
        }

        assert false : "unexpected arg type: " + argType.getName();
        return null;
    }
}


class Formatter {
    interface t {
        @Nullable
        Object format(Object ret);
    }

    static Integer formatInt(int ret) {
        return ret;
    }

    static Integer formatByte(byte ret) {
        return (int) ret;
    }

    static Integer formatShort(short ret) {
        return (int) ret;
    }

    static Boolean formatBoolean(boolean ret) {
        return ret;
    }

    static JSONObject formatByteArray(@NonNull byte[] ret) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(JSONConstant.KEY_BYTES_FORMAT, JSONConstant.VALUE_BYTES_FORMAT_HEX);
            jsonObject.put(JSONConstant.KEY_BYTES_VALUE, Utils.formatHex(ret));
        } catch (JSONException e) {
            assert false : e.getMessage();
        }
        return jsonObject;
    }

    static JSONObject formatTag(@NonNull Tag ret) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(JSONConstant.KEY_TAG_ID, formatByteArray(ret.getId()));
            jsonObject.put(JSONConstant.KEY_TAG_TECH_LIST, new JSONArray(ret.getTechList()));
        } catch (JSONException e) {
            assert false : e.getMessage();
        }
        return jsonObject;
    }

    static String formatTnf(short tnf) {
        switch (tnf) {
            case NdefRecord.TNF_EMPTY:
                return "TNF_EMPTY";
            case NdefRecord.TNF_WELL_KNOWN:
                return "TNF_WELL_KNOWN";
            case NdefRecord.TNF_MIME_MEDIA:
                return "TNF_MIME_MEDIA";
            case NdefRecord.TNF_ABSOLUTE_URI:
                return "TNF_ABSOLUTE_URI";
            case NdefRecord.TNF_EXTERNAL_TYPE:
                return "TNF_EXTERNAL_TYPE";
            case NdefRecord.TNF_UNKNOWN:
                return "TNF_UNKNOWN";
            case NdefRecord.TNF_UNCHANGED:
                return "TNF_UNCHANGED";
            default:
                return "Unexpected TNF: " + tnf;
        }
    }

    static JSONObject formatNdefRecord(@NonNull NdefRecord ret) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(JSONConstant.KEY_NDEF_RECORD_ID, formatByteArray(ret.getId()));
            jsonObject.put(JSONConstant.KEY_NDEF_RECORD_PAYLOAD, formatByteArray(ret.getPayload()));
            jsonObject.put(JSONConstant.KEY_NDEF_RECORD_TNF, formatTnf(ret.getTnf()));
            jsonObject.put(JSONConstant.KEY_NDEF_RECORD_TYPE, formatByteArray(ret.getType()));
        } catch (JSONException e) {
            assert false : e.getMessage();
        }
        return jsonObject;
    }

    static JSONArray formatNdefMessage(@NonNull NdefMessage ret) {
        JSONArray jsonArray = new JSONArray();
        for (NdefRecord record : ret.getRecords()) {
            jsonArray.put(formatNdefRecord(record));
        }
        return jsonArray;
    }

    static String formatMifareClassicSize(int size) {
        switch (size) {
            case MifareClassic.SIZE_1K:
                return "SIZE_1K";
            case MifareClassic.SIZE_2K:
                return "SIZE_2K";
            case MifareClassic.SIZE_4K:
                return "SIZE_4K";
            case MifareClassic.SIZE_MINI:
                return "SIZE_MINI";
            default:
                return "Unexpected size: " + size;
        }
    }

    static String formatMifareClassicType(int type) {
        switch (type) {
            case MifareClassic.TYPE_CLASSIC:
                return "TYPE_CLASSIC";
            case MifareClassic.TYPE_PLUS:
                return "TYPE_PLUS";
            case MifareClassic.TYPE_PRO:
                return "TYPE_PRO";
            case MifareClassic.TYPE_UNKNOWN:
                return "TYPE_UNKNOWN";
            default:
                return "Unexpected type: " + type;
        }
    }

    static String formatMifareUltralightType(int type) {
        switch (type) {
            case MifareUltralight.TYPE_ULTRALIGHT:
                return "TYPE_ULTRALIGHT";
            case MifareUltralight.TYPE_ULTRALIGHT_C:
                return "TYPE_ULTRAILGHT_C";
            case MifareUltralight.TYPE_UNKNOWN:
                return "TYPE_UNKNOWN";
            default:
                return "Unexpected type: " + type;
        }
    }

    static String formatNfcBarcodeType(int type) {
        switch (type) {
            case NfcBarcode.TYPE_KOVIO:
                return "TYPE_KOVIO";
            case NfcBarcode.TYPE_UNKNOWN:
                return "TYPE_UNKNOWN";
            default:
                return "Unexpected type: " + type;
        }
    }

    static @NonNull t getDefaultFormatter(Class<?> retType) {
        assert retType != void.class;

        if (retType == int.class) {
            return ret -> formatInt((Integer) ret);
        }
        if (retType == byte.class) {
            return ret -> formatByte((Byte) ret);
        }
        if (retType == short.class) {
            return ret -> formatShort((Short) ret);
        }
        if (retType == boolean.class) {
            return ret -> formatBoolean((Boolean) ret);
        }
        if (retType == byte[].class) {
            return ret -> formatByteArray((byte[]) ret);
        }
        if (retType == Tag.class) {
            return ret -> formatTag((Tag) ret);
        }
        if (retType == NdefMessage.class) {
            return ret -> formatNdefMessage((NdefMessage) ret);
        }
        if (retType == String.class) {
            return ret -> ret;
        }

        assert false : "Unexpected return type: " + retType.getName();
        return null;
    }
}

class NfcManager {
    // The result of connect()
    private @Nullable TagTechnology mTechnology;

    // The last discovered tag
    private Tag mTag;
    private final Semaphore mTagSemaphore;

    private final Activity mActivity;

    private final Intent mIntent;

    private final Map<String, Map<String, ApiInvoker>> mInvokerMap;
    private final Map<String, Map<String, Method>> mMethodMap;

    NfcManager(Activity activity, Intent intent) {
        mTag = null;
        mTechnology = null;
        mActivity = activity;
        mIntent = intent;
        mTagSemaphore = new Semaphore(0);

        mMethodMap = new HashMap<>();
        populateMethods();

        mInvokerMap = new HashMap<>();
        populateInvokers();
    }

    // helper function for subclasses' connect()
    private void callTagTechnologyConnect(@NonNull Method techGetMethod, @NonNull Method techConnectMethod, @NonNull Object[] args) throws Throwable {
        if (args.length != 0) {
            throw new ArgNumberException(0, args.length);
        }

        if (mTag == null) {
            throw new TagNullException();
        }

        mTechnology = (TagTechnology) Utils.invokePublicMethod(techGetMethod, null, mTag);
        if (mTechnology == null) {
            throw new UnsupportedTechnology();
        }

        Utils.invokePublicMethod(techConnectMethod, mTechnology);
    }

    private void discoverTag() throws ArgumentException, InterruptedException {
        clearTag();

        Intent intent = new Intent(mActivity, NfcAPI.NfcActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        mActivity.startActivity(intent);

        mTagSemaphore.acquire();
        assert mTag != null;

        mActivity.runOnUiThread(() -> mActivity.moveTaskToBack(true));
    }

    private MaybeVoidResult invokeDiscoverTag() throws ArgumentException, InterruptedException {
        discoverTag();
        assert mTag != null;
        return MaybeVoidResult.nonVoid(Formatter.formatTag(mTag));
    }

    MaybeVoidResult invokeByLine(@NonNull String line) throws Throwable {
        JSONObject jsonObject;
        String operationName;

        try {
            jsonObject = new JSONObject(line);
            operationName = jsonObject.getString(JSONConstant.KEY_OP_NAME);
        } catch (JSONException e) {
            throw new ArgumentException(e.toString());
        }

        try {
            switch (operationName) {
                case JSONConstant.VALUE_OP_DISCOVER_TAG:
                    return invokeDiscoverTag();
                case JSONConstant.VALUE_OP_API:
                    return invokeJsonApi(jsonObject);
                default:
                    throw new InvalidCommandException();
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw cause==null ? e : cause;
        }
    }

    MaybeVoidResult invokeJsonApi(@NonNull JSONObject jsonObject) throws Throwable {
        String className;
        String methodName;
        JSONArray jsonArray;

        try {
            className = jsonObject.getString(JSONConstant.KEY_CLASS_NAME);
            methodName = jsonObject.getString(JSONConstant.KEY_METHOD_NAME);
            jsonArray = jsonObject.getJSONArray(JSONConstant.KEY_ARGS);
        } catch (JSONException e) {
            throw new ArgumentException(e.getMessage());
        }

        Object[] args = new Object[jsonArray.length()];
        try {
            for (int i = 0; i < args.length; ++i) {
                args[i] = jsonArray.get(i);
            }
        } catch (JSONException e) {
            assert false : e.getMessage();
        }

        return getInvoker(className, methodName).invoke(args);
    }

    ApiInvoker getInvoker(@NonNull String className, @NonNull String methodName) throws InvalidCommandException {
        ApiInvoker invoker = Utils.get2dMap(mInvokerMap, className, methodName);
        if (invoker == null) {
            throw new InvalidCommandException();
        }
        return invoker;
    }

    void listenAsync() {
        ResultReturner.returnData(mActivity, mIntent, new ResultReturner.WithInput() {
            @Override
            public void writeResult(PrintWriter out) throws Exception {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String line;
                while ((line = reader.readLine()) != null) {
                    MaybeVoidResult result;
                    try {
                        result = invokeByLine(line);
                    } catch (Throwable e) {
                        Utils.printLnFlush(out, Utils.throwableToJson(e).toString());
                        continue;
                    }

                    Utils.printLnFlush(out, result.toJson().toString());
                }

                mActivity.runOnUiThread(mActivity::finishAndRemoveTask);
            }
        });
    }

    synchronized void setTag(@NonNull Tag tag) {
        mTag = tag;
        mTagSemaphore.release();
    }

    private synchronized void clearTag() {
        mTag = null;
        mTagSemaphore.drainPermits();
    }

    MaybeVoidResult invokeWithArgs(@NonNull Class<? extends TagTechnology> cls, @NonNull Method method, @Nullable Formatter.t retFormatter, @NonNull Parser.t[] argParsers, @NonNull Object[] args) throws Throwable {
        Utils.checkTechnologyNonNull(mTechnology);

        if (!cls.isInstance(mTechnology)) {
            throw new WrongTechnologyException(cls.getSimpleName(), mTechnology.getClass().getSimpleName());
        }

        int nArgs = args.length;
        if (nArgs != argParsers.length) {
            throw new ArgNumberException(argParsers.length, nArgs);
        }

        Object[] parsedArgs = new Object[nArgs];
        for (int i = 0; i < nArgs; ++i) {
            parsedArgs[i] = argParsers[i].parse(args[i]);
        }

        Object result;
        try {
            result = method.invoke(mTechnology, parsedArgs);
        } catch (IllegalAccessException e) {
            assert false : e.getMessage();
            return null;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                throw cause;
            } else {
                throw e;
            }
        }

        if (retFormatter == null) {
            return MaybeVoidResult.theVoid();
        } else {
            return MaybeVoidResult.nonVoid(retFormatter.format(result));
        }
    }

    ApiInvoker newDefaultInvoker(@NonNull Class<? extends TagTechnology> cls, @NonNull Method method, @Nullable Formatter.t retFormatter, @NonNull Parser.t... argParsers) {
        assert method.getParameterCount() == argParsers.length;
        assert TagTechnology.class.isAssignableFrom(method.getDeclaringClass());

        return args -> invokeWithArgs(cls, method, retFormatter, argParsers, args);
    }

    static Parser.t[] getArgParsers(Class<?>[] parameterTypes) {
        Parser.t[] argParsers = new Parser.t[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; ++i) {
            argParsers[i] = Parser.getArgParser(parameterTypes[i]);
        }
        return argParsers;
    }

    @NonNull Method getMethod(Class<? extends TagTechnology> cls, @NonNull String methodName) {
        Method method = Utils.get2dMap(mMethodMap, cls.getSimpleName(), methodName);
        assert method != null : String.format("Method %s not found in %s", methodName, cls.getName());
        return method;
    }

    void addInvokerByName(Class<? extends TagTechnology> cls, @NonNull String methodName) {
        Method method = getMethod(cls, methodName);
        Class<?> retType = method.getReturnType();
        Formatter.t retFormatter = retType==void.class ? null : Formatter.getDefaultFormatter(retType);
        Parser.t[] argParsers = getArgParsers(method.getParameterTypes());
        addInvokerBySig(cls, method, retFormatter, argParsers);
    }

    void addInvokerBySig(@NonNull Class<? extends TagTechnology> cls, @NonNull String methodName, @Nullable Formatter.t retFormatter, @NonNull Parser.t... argParsers) {
        addInvokerBySig(cls, getMethod(cls, methodName), retFormatter, argParsers);
    }

    void addInvokerBySig(@NonNull Class<? extends TagTechnology> cls, @NonNull Method method, @Nullable Formatter.t retFormatter, @NonNull Parser.t... argParsers) {
        assert (method.getReturnType() == void.class) == (retFormatter == null);
        addInvoker(cls, method.getName(), newDefaultInvoker(cls, method, retFormatter, argParsers));
    }

    void addInvoker(@NonNull Class<? extends TagTechnology> cls, @NonNull String methodName, @NonNull ApiInvoker invoker) {
        Utils.getOrCreate2dMap(mInvokerMap, cls.getSimpleName()).put(methodName, invoker);
    }

    static final String METHOD_NAME_GET = "get";
    static final String METHOD_NAME_CONNECT = "connect";

    void addConnectInvoker(@NonNull Class<? extends TagTechnology> cls) {
        Method techGetMethod = getMethod(cls, METHOD_NAME_GET);
        Method techConnectMethod = getMethod(cls, METHOD_NAME_CONNECT);

        addInvoker(cls, METHOD_NAME_CONNECT, args -> {
            callTagTechnologyConnect(techGetMethod, techConnectMethod, args);
            return MaybeVoidResult.theVoid();
        });
    }

    void populateClassMethods(Class<? extends TagTechnology> cls) {
        Map<String, Method> map = Utils.getOrCreate2dMap(mMethodMap, cls.getSimpleName());
        for (Method method : cls.getDeclaredMethods()) {
            map.put(method.getName(), method);
        }
    }

    void populateMethods() {
        populateClassMethods(TagTechnology.class);
        populateClassMethods(NfcA.class);
        populateClassMethods(NfcB.class);
        populateClassMethods(NfcF.class);
        populateClassMethods(NfcV.class);
        populateClassMethods(IsoDep.class);
        populateClassMethods(Ndef.class);
        populateClassMethods(MifareClassic.class);
        populateClassMethods(MifareUltralight.class);
        populateClassMethods(NfcBarcode.class);
        populateClassMethods(NdefFormatable.class);
    }

    void populateInvokers() {
        addInvokerByName(TagTechnology.class, "isConnected");
        addInvokerByName(TagTechnology.class, "close");
        addInvokerByName(TagTechnology.class, "getTag");

        addConnectInvoker(NfcA.class);
        addInvokerByName(NfcA.class, "getAtqa");
        addInvokerByName(NfcA.class, "getMaxTransceiveLength");
        addInvokerByName(NfcA.class, "getSak");
        addInvokerByName(NfcA.class, "getTimeout");
        addInvokerByName(NfcA.class, "setTimeout");
        addInvokerByName(NfcA.class, "transceive");

        addConnectInvoker(NfcB.class);
        addInvokerByName(NfcB.class, "getApplicationData");
        addInvokerByName(NfcB.class, "getMaxTransceiveLength");
        addInvokerByName(NfcB.class, "getProtocolInfo");
        addInvokerByName(NfcB.class, "transceive");

        addConnectInvoker(NfcF.class);
        addInvokerByName(NfcF.class, "getManufacturer");
        addInvokerByName(NfcF.class, "getMaxTransceiveLength");
        addInvokerByName(NfcF.class, "getSystemCode");
        addInvokerByName(NfcF.class, "getTimeout");
        addInvokerByName(NfcF.class, "setTimeout");
        addInvokerByName(NfcF.class, "transceive");

        addConnectInvoker(NfcV.class);
        addInvokerByName(NfcV.class, "getDsfId");
        addInvokerByName(NfcV.class, "getMaxTransceiveLength");
        addInvokerByName(NfcV.class, "getResponseFlags");
        addInvokerByName(NfcV.class, "transceive");

        addConnectInvoker(IsoDep.class);
        addInvokerByName(IsoDep.class, "getHiLayerResponse");
        addInvokerByName(IsoDep.class, "getHistoricalBytes");
        addInvokerByName(IsoDep.class, "getMaxTransceiveLength");
        addInvokerByName(IsoDep.class, "getTimeout");
        addInvokerByName(IsoDep.class, "isExtendedLengthApduSupported");
        addInvokerByName(IsoDep.class, "setTimeout");
        addInvokerByName(IsoDep.class, "transceive");

        addConnectInvoker(Ndef.class);
        addInvokerByName(Ndef.class, "canMakeReadOnly");
        addInvokerByName(Ndef.class, "getCachedNdefMessage");
        addInvokerByName(Ndef.class, "getMaxSize");
        addInvokerByName(Ndef.class, "getNdefMessage");
        addInvokerByName(Ndef.class, "getType");
        addInvokerByName(Ndef.class, "isWritable");
        addInvokerByName(Ndef.class, "makeReadOnly");
        addInvokerByName(Ndef.class, "writeNdefMessage");

        addConnectInvoker(MifareClassic.class);
        addInvokerByName(MifareClassic.class, "authenticateSectorWithKeyA");
        addInvokerByName(MifareClassic.class, "authenticateSectorWithKeyB");
        addInvokerByName(MifareClassic.class, "blockToSector");
        addInvokerByName(MifareClassic.class, "decrement");
        addInvokerByName(MifareClassic.class, "getBlockCount");
        addInvokerByName(MifareClassic.class, "getBlockCountInSector");
        addInvokerByName(MifareClassic.class, "getMaxTransceiveLength");
        addInvokerByName(MifareClassic.class, "getSectorCount");
        addInvokerBySig(MifareClassic.class, "getSize", ret -> Formatter.formatMifareClassicSize((Integer) ret));
        addInvokerByName(MifareClassic.class, "getTimeout");
        addInvokerBySig(MifareClassic.class, "getType", ret -> Formatter.formatMifareClassicType((Integer) ret));
        addInvokerByName(MifareClassic.class, "increment");
        addInvokerByName(MifareClassic.class, "readBlock");
        addInvokerByName(MifareClassic.class, "restore");
        addInvokerByName(MifareClassic.class, "sectorToBlock");
        addInvokerByName(MifareClassic.class, "setTimeout");
        addInvokerByName(MifareClassic.class, "transceive");
        addInvokerByName(MifareClassic.class, "transfer");
        addInvokerByName(MifareClassic.class, "writeBlock");

        addConnectInvoker(MifareUltralight.class);
        addInvokerByName(MifareUltralight.class, "getMaxTransceiveLength");
        addInvokerByName(MifareUltralight.class, "getTimeout");
        addInvokerBySig(MifareUltralight.class, "getType", ret -> Formatter.formatMifareUltralightType((Integer) ret));
        addInvokerByName(MifareUltralight.class, "readPages");
        addInvokerByName(MifareUltralight.class, "setTimeout");
        addInvokerByName(MifareUltralight.class, "transceive");
        addInvokerByName(MifareUltralight.class, "writePage");

        addConnectInvoker(NfcBarcode.class);
        addInvokerByName(NfcBarcode.class, "getBarcode");
        addInvokerBySig(NfcBarcode.class, "getType", ret -> Formatter.formatNfcBarcodeType((Integer) ret));

        addConnectInvoker(NdefFormatable.class);
        addInvokerByName(NdefFormatable.class, "format");
        addInvokerByName(NdefFormatable.class, "formatReadOnly");
    }
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
        private static final String LOG_TAG = "NfcActivity";
        NfcManager mNfcManager;
        NfcAdapter mAdapter;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            Logger.logDebug(LOG_TAG, "onCreate");
            super.onCreate(savedInstanceState);

            View view = new View(this);
            Drawable drawable = AppCompatResources.getDrawable(this, R.drawable.ic_nfc_black_24dp);
            view.setBackground(drawable);
            getWindow().getDecorView().setBackgroundColor(Color.argb(128, 255, 255, 255));
            setContentView(view);
            moveTaskToBack(true);

            Intent intent = this.getIntent();
            if (intent == null) {
                finish();
                return;
            }

            assert intent.hasExtra("socket_input");
            assert intent.hasExtra("socket_output");

            mAdapter = NfcAdapter.getDefaultAdapter(this);
            if (mAdapter == null || !mAdapter.isEnabled()) {
                finish();
                return;
            }

            mNfcManager = new NfcManager(this, intent);
            mNfcManager.listenAsync();
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

        @Override
        protected void onNewIntent(Intent intent) {
            Logger.logDebug(LOG_TAG, "onNewIntent");
            super.onNewIntent(intent);

            String action = intent.getAction();
            if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                    || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)
                    || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                assert tag != null;
                mNfcManager.setTag(tag);
            }
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
    }
}