package com.termux.api;

import android.app.Application;
import android.hardware.SensorManager;

public class TermuxAPI extends Application {
    private SensorManager mSensorManager;
    private SensorAPI sensorAPI;

    @Override
    public void onCreate() {
        super.onCreate();

        sensorAPI = new SensorAPI(this);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorManager.registerListener(sensorAPI, sensorAPI.getProximitySensor(), SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onTerminate() {
        mSensorManager.unregisterListener(sensorAPI);

        super.onTerminate();
    }
}
