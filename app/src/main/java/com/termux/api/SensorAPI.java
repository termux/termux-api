package com.termux.api;

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
import com.termux.api.util.TermuxApiLogger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;


/**
 * API that allows you to listen to all sensors on device
 */
public class SensorAPI {

    /**
     * Starts our SensorReader service
     */
    public static void onReceive(final Context context, final Intent intent) {
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


        public void onCreate() {
            super.onCreate();
            sensorReadout = new JSONObject();
            semaphore = new Semaphore(1);
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
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
            super.onDestroy();
            cleanup();
            TermuxApiLogger.info("SensorAPI SensorReaderService onDestroy()");
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
                    TermuxApiLogger.error("onSensorChanged error", e);
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
                    return new SensorCommandHandler() {
                        @Override
                        public SensorCommandResult handle(SensorManager sensorManager, Context context, Intent intent) {
                            SensorCommandResult result = new SensorCommandResult();
                            result.message = "Unknown command: " + command;
                            return result;
                        }
                    };
            }
        }

        private void postSensorCommandResult(final Context context, final Intent intent,
                                             final SensorCommandResult result) {

            ResultReturner.returnData(context, intent, new ResultReturner.ResultWriter() {
                @Override
                public void writeResult(PrintWriter out) {
                    out.append(result.message + "\n");
                    if (result.error != null) {
                        out.append(result.error + "\n");
                    }
                    out.flush();
                    out.close();
                }
            });
        }


        /*
         * -----
         * Sensor Command Handlers
         * -----
         */


        /**
         * Handler for returning a list of all available sensors
         */
        static SensorCommandHandler listHandler = new SensorCommandHandler() {
            @Override
            public SensorCommandResult handle(SensorManager sensorManager, Context context, Intent intent) {
                SensorCommandResult result = new SensorCommandResult();
                JSONArray sensorArray = new JSONArray();
                List<Sensor> sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL);

                try {
                    for (int j = 0; j < sensorList.size(); ++j) {
                        Sensor sensor = sensorList.get(j);
                        sensorArray.put(sensor.getName());
                    }
                    JSONObject output = new JSONObject();
                    output.put("sensors", sensorArray);
                    result.message = output.toString(INDENTATION);
                } catch (JSONException e) {
                    TermuxApiLogger.error("listHandler JSON error", e);
                }
                return result;
            }
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
                    TermuxApiLogger.info("Cleanup()");
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
            List<Sensor> availableSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
            List<Sensor> sensorsToListenTo = new ArrayList<>();

            boolean listenToAll = intent.getBooleanExtra("all", false);

            if (listenToAll) {
                for (Sensor sensor : availableSensors) {
                    sensorManager.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_UI);
                }
                sensorsToListenTo = availableSensors;
                TermuxApiLogger.info("Listening to ALL sensors");
            } else {

                // try to find matching sensors that were sent in request
                for (String sensorName : requestedSensors) {
                    // ignore case
                    sensorName = sensorName.toUpperCase();

                    for (Sensor sensor : availableSensors) {
                        if (sensor.getName().toUpperCase().contains(sensorName)) {
                            sensorManager.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_UI);
                            sensorsToListenTo.add(sensor);
                            break;
                        }
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
            outputWriter.setOnErrorListener(new SocketWriterErrorListener() {
                @Override
                public void onError(Exception e) {
                    outputWriter = null;
                    TermuxApiLogger.error("SensorOutputWriter error", e);
                }
            });

            int delay = intent.getIntExtra("delay", SensorOutputWriter.DEFAULT_DELAY);
            TermuxApiLogger.info("Delay set to: " + delay);
            outputWriter.setDelay(delay);

            int limit = intent.getIntExtra("limit", SensorOutputWriter.DEFAULT_LIMIT);
            TermuxApiLogger.info("SensorOutput limit set to: " + limit);
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
                        outputSocket.connect(new LocalSocketAddress(this.outputSocketAddress));

                        try (PrintWriter writer = new PrintWriter(outputSocket.getOutputStream())) {

                            while (isRunning) {
                                try {
                                    Thread.sleep(this.delay);
                                } catch (InterruptedException e) {
                                    TermuxApiLogger.info("SensorOutputWriter interrupted: " + e.getMessage());
                                }
                                semaphore.acquire();
                                writer.write(sensorReadout.toString(INDENTATION) + "\n");
                                writer.flush();
                                semaphore.release();

                                if (++counter >= limit) {
                                    TermuxApiLogger.info("SensorOutput limit reached! Performing cleanup");
                                    cleanup();
                                }
                            }
                            TermuxApiLogger.info("SensorOutputWriter finished");
                        }
                    }
                } catch (Exception e) {
                    TermuxApiLogger.error("SensorOutputWriter error", e);

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

