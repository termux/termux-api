package com.termux.api;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.IBinder;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.TermuxApiLogger;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.media.MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED;
import static android.media.MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED;

/**
 * API that enables recording to a file via the built-in microphone
 */
public class MicRecorderAPI {

    /**
     * Starts our MicRecorder service
     */
    static void onReceive(final Context context, final Intent intent) {
        Intent recorderService = new Intent(context, MicRecorderService.class);
        recorderService.setAction(intent.getAction());
        recorderService.putExtras(intent.getExtras());
        context.startService(recorderService);
    }

    /**
     * All recording functionality exists in this background service
     */
    public static class MicRecorderService extends Service implements MediaRecorder.OnInfoListener, MediaRecorder.OnErrorListener {
        protected static final int MIN_RECORDING_LIMIT = 1000;

        // default max recording duration in seconds
        protected static final int DEFAULT_RECORDING_LIMIT = (1000 * 60 * 15);

        protected static MediaRecorder mediaRecorder;

        // are we currently recording using the microphone?
        protected static boolean isRecording;

        // file we're recording too
        protected static File file;


        public int onStartCommand(Intent intent, int flags, int startId) {
            getMediaRecorder(this);

            // get command handler and display result
            String command = intent.getAction();
            Context context = getApplicationContext();
            RecorderCommandHandler handler = getRecorderCommandHandler(command);
            RecorderCommandResult result = handler.handle(context, intent);
            postRecordCommandResult(context, intent, result);

            return Service.START_NOT_STICKY;
        }

        protected static RecorderCommandHandler getRecorderCommandHandler(final String command) {
            switch (command == null ? "" : command) {
                case "info":
                    return infoHandler;
                case "record":
                    return recordHandler;
                case "quit":
                    return quitHandler;
                default:
                    return new RecorderCommandHandler() {
                        @Override
                        public RecorderCommandResult handle(Context context, Intent intent) {
                            RecorderCommandResult result = new RecorderCommandResult();
                            result.error = "Unknown command: " + command;
                            return result;
                        }
                    };
            }
        }

        protected static void postRecordCommandResult(final Context context, final Intent intent,
                                                      final RecorderCommandResult result) {

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

        /**
         * Returns our MediaPlayer instance and ensures it has all the necessary callbacks
         */
        protected static void getMediaRecorder(MicRecorderService service) {
            if (mediaRecorder == null) {
                mediaRecorder = new MediaRecorder();
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                mediaRecorder.setOnErrorListener(service);
                mediaRecorder.setOnInfoListener(service);
            }
        }

        public void onDestroy() {
            super.onDestroy();
            cleanupMediaRecorder();
            TermuxApiLogger.info("MicRecorderAPI MicRecorderService onDestroy()");
        }

        /**
         * Releases MediaRecorder resources
         */
        protected static void cleanupMediaRecorder() {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            }
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public void onError(MediaRecorder mr, int what, int extra) {
            TermuxApiLogger.error("MicRecorderService onError() " + what);
        }

        @Override
        public void onInfo(MediaRecorder mr, int what, int extra) {
            switch (what) {
                case MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED: // intentional fallthrough
                case MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
                    finishRecording();
            }
            TermuxApiLogger.info("MicRecorderService onInfo() " + what);
        }

        protected static void finishRecording() {
            isRecording = false;
            cleanupMediaRecorder();
        }

        protected static String getDefaultRecordingFilename() {
            DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy_HH-mm-ss");
            Date date = new Date();
            return Environment.getExternalStorageDirectory().getAbsolutePath() + "/TermuxAudioRecording_" + dateFormat.format(date) + ".3gp";
        }

        protected static String getRecordingInfoJSONString() {
            String result = "";
            JSONObject info = new JSONObject();
            try {
                info.put("isRecording", isRecording);

                if (isRecording) {
                    info.put("outputFile", file.getAbsoluteFile());
                }
                result = info.toString(2);
            } catch (JSONException e) {
                TermuxApiLogger.error("infoHandler json error", e);
            }
            return result;
        }


        /**
         * -----
         * Recorder Command Handlers
         * -----
         */

        static RecorderCommandHandler infoHandler = new RecorderCommandHandler() {
            @Override
            public RecorderCommandResult handle(Context context, Intent intent) {
                RecorderCommandResult result = new RecorderCommandResult();
                result.message = getRecordingInfoJSONString();
                return result;
            }
        };

        static RecorderCommandHandler recordHandler = new RecorderCommandHandler() {
            @Override
            public RecorderCommandResult handle(Context context, Intent intent) {
                RecorderCommandResult result = new RecorderCommandResult();

                String filename = intent.hasExtra("file") ? intent.getStringExtra("file") : getDefaultRecordingFilename();

                int duration = intent.getIntExtra("limit", DEFAULT_RECORDING_LIMIT);
                // allow the duration limit to be disabled with zero or negative
                if (duration > 0 && duration < MIN_RECORDING_LIMIT)
                    duration = MIN_RECORDING_LIMIT;

                file = new File(filename);

                TermuxApiLogger.info("MediaRecording file is: " + file.getAbsoluteFile());

                if (file.exists()) {
                    result.error = String.format("File: %s already exists! Please specify a different filename", file.getName());
                } else {
                    if (isRecording) {
                        result.error = "Recording already in progress!";
                    } else {
                        try {
                            mediaRecorder.setOutputFile(filename);
                            mediaRecorder.setMaxDuration(duration);
                            mediaRecorder.prepare();
                            mediaRecorder.start();
                            isRecording = true;
                            result.message = String.format("Recording started: %s \nMax Duration: %s",
                                    file.getAbsoluteFile(), MediaPlayerAPI.getTimeString(duration / 1000));

                        } catch (IllegalStateException | IOException e) {
                            TermuxApiLogger.error("MediaRecorder error", e);
                            result.error = "Recording error: " + e.getMessage();
                        }
                    }
                }
                return result;
            }
        };

        static RecorderCommandHandler quitHandler = new RecorderCommandHandler() {
            @Override
            public RecorderCommandResult handle(Context context, Intent intent) {
                RecorderCommandResult result = new RecorderCommandResult();

                if (isRecording) {
                    finishRecording();
                    result.message = "Recording finished: " + file.getAbsoluteFile();
                } else {
                    result.message = "No recording to stop";
                }
                return result;
            }
        };
    }

    /**
     * Interface for handling recorder commands
     */
    interface RecorderCommandHandler {
        RecorderCommandResult handle(final Context context, final Intent intent);
    }

    /**
     * Simple POJO to store result of executing a Recorder command
     */
    static class RecorderCommandResult {
        public String message = "";
        public String error;
    }
}
