package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.hardware.ConsumerIrManager;
import android.util.JsonWriter;

import com.termux.api.util.ResultReturner;

/**
 * Exposing {@link ConsumerIrManager}.
 */
public class InfraredAPI {

    static void onReceiveCarrierFrequency(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                ConsumerIrManager irManager = (ConsumerIrManager) context.getSystemService(Context.CONSUMER_IR_SERVICE);

                if (irManager.hasIrEmitter()) {
                    ConsumerIrManager.CarrierFrequencyRange[] ranges = irManager.getCarrierFrequencies();
                    if (ranges == null) {
                        out.beginObject().name("API_ERROR").value("Error communicating with the Consumer IR Service").endObject();
                    } else {
                        out.beginArray();
                        for (ConsumerIrManager.CarrierFrequencyRange range : ranges) {
                            out.beginObject();
                            out.name("min").value(range.getMinFrequency());
                            out.name("max").value(range.getMaxFrequency());
                            out.endObject();
                        }
                        out.endArray();
                    }
                } else {
                    out.beginArray().endArray();
                }
            }
        });
    }


    static void onReceiveTransmit(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                ConsumerIrManager irManager = (ConsumerIrManager) context.getSystemService(Context.CONSUMER_IR_SERVICE);

                int carrierFrequency = intent.getIntExtra("frequency", -1);
                int[] pattern = intent.getIntArrayExtra("pattern");

                String error = null;
                if (!irManager.hasIrEmitter()) {
                    error = "No infrared emitter available";
                } else if (carrierFrequency == -1) {
                    error = "Missing 'frequency' extra";
                } else if (pattern == null || pattern.length == 0) {
                    error = "Missing 'pattern' extra";
                }

                if (error != null) {
                    out.beginObject().name("API_ERROR").value(error).endObject();
                    return;
                }

                irManager.transmit(carrierFrequency, pattern);
            }
        });
    }
}
