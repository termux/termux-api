package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.JsonWriter;

import com.termux.api.util.ResultReturner;

import java.util.List;

public class ProximityAPI {

     static void onReceiveProximity(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
              private SensorManager mSensorManager;
              private Sensor mProximity;
              private static final int SENSOR_SENSITIVITY = 4;
              
              mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
              mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
              
              mSensorManager.registerListener(this, mProximity, SensorManager.SENSOR_DELAY_NORMAL);
              out.beginObject();
              
              if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                  if (event.values[0] >= -SENSOR_SENSITIVITY && event.values[0] <= SENSOR_SENSITIVITY) {
                       out.name("object is").value("Near");
                  } else {
                      out.name("object is").value("Far");
                  }
              }
              out.endObject();
              mSensorManager.unregisterListener(this);
            }
        });
        
     }
}
