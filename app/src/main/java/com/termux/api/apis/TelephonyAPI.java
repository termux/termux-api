package com.termux.api.apis;

import android.Manifest;
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
import android.telephony.CellInfoNr;
import android.telephony.CellIdentityNr;
import android.telephony.CellSignalStrength;
import android.telephony.CellSignalStrengthNr;
import android.telephony.TelephonyManager;
import android.util.JsonWriter;

import androidx.annotation.RequiresPermission;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.io.IOException;

import java.util.List;

/**
 * Exposing {@link android.telephony.TelephonyManager}.
 */
public class TelephonyAPI {

    private static final String LOG_TAG = "TelephonyAPI";

    private static void writeIfKnown(JsonWriter out, String name, int value) throws IOException {
        if (value != Integer.MAX_VALUE) out.name(name).value(value);
    }
    private static void writeIfKnown(JsonWriter out, String name, long value) throws IOException {
        if (value != Long.MAX_VALUE) out.name(name).value(value);
    }
    private static void writeIfKnown(JsonWriter out, String name, int[] value) throws IOException {
        if (value != null) {
                out.name(name);
                out.beginArray();
                for (int i = 0; i < value.length; i++) out.value(value[i]);
                out.endArray();

        }
    }

    public static void onReceiveTelephonyCellInfo(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceiveTelephonyCellInfo");

        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                out.beginArray();

                List<CellInfo> cellInfoData = null;

                try {
                    cellInfoData = manager.getAllCellInfo();
                } catch (SecurityException e) {
                    // Direct call of getAllCellInfo() doesn't work on Android 10 (Q).
                    // https://developer.android.com/reference/android/telephony/TelephonyManager#getAllCellInfo().
                }

                if (cellInfoData != null) {
                    for (CellInfo cellInfo : cellInfoData) {
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

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                writeIfKnown(out, "rsrp", lteInfo.getCellSignalStrength().getRsrp());
                                writeIfKnown(out, "rsrq", lteInfo.getCellSignalStrength().getRsrq());
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                writeIfKnown(out, "rssi", lteInfo.getCellSignalStrength().getRssi());
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                writeIfKnown(out, "bands", lteInfo.getCellIdentity().getBands());
                            }
                        } else if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) && (cellInfo instanceof CellInfoNr)) {
                            CellInfoNr nrInfo = (CellInfoNr) cellInfo;
                            CellIdentityNr nrcellIdent = (CellIdentityNr) nrInfo.getCellIdentity();
                            CellSignalStrength ssInfo = nrInfo.getCellSignalStrength();
                            out.name("type").value("nr");
                            out.name("registered").value(cellInfo.isRegistered());

                            out.name("asu").value(ssInfo.getAsuLevel());
                            out.name("dbm").value(ssInfo.getDbm());
                            writeIfKnown(out, "level", ssInfo.getLevel());
                            writeIfKnown(out, "nci", nrcellIdent.getNci());
                            writeIfKnown(out, "pci", nrcellIdent.getPci());
                            writeIfKnown(out, "tac", nrcellIdent.getTac());
                            out.name("mcc").value(nrcellIdent.getMccString());
                            out.name("mnc").value(nrcellIdent.getMncString());
                            if (ssInfo instanceof  CellSignalStrengthNr) {
                                CellSignalStrengthNr nrssInfo = (CellSignalStrengthNr) ssInfo;
                                writeIfKnown(out, "csi_rsrp", nrssInfo.getCsiRsrp());
                                writeIfKnown(out, "csi_rsrq", nrssInfo.getCsiRsrq());
                                writeIfKnown(out, "csi_sinr", nrssInfo.getCsiSinr());
                                writeIfKnown(out, "ss_rsrp", nrssInfo.getSsRsrp());
                                writeIfKnown(out, "ss_rsrq", nrssInfo.getSsRsrq());
                                writeIfKnown(out, "ss_sinr", nrssInfo.getSsSinr());
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                writeIfKnown(out, "bands", nrcellIdent.getBands());
                            }
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
                }

                out.endArray();
            }
        });
    }

    public static void onReceiveTelephonyDeviceInfo(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceiveTelephonyDeviceInfo");

        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
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

                    int phoneType = manager.getPhoneType();

                    String device_id = null;

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            device_id = phoneType == TelephonyManager.PHONE_TYPE_GSM ? manager.getImei() : manager.getMeid();
                        }
                    } catch (SecurityException e) {
                        // Failed to obtain device id.
                        // Android 10+ requires READ_PRIVILEGED_PHONE_STATE
                        // https://source.android.com/devices/tech/config/device-identifiers
                    }

                    out.name("device_id").value(device_id);
                    out.name("device_software_version").value(manager.getDeviceSoftwareVersion());
                    out.name("phone_count").value(manager.getPhoneCount());
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
                            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) && (networkType == TelephonyManager.NETWORK_TYPE_NR)) {
                                networkTypeName = "nr";
                                break;
                            }
                            networkTypeName = Integer.toString(networkType);
                            break;
                    }
                    out.name("network_type").value(networkTypeName);
                    out.name("network_roaming").value(manager.isNetworkRoaming());
                    out.name("sim_country_iso").value(manager.getSimCountryIso());
                    out.name("sim_operator").value(manager.getSimOperator());
                    out.name("sim_operator_name").value(manager.getSimOperatorName());

                    String sim_serial = null;
                    String subscriber_id = null;
                    try {
                        sim_serial = manager.getSimSerialNumber();
                        subscriber_id = manager.getSubscriberId();
                    } catch (SecurityException e) {
                        // Failed to obtain device id.
                        // Android 10+.
                    }
                    out.name("sim_serial_number").value(sim_serial);
                    out.name("sim_subscriber_id").value(subscriber_id);

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

    public static void onReceiveTelephonyCall(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceiveTelephonyCall");

        String numberExtra = intent.getStringExtra("number");
        if (numberExtra == null) {
            Logger.logError(LOG_TAG, "No 'number' extra");
            ResultReturner.noteDone(apiReceiver, intent);
            return;
        }

        if(numberExtra.contains("#"))
            numberExtra = numberExtra.replace("#","%23");

        Uri data = Uri.parse("tel:" + numberExtra);

        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        callIntent.setData(data);

        try {
            context.startActivity(callIntent);
        } catch (SecurityException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Exception in phone call", e);
        }

        ResultReturner.noteDone(apiReceiver, intent);
    }

}
