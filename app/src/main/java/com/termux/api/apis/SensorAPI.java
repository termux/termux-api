package com.termux.api.apis;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.IBinder;

import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.Semaphore;


/**
 * API that allows you to listen to all sensors on device
 */
public class SensorAPI {

    private static final String LOG_TAG = "SensorAPI";

    /**
     * Starts our SensorReader service
     */
    public static void onReceive(final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        Intent serviceIntent = new Intent(context, SensorReaderService.class);
        serviceIntent.setAction(intent.getAction());
        serviceIntent.putExtras(intent.getExtras());
        context.startService(serviceIntent);
    }


    /**
     * All sensor listening functionality exists in this background service
     */
    public static class SensorReaderService extends Service {

        // indentation for JSON output
        protected static final int INDENTATION = 2;

        protected static SensorManager sensorManager;
        protected static JSONObject sensorReadout;
        protected static SensorOutputWriter outputWriter;

        // prevent concurrent modifications w/ sensor readout
        protected static Semaphore semaphore;

        private static final String LOG_TAG = "SensorReaderService";

        public void onCreate() {
            Logger.logDebug(LOG_TAG, "onCreate");

            super.onCreate();
            sensorReadout = new JSONObject();
            semaphore = new Semaphore(1);
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            Logger.logDebug(LOG_TAG, "onStartCommand");

            String command = intent.getAction();
            Context context = getApplicationContext();
            SensorManager sensorManager = getSensorManager(context);

            SensorCommandHandler handler = getSensorCommandHandler(command);
            SensorCommandResult result = handler.handle(sensorManager, context, intent);

            if (result.type == ResultType.SINGLE) {
                // post one-time result now, rather than an active stream
                postSensorCommandResult(context, intent, result);
            }
            return Service.START_NOT_STICKY;
        }

        protected static SensorManager getSensorManager(Context context) {
            if (sensorManager == null) {
                sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            }
            return sensorManager;
        }

        @Override
        public void onDestroy() {
            Logger.logDebug(LOG_TAG, "onDestroy");

            super.onDestroy();
            cleanup();
        }

        protected static void cleanup() {
            if (outputWriter != null && outputWriter.isRunning()) {
                outputWriter.interrupt();
                outputWriter = null;
            }

            if (sensorManager != null) {
                sensorManager.unregisterListener(sensorEventListener);
                sensorManager = null;
            }
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        /**
         * Sensor event listener for reading sensor value updates and storing them
         * in the sensorReadout JSON object
         */
        protected static SensorEventListener sensorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                JSONArray sensorValuesArray = new JSONArray();
                try {
                    semaphore.acquire();
                    for (int j = 0; j < sensorEvent.values.length; ++j) {
                        sensorValuesArray.put(j, sensorEvent.values[j]);
                    }
                    JSONObject sensorInfo = new JSONObject();
                    sensorInfo.put("values", sensorValuesArray);
                    sensorReadout.put(sensorEvent.sensor.getName(), sensorInfo);
                    semaphore.release();
                } catch (JSONException e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "onSensorChanged error", e);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // unused
            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {
            }
        };

        protected static SensorCommandHandler getSensorCommandHandler(final String command) {
            switch (command == null ? "" : command) {
                case "list":
                    return listHandler;
                case "cleanup":
                    return cleanupHandler;
                case "sensors":
                    return sensorHandler;
                default:
                    return (sensorManager, context, intent) -> {
                        SensorCommandResult result = new SensorCommandResult();
                        result.message = "Unknown command: " + command;
                        return result;
                    };
            }
        }

        private void postSensorCommandResult(final Context context, final Intent intent,
                                             final SensorCommandResult result) {

            ResultReturner.returnData(context, intent, out -> {
                out.append(result.message).append("\n");
                if (result.error != null) {
                    out.append(result.error).append("\n");
                }
                out.flush();
                out.close();
            });
        }

        private static JSONObject getSensorInfo(Sensor sensor) throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("name", sensor.getName());
            obj.put("max_range", sensor.getMaximumRange());
            obj.put("min_delay", sensor.getMinDelay());
            obj.put("power", sensor.getPower());
            obj.put("resolution", sensor.getResolution());
            obj.put("type", sensor.getStringType());
            obj.put("vendor", sensor.getVendor());
            obj.put("version", sensor.getVersion());
            return obj;
        }

        /*
         * -----
         * Sensor Command Handlers
         * -----
         */


