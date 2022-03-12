package com.termux.api.apis;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.IBinder;
import android.util.ArrayMap;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.media.MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED;
import static android.media.MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED;

/**
 * API that enables recording to a file via the built-in microphone
 */
public class MicRecorderAPI {

    private static final String LOG_TAG = "MicRecorderAPI";

    /**
     * Starts our MicRecorder service
     */
    public static void onReceive(final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

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


        private static final String LOG_TAG = "MicRecorderService";

        public void onCreate() {
            getMediaRecorder(this);
        }

        public int onStartCommand(Intent intent, int flags, int startId) {
            Logger.logDebug(LOG_TAG, "onStartCommand");

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
                    return (context, intent) -> {
                        RecorderCommandResult result = new RecorderCommandResult();
                        result.error = "Unknown command: " + command;
                        if (!isRecording)
                            context.stopService(intent);
                        return result;
                    };
            }
        }

        protected static void postRecordCommandResult(final Context context, final Intent intent,
                                                      final RecorderCommandResult result) {

            ResultReturner.returnData(context, intent, out -> {
                out.append(result.message).append("\n");
                if (result.error != null) {
                    out.append(result.error).append("\n");
                }
                out.flush();
                out.close();
            });
        }

        /**
         * Returns our MediaPlayer instance and ensures it has all the necessary callbacks
         */
        protected static void getMediaRecorder(MicRecorderService service) {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setOnErrorListener(service);
            mediaRecorder.setOnInfoListener(service);
        }

        public void onDestroy() {
            Logger.logDebug(LOG_TAG, "onDestroy");

            cleanupMediaRecorder();
        }

        /**
         * Releases MediaRecorder resources
         */
        protected static void cleanupMediaRecorder() {
            if (isRecording) {
                mediaRecorder.stop();
                isRecording = false;
            }
            mediaRecorder.reset();
            mediaRecorder.release();
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public void onError(MediaRecorder mr, int what, int extra) {
            Logger.logVerbose(LOG_TAG, "onError: what: " + what + ", extra: "  + extra);

            isRecording = false;
            this.stopSelf();
        }

        @Override
        public void onInfo(MediaRecorder mr, int what, int extra) {
            Logger.logVerbose(LOG_TAG, "onInfo: what: " + what + ", extra: "  + extra);

            switch (what) {
                case MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED: // intentional fallthrough
                case MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
                    this.stopSelf();
            }
        }

        protected static String getDefaultRecordingFilename() {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            Date date = new Date();
            return Environment.getExternalStorageDirectory().getAbsolutePath() + "/TermuxAudioRecording_" + dateFormat.format(date);
        }

        protected static String getRecordingInfoJSONString() {
            String result = "";
            JSONObject info = new JSONObject();
            try {
                info.put("isRecording", isRecording);
                if (isRecording)
                    info.put("outputFile", file.getAbsolutePath());
                result = info.toString(2);
            } catch (JSONException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "infoHandler json error", e);
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
                if (!isRecording)
                    context.stopService(intent);
                return result;
            }
        };

        static RecorderCommandHandler recordHandler = new RecorderCommandHandler() {
            @Override
            public RecorderCommandResult handle(Context context, Intent intent) {
                RecorderCommandResult result = new RecorderCommandResult();

                int duration = intent.getIntExtra("limit", DEFAULT_RECORDING_LIMIT);
                // allow the duration limit to be disabled with zero or negative
                if (duration > 0 && duration < MIN_RECORDING_LIMIT)
                    duration = MIN_RECORDING_LIMIT;

                String sencoder = intent.hasExtra("encoder") ? intent.getStringExtra("encoder") : "";
                ArrayMap<String, Integer> encoder_map = new ArrayMap<>(3);
                encoder_map.put("aac", MediaRecorder.AudioEncoder.AAC);
                encoder_map.put("amr_nb", MediaRecorder.AudioEncoder.AMR_NB);
                encoder_map.put("amr_wb", MediaRecorder.AudioEncoder.AMR_WB);

                Integer encoder = encoder_map.get(sencoder.toLowerCase());
                if (encoder == null)
                    encoder = MediaRecorder.AudioEncoder.AAC;

                int format = intent.getIntExtra("format", MediaRecorder.OutputFormat.DEFAULT);
                if (format == MediaRecorder.OutputFormat.DEFAULT) {
                    SparseIntArray format_map = new SparseIntArray(3);
                    format_map.put(MediaRecorder.AudioEncoder.AAC,
                                   MediaRecorder.OutputFormat.MPEG_4);
                    format_map.put(MediaRecorder.AudioEncoder.AMR_NB,
                                   MediaRecorder.OutputFormat.THREE_GPP);
                    format_map.put(MediaRecorder.AudioEncoder.AMR_WB,
                                   MediaRecorder.OutputFormat.THREE_GPP);
                    format = format_map.get(encoder, MediaRecorder.OutputFormat.DEFAULT);
                }

                SparseArray<String> extension_map = new SparseArray<>(2);
                extension_map.put(MediaRecorder.OutputFormat.MPEG_4, ".m4a");
                extension_map.put(MediaRecorder.OutputFormat.THREE_GPP, ".3gp");
                String extension = extension_map.get(format);

                String filename = intent.hasExtra("file") ? intent.getStringExtra("file") : getDefaultRecordingFilename() + (extension != null ? extension : "");

                int source = intent.getIntExtra("source", MediaRecorder.AudioSource.MIC);

                int bitrate = intent.getIntExtra("bitrate", 0);
                int srate = intent.getIntExtra("srate", 0);
                int channels = intent.getIntExtra("channels", 0);

                file = new File(filename);

                Logger.logInfo(LOG_TAG, "MediaRecording file is: " + file.getAbsolutePath());

                if (file.exists()) {
                    result.error = String.format("File: %s already exists! Please specify a different filename", file.getName());
                } else {
                    if (isRecording) {
                        result.error = "Recording already in progress!";
                    } else {
                        try {
                            mediaRecorder.setAudioSource(source);
                            mediaRecorder.setOutputFormat(format);
                            mediaRecorder.setAudioEncoder(encoder);
                            mediaRecorder.setOutputFile(filename);
                            mediaRecorder.setMaxDuration(duration);
                            if (bitrate > 0)
                                mediaRecorder.setAudioEncodingBitRate(bitrate);
                            if (srate > 0)
                                mediaRecorder.setAudioSamplingRate(srate);
                            if (channels > 0)
                                mediaRecorder.setAudioChannels(channels);
                            mediaRecorder.prepare();
                            mediaRecorder.start();
                            isRecording = true;
                            result.message = String.format("Recording started: %s \nMax Duration: %s",
                                                           file.getAbsolutePath(),
                                                           duration <= 0 ?
                                                           "unlimited" :
                                                           MediaPlayerAPI.getTimeString(duration /
                                                                                        1000));

                        } catch (IllegalStateException | IOException e) {
                            Logger.logStackTraceWithMessage(LOG_TAG, "MediaRecorder error", e);
                            result.error = "Recording error: " + e.getMessage();
                        }
                    }
                }
                if (!isRecording)
                    context.stopService(intent);
                return result;
            }
        };

        static RecorderCommandHandler quitHandler = new RecorderCommandHandler() {
            @Override
            public RecorderCommandResult handle(Context context, Intent intent) {
                RecorderCommandResult result = new RecorderCommandResult();

                if (isRecording) {
                    result.message = "Recording finished: " + file.getAbsolutePath();
                } else {
                    result.message = "No recording to stop";
                }
                context.stopService(intent);
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
