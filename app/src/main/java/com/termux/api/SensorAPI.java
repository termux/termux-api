package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.JsonWriter;
import com.termux.api.util.ResultReturner;

public class SensorAPI implements SensorEventListener{
    private static SensorAPI instance;

    private Sensor proximitySensor;
    private float proximityData;

    SensorAPI(Context context){
        instance = this;
        SensorManager manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        proximitySensor = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
    }

    Sensor getProximitySensor() {
        return proximitySensor;
    }

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent, final int type) {
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                String info;
                out.beginObject();
                switch (type){
                    case Sensor.TYPE_PROXIMITY:
                        info = instance.proximitySensor.toString();
                        out.name("data").value(instance.proximityData);
                        break;
                    default:
                        info = "Unknown";
                }
                out.name("info").value(info);
                out.endObject();
            }
        });
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()){
            case Sensor.TYPE_PROXIMITY:
                proximityData = event.values[0];
        }
    }
}
