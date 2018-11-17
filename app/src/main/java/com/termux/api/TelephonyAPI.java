package com.termux.api;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;
import android.util.JsonWriter;
import android.util.Log;

import com.termux.api.util.ResultReturner;

import org.json.JSONObject;

import java.io.IOException;

/**
 * Exposing {@link android.telephony.TelephonyManager}.
 */
public class TelephonyAPI {

    private static void writeIfKnown(JsonWriter out, String name, int value) throws IOException {
        if (value != Integer.MAX_VALUE) out.name(name).value(value);
    }

    static void onReceiveTelephonyCellInfo(final Context context) {
        ResultReturner.returnData(context, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                out.beginArray();

                for (CellInfo cellInfo : manager.getAllCellInfo()) {
                    out.beginObject();
                    if (cellInfo instanceof CellInfoGsm) {
                        CellInfoGsm gsmInfo = (CellInfoGsm) cellInfo;
                        out.name("type").value("gsm");
                        out.name("registered").value(cellInfo.isRegistered());

                        out.name("asu").value(gsmInfo.getCellSignalStrength().getAsuLevel());
                        writeIfKnown(out, "dbm", gsmInfo.getCellSignalStrength().getDbm());
                        out.name("level").value(gsmInfo.getCellSignalStrength().getLevel());

                        writeIfKnown(out, "cid", gsmInfo.getCellIdentity().getCid());
                        writeIfKnown(out, "lac", gsmInfo.getCellIdentity().getLac());
                        writeIfKnown(out, "mcc", gsmInfo.getCellIdentity().getMcc());
                        writeIfKnown(out, "mnc", gsmInfo.getCellIdentity().getMnc());
                    } else if (cellInfo instanceof CellInfoLte) {
                        CellInfoLte lteInfo = (CellInfoLte) cellInfo;
                        out.name("type").value("lte");
                        out.name("registered").value(cellInfo.isRegistered());

                        out.name("asu").value(lteInfo.getCellSignalStrength().getAsuLevel());
                        out.name("dbm").value(lteInfo.getCellSignalStrength().getDbm());
                        writeIfKnown(out, "level", lteInfo.getCellSignalStrength().getLevel());
                        writeIfKnown(out, "timing_advance", lteInfo.getCellSignalStrength().getTimingAdvance());

                        writeIfKnown(out, "ci", lteInfo.getCellIdentity().getCi());
                        writeIfKnown(out, "pci", lteInfo.getCellIdentity().getPci());
                        writeIfKnown(out, "tac", lteInfo.getCellIdentity().getTac());
                        writeIfKnown(out, "mcc", lteInfo.getCellIdentity().getMcc());
                        writeIfKnown(out, "mnc", lteInfo.getCellIdentity().getMnc());
                    } else if (cellInfo instanceof CellInfoCdma) {
                        CellInfoCdma cdmaInfo = (CellInfoCdma) cellInfo;
                        out.name("type").value("cdma");
                        out.name("registered").value(cellInfo.isRegistered());

                        out.name("asu").value(cdmaInfo.getCellSignalStrength().getAsuLevel());
                        out.name("dbm").value(cdmaInfo.getCellSignalStrength().getDbm());
                        out.name("level").value(cdmaInfo.getCellSignalStrength().getLevel());
                        out.name("cdma_dbm").value(cdmaInfo.getCellSignalStrength().getCdmaDbm());
                        out.name("cdma_ecio").value(cdmaInfo.getCellSignalStrength().getCdmaEcio());
                        out.name("cdma_level").value(cdmaInfo.getCellSignalStrength().getCdmaLevel());
                        out.name("evdo_dbm").value(cdmaInfo.getCellSignalStrength().getEvdoDbm());
                        out.name("evdo_ecio").value(cdmaInfo.getCellSignalStrength().getEvdoEcio());
                        out.name("evdo_level").value(cdmaInfo.getCellSignalStrength().getEvdoLevel());
                        out.name("evdo_snr").value(cdmaInfo.getCellSignalStrength().getEvdoSnr());

                        out.name("basestation").value(cdmaInfo.getCellIdentity().getBasestationId());
                        out.name("latitude").value(cdmaInfo.getCellIdentity().getLatitude());
                        out.name("longitude").value(cdmaInfo.getCellIdentity().getLongitude());
                        out.name("network").value(cdmaInfo.getCellIdentity().getNetworkId());
                        out.name("system").value(cdmaInfo.getCellIdentity().getSystemId());
                    } else if (cellInfo instanceof CellInfoWcdma) {
                        CellInfoWcdma wcdmaInfo = (CellInfoWcdma) cellInfo;
                        out.name("type").value("wcdma");
                        out.name("registered").value(cellInfo.isRegistered());

                        out.name("asu").value(wcdmaInfo.getCellSignalStrength().getAsuLevel());
                        writeIfKnown(out, "dbm", wcdmaInfo.getCellSignalStrength().getDbm());
                        out.name("level").value(wcdmaInfo.getCellSignalStrength().getLevel());

                        writeIfKnown(out, "cid", wcdmaInfo.getCellIdentity().getCid());
                        writeIfKnown(out, "lac", wcdmaInfo.getCellIdentity().getLac());
                        writeIfKnown(out, "mcc", wcdmaInfo.getCellIdentity().getMcc());
                        writeIfKnown(out, "mnc", wcdmaInfo.getCellIdentity().getMnc());
                        writeIfKnown(out, "psc", wcdmaInfo.getCellIdentity().getPsc());
                    }
                    out.endObject();
                }

                out.endArray();
            }
        });
    }


    static void onReceiveTelephonyDeviceInfo(final Context context) {
        ResultReturner.returnData(context, new ResultReturner.ResultJsonWriter() {
            @SuppressLint("HardwareIds")
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                out.beginObject();

                {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        out.name("data_enabled").value(Boolean.toString(manager.isDataEnabled()));
                    }

                    int dataActivity = manager.getDataActivity();
                    String dataActivityString;
                    switch (dataActivity) {
                        case TelephonyManager.DATA_ACTIVITY_NONE:
                            dataActivityString = "none";
                            break;
                        case TelephonyManager.DATA_ACTIVITY_IN:
                            dataActivityString = "in";
                            break;
                        case TelephonyManager.DATA_ACTIVITY_OUT:
                            dataActivityString = "out";
                            break;
                        case TelephonyManager.DATA_ACTIVITY_INOUT:
                            dataActivityString = "inout";
                            break;
                        case TelephonyManager.DATA_ACTIVITY_DORMANT:
                            dataActivityString = "dormant";
                            break;
                        default:
                            dataActivityString = Integer.toString(dataActivity);
                            break;
                    }
                    out.name("data_activity").value(dataActivityString);

                    int dataState = manager.getDataState();
                    String dataStateString;
                    switch (dataState) {
                        case TelephonyManager.DATA_DISCONNECTED:
                            dataStateString = "disconnected";
                            break;
                        case TelephonyManager.DATA_CONNECTING:
                            dataStateString = "connecting";
                            break;
                        case TelephonyManager.DATA_CONNECTED:
                            dataStateString = "connected";
                            break;
                        case TelephonyManager.DATA_SUSPENDED:
                            dataStateString = "suspended";
                            break;
                        default:
                            dataStateString = Integer.toString(dataState);
                            break;
                    }
                    out.name("data_state").value(dataStateString);

                    out.name("device_id").value(manager.getDeviceId());
                    out.name("device_software_version").value(manager.getDeviceSoftwareVersion());

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        out.name("phone_count").value(manager.getPhoneCount());
                    }
                    int phoneType = manager.getPhoneType();
                    String phoneTypeString;
                    switch (phoneType) {
                        case TelephonyManager.PHONE_TYPE_CDMA:
                            phoneTypeString = "cdma";
                            break;
                        case TelephonyManager.PHONE_TYPE_GSM:
                            phoneTypeString = "gsm";
                            break;
                        case TelephonyManager.PHONE_TYPE_NONE:
                            phoneTypeString = "none";
                            break;
                        case TelephonyManager.PHONE_TYPE_SIP:
                            phoneTypeString = "sip";
                            break;
                        default:
                            phoneTypeString = Integer.toString(phoneType);
                            break;
                    }
                    out.name("phone_type").value(phoneTypeString);

                    out.name("network_operator").value(manager.getNetworkOperator());
                    out.name("network_operator_name").value(manager.getNetworkOperatorName());
                    out.name("network_country_iso").value(manager.getNetworkCountryIso());
                    int networkType = manager.getNetworkType();
                    String networkTypeName;
                    switch (networkType) {
                        case TelephonyManager.NETWORK_TYPE_1xRTT:
                            networkTypeName = "1xrtt";
                            break;
                        case TelephonyManager.NETWORK_TYPE_CDMA:
                            networkTypeName = "cdma";
                            break;
                        case TelephonyManager.NETWORK_TYPE_EDGE:
                            networkTypeName = "edge";
                            break;
                        case TelephonyManager.NETWORK_TYPE_EHRPD:
                            networkTypeName = "ehrpd";
                            break;
                        case TelephonyManager.NETWORK_TYPE_EVDO_0:
                            networkTypeName = "evdo_0";
                            break;
                        case TelephonyManager.NETWORK_TYPE_EVDO_A:
                            networkTypeName = "evdo_a";
                            break;
                        case TelephonyManager.NETWORK_TYPE_EVDO_B:
                            networkTypeName = "evdo_b";
                            break;
                        case TelephonyManager.NETWORK_TYPE_GPRS:
                            networkTypeName = "gprs";
                            break;
                        case TelephonyManager.NETWORK_TYPE_HSDPA:
                            networkTypeName = "hdspa";
                            break;
                        case TelephonyManager.NETWORK_TYPE_HSPA:
                            networkTypeName = "hspa";
                            break;
                        case TelephonyManager.NETWORK_TYPE_HSPAP:
                            networkTypeName = "hspap";
                            break;
                        case TelephonyManager.NETWORK_TYPE_HSUPA:
                            networkTypeName = "hsupa";
                            break;
                        case TelephonyManager.NETWORK_TYPE_IDEN:
                            networkTypeName = "iden";
                            break;
                        case TelephonyManager.NETWORK_TYPE_LTE:
                            networkTypeName = "lte";
                            break;
                        case TelephonyManager.NETWORK_TYPE_UMTS:
                            networkTypeName = "umts";
                            break;
                        case TelephonyManager.NETWORK_TYPE_UNKNOWN:
                            networkTypeName = "unknown";
                            break;
                        default:
                            networkTypeName = Integer.toString(networkType);
                            break;
                    }
                    out.name("network_type").value(networkTypeName);
                    out.name("network_roaming").value(manager.isNetworkRoaming());

                    out.name("sim_country_iso").value(manager.getSimCountryIso());
                    out.name("sim_operator").value(manager.getSimOperator());
                    out.name("sim_operator_name").value(manager.getSimOperatorName());
                    out.name("sim_serial_number").value(manager.getSimSerialNumber());
                    out.name("sim_subscriber_id").value(manager.getSubscriberId());
                    int simState = manager.getSimState();
                    String simStateString;
                    switch (simState) {
                        case TelephonyManager.SIM_STATE_ABSENT:
                            simStateString = "absent";
                            break;
                        case TelephonyManager.SIM_STATE_NETWORK_LOCKED:
                            simStateString = "network_locked";
                            break;
                        case TelephonyManager.SIM_STATE_PIN_REQUIRED:
                            simStateString = "pin_required";
                            break;
                        case TelephonyManager.SIM_STATE_PUK_REQUIRED:
                            simStateString = "puk_required";
                            break;
                        case TelephonyManager.SIM_STATE_READY:
                            simStateString = "ready";
                            break;
                        case TelephonyManager.SIM_STATE_UNKNOWN:
                            simStateString = "unknown";
                            break;
                        default:
                            simStateString = Integer.toString(simState);
                            break;
                    }
                    out.name("sim_state").value(simStateString);


                }

                out.endObject();
            }
        });
    }

    static void onReceiveTelephonyCall(final Context context, JSONObject opts) {
        String numberExtra = opts.optString("number");
        if (numberExtra == null) {
            Log.e("termux-api", "No 'number extra");
            ResultReturner.noteDone(context);
        }

        Uri data = Uri.parse("tel:" + numberExtra);

        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        callIntent.setData(data);

        try {
            context.startActivity(callIntent);
        } catch (SecurityException e) {
            Log.e("termux-api", "Exception in phone call", e);
        }

        ResultReturner.noteDone(context);
    }

}