        /**
         * Handler for returning a list of all available sensors
         */
        static SensorCommandHandler listHandler = (sensorManager, context, intent) -> {
            SensorCommandResult result = new SensorCommandResult();
            JSONArray sensorArray = new JSONArray();
            List<Sensor> sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL);

            boolean showInfo = intent.getBooleanExtra("info", false);

            try {
                for (int j = 0; j < sensorList.size(); ++j) {
                    Sensor sensor = sensorList.get(j);
                    if (showInfo) {
                        sensorArray.put(getSensorInfo(sensor));
                    } else {
                        sensorArray.put(sensor.getName());
                    }
                }
                JSONObject output = new JSONObject();
                output.put("sensors", sensorArray);
                result.message = output.toString(INDENTATION);
            } catch (JSONException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "listHandler JSON error", e);
            }
            return result;
        };

        /**
         * Handler for managing cleaning up sensor resources
         */
        static SensorCommandHandler cleanupHandler = new SensorCommandHandler() {
            @Override
            public SensorCommandResult handle(SensorManager sensorManager, Context context, Intent intent) {
                SensorCommandResult result = new SensorCommandResult();

                if (outputWriter != null) {
                    outputWriter.interrupt();
                    outputWriter = null;
                    sensorManager.unregisterListener(sensorEventListener);
                    result.message = "Sensor cleanup successful!";
                    Logger.logInfo(LOG_TAG, "Cleanup()");
                } else {
                    result.message = "Sensor cleanup unnecessary";
                }
                return result;
            }
        };

        /**
         * Handler for managing listening to sensors
         */
        static SensorCommandHandler sensorHandler = new SensorCommandHandler() {
            @Override
            public SensorCommandResult handle(SensorManager sensorManager, Context context, Intent intent) {
                SensorCommandResult result = new SensorCommandResult();
                result.type = ResultType.CONTINUOUS;

                clearSensorValues();

                // sensor list user passed to us
                String[] requestedSensors = getUserRequestedSensors(intent);
                List<Sensor> sensorsToListenTo = getSensorsToListenTo(sensorManager, requestedSensors, intent);

                if (sensorsToListenTo.isEmpty()) {
                    result.message = "No valid sensors were registered!";
                    result.type = ResultType.SINGLE;
                } else {
                    if (outputWriter == null) {
                        outputWriter = createSensorOutputWriter(intent);
                        outputWriter.start();
                    }
                }
                return result;
            }
        };

        /**
         * Gets a string array of all user requested sensor names to listen to
         */
        protected static String[] getUserRequestedSensors(Intent intent) {
            // sensor values passed to us from user
            String sensorListString = intent.hasExtra("sensors") ? intent.getStringExtra("sensors") : "";
            return sensorListString.split(",");
        }

        /**
         * Gets a list of all sensors to listen to, that were requested and are available
         */
        protected static List<Sensor> getSensorsToListenTo(SensorManager sensorManager, String[] requestedSensors, Intent intent) {
            List<Sensor> availableSensors = new ArrayList<>(sensorManager.getSensorList(Sensor.TYPE_ALL));
            Collections.sort(availableSensors, (s1, s2) -> s1.getName().compareTo(s2.getName()));
            List<Sensor> sensorsToListenTo = new ArrayList<>();

            boolean listenToAll = intent.getBooleanExtra("all", false);

            if (listenToAll) {
                for (Sensor sensor : availableSensors) {
                    sensorManager.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_UI);
                }
                sensorsToListenTo = availableSensors;
                Logger.logInfo(LOG_TAG, "Listening to ALL sensors");
            } else {

                // try to find matching sensors that were sent in request
                for (String requestedSensor : requestedSensors) {
                    // ignore case
                    requestedSensor = requestedSensor.toUpperCase();

                    Sensor shortestMatchSensor = null;
                    int shortestMatchSensorLength = Integer.MAX_VALUE;

                    for (Sensor availableSensor : availableSensors) {
                        String sensorName = availableSensor.getName().toUpperCase();
                        if (sensorName.contains(requestedSensor) && sensorName.length() < shortestMatchSensorLength) {
                            shortestMatchSensor = availableSensor;
                            shortestMatchSensorLength = sensorName.length();
                        }
                    }

                    if (shortestMatchSensor != null) {
                        sensorManager.registerListener(sensorEventListener, shortestMatchSensor, SensorManager.SENSOR_DELAY_UI);
                        sensorsToListenTo.add(shortestMatchSensor);
                    }
                }
            }
            return sensorsToListenTo;
        }

        /**
         * Clears out sensorEventListener as well as our sensorReadout JSON object
         */
        protected static void clearSensorValues() {
            // prevent duplicate listeners
            sensorManager.unregisterListener(sensorEventListener);

            // clear out old values
            sensorReadout = new JSONObject();
        }


        /**
         * Creates SensorOutputWriter to write sensor values to stdout
         */
        protected static SensorOutputWriter createSensorOutputWriter(Intent intent) {
            String socketAddress = intent.getStringExtra("socket_output");

            outputWriter = new SensorOutputWriter(socketAddress);
            outputWriter.setOnErrorListener(e -> {
                outputWriter = null;
                Logger.logStackTraceWithMessage(LOG_TAG, "SensorOutputWriter error", e);
            });

            int delay = intent.getIntExtra("delay", SensorOutputWriter.DEFAULT_DELAY);
            Logger.logInfo(LOG_TAG, "Delay set to: " + delay);
            outputWriter.setDelay(delay);

            int limit = intent.getIntExtra("limit", SensorOutputWriter.DEFAULT_LIMIT);
            Logger.logInfo(LOG_TAG, "SensorOutput limit set to: " + limit);
            outputWriter.setLimit(limit);

            return outputWriter;
        }


        /**
         * Handles continuously writing Sensor info to an OutputStream asynchronously
         */
        static class SensorOutputWriter extends Thread {
            // delay in milliseconds before posting new sensor reading
            static final int DEFAULT_DELAY = 1000;

            static final int DEFAULT_LIMIT = Integer.MAX_VALUE;

            protected String outputSocketAddress;
            protected boolean isRunning;
            protected int delay;
            protected int counter;
            protected int limit;
            protected SocketWriterErrorListener errorListener;


            public SensorOutputWriter(String outputSocketAddress, int delay) {
                this.outputSocketAddress = outputSocketAddress;
                this.delay = delay;
            }

            public SensorOutputWriter(String outputSocketAddress) {
                this(outputSocketAddress, DEFAULT_DELAY);
            }

            public boolean isRunning() {
                return isRunning;
            }

            public void setOnErrorListener(SocketWriterErrorListener errorListener) {
                this.errorListener = errorListener;
            }

            public void setDelay(int delay) {
                this.delay = delay;
            }

            public void setLimit(int limit) {
                this.limit = limit;
            }

            @Override
            public void run() {
                isRunning = true;
                counter = 0;

                try {
                    try (LocalSocket outputSocket = new LocalSocket()) {
                        outputSocket.connect(ResultReturner.getApiLocalSocketAddress(
                                ResultReturner.context, "output", this.outputSocketAddress));

                        try (PrintWriter writer = new PrintWriter(outputSocket.getOutputStream())) {

                            while (isRunning) {
                                try {
                                    Thread.sleep(this.delay);
                                } catch (InterruptedException e) {
                                    Logger.logInfo(LOG_TAG, "SensorOutputWriter interrupted: " + e.getMessage());
                                }
                                semaphore.acquire();
                                writer.write(sensorReadout.toString(INDENTATION) + "\n");
                                writer.flush();
                                semaphore.release();

                                if (++counter >= limit) {
                                    Logger.logInfo(LOG_TAG, "SensorOutput limit reached! Performing cleanup");
                                    cleanup();
                                }
                            }
                            Logger.logInfo(LOG_TAG, "SensorOutputWriter finished");
                        }
                    }
                } catch (Exception e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "SensorOutputWriter error", e);

                    if (errorListener != null) {
                        errorListener.onError(e);
                    }
                }
            }

            @Override
            public void interrupt() {
                super.interrupt();
                this.isRunning = false;
            }
        }
    }

    /**
     * Callback interface for handling exceptions that could occur in SensorOutputWriter
     */
    interface SocketWriterErrorListener {
        void onError(Exception e);
    }


    /**
     * Interface for handling sensor commands
     */
    interface SensorCommandHandler {
        SensorCommandResult handle(SensorManager sensorManager, final Context context, final Intent intent);
    }

    /**
     * Simple POJO to store result of executing a sensor command
     */
    static class SensorCommandResult {
        public String message = "";
        public ResultType type = ResultType.SINGLE;
        public String error;
    }

    enum ResultType {
        SINGLE,
        CONTINUOUS
    }

}

