package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.JsonWriter;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.ResultReturner.ResultJsonWriter;
import com.termux.api.util.TermuxApiLogger;

public class AccelerometerAPI {

    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        ResultReturner.returnData(apiReceiver, intent, new ResultJsonWriter() {
        private sensorManager SensorManager;
        double ax,ay,az;   // these are the acceleration in x,y and z axis
        String accuracy;
            @Override
            public void writeJson(JsonWriter out) throws Exception {
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager.getDefaultSensor(Sensor.ACCELEROMETER) != null){
            public class Accelerometer extends Activity implements SensorEventListener{
            sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
            }
            
            @Override
            public void onAccuracyChanged(Sensor arg0, int arg1) {
            switch (arg1) {
              case 0:
                accuracy = "Unreliable";
                break;
              case 1:
                accuracy = "Low";
                break;
              case 2:
                accuracy = "Medium";
                break;
              case 3:
                accuracy = "High";
                break;
            }
            }

            @Override
            public void onSensorChanged(SensorEvent event) {
                  // alpha is calculated as t / (t + dT)
                  // with t, the low-pass filter's time-constant
                  // and dT, the event delivery rate

                  final float alpha = 0.8;
                  array gravity;
                  array linear_acceleration;

                  gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
                  gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
                  gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

                  linear_acceleration[0] = event.values[0] - gravity[0];
                  linear_acceleration[1] = event.values[1] - gravity[1];
                  linear_acceleration[2] = event.values[2] - gravity[2];
                  
                  ax = linear_acceleration[0]
                  ay = linear_acceleration[1]
                  az = linear_acceleration[2]
            }
            out.beginObject();
            out.name("accuracy").value(accuracy);
            out.name("x").value(ax);
            out.name("y").value(ay);
            out.name("z").value(az);
            out.endObject();
            sensorManager.unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
  }
            else {
            string error = "No Accelerometer in this device."
            out.beginObject().name("API_ERROR").value(error).endObject();
            }
      }
      }
}
}
