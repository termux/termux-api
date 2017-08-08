package com.termux.api;

import android.app.Application;
import android.hardware.SensorManager;

public class TermuxAPI extends Application {
    private SensorAPI sensorAPI;

    @Override
    public void onCreate() {
        super.onCreate();
        sensorAPI = new SensorAPI(this);
    }

    @Override
    public void onTerminate() {
        ((SensorManager) getSystemService(SENSOR_SERVICE)).unregisterListener(sensorAPI);

        super.onTerminate();
    }
}
