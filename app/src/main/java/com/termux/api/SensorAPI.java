package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.JsonWriter;
import android.util.SparseArray;
import com.termux.api.util.ResultReturner;

public class SensorAPI implements SensorEventListener{
    private static SensorAPI instance;

    private static SensorManager mSensorManager;

    private SparseArray<float[]> mSensorValues = new SparseArray<>();

    SensorAPI(Context context){
        instance = this;
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        tryRegisterSensor(mSensorManager, Sensor.TYPE_AMBIENT_TEMPERATURE);
        tryRegisterSensor(mSensorManager, Sensor.TYPE_LIGHT);
        tryRegisterSensor(mSensorManager, Sensor.TYPE_RELATIVE_HUMIDITY);
        tryRegisterSensor(mSensorManager, Sensor.TYPE_PRESSURE);
        tryRegisterSensor(mSensorManager, Sensor.TYPE_PROXIMITY);
    }

    private void tryRegisterSensor(SensorManager manager, int type){
        Sensor sensor = manager.getDefaultSensor(type);
        if (sensor != null){
            manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent, final int type) {
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                Sensor sensor;
                if ((sensor = mSensorManager.getDefaultSensor(type)) != null) {
                    out.beginObject();
                    out.name("info").value(sensor.toString());
                    out.name("data").value(instance.mSensorValues.get(type)[0]);
                    out.endObject();
                }
            }
        });
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        mSensorValues.put(event.sensor.getType(), event.values);
    }
}
