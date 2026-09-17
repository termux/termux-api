package com.termux.api.apis;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Size;
import android.view.Surface;

import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * API that enables recording video (camera sensor, with audio) to a file.
 * Mirrors MicRecorderAPI's persistent-Service start/stop pattern, but drives
 * the camera via Camera2 (as CameraPhotoAPI does) instead of MediaRecorder's
 * built-in camera source, since MediaRecorder.CAMERA is deprecated/removed
 * on modern Android and Camera2 + a MediaRecorder-provided Surface is the
 * supported path.
 */
public class CameraVideoAPI {

    private static final String LOG_TAG = "CameraVideoAPI";

    /**
     * Starts our CameraVideo service
     */
    public static void onReceive(final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        Intent recorderService = new Intent(context, CameraVideoService.class);
        recorderService.setAction(intent.getAction());
        recorderService.putExtras(intent.getExtras());
        context.startService(recorderService);
    }

    /**
     * All recording functionality exists in this background service so that
     * state (the open camera, the active session, the MediaRecorder) survives
     * across separate "record" / "info" / "quit" broadcasts.
     */
    public static class CameraVideoService extends Service {
        private static final String LOG_TAG = "CameraVideoService";

        protected static final int MIN_DURATION_LIMIT = 1000;
        // default max recording duration in seconds (15 minutes, matching MicRecorderAPI)
        protected static final int DEFAULT_DURATION_LIMIT = (1000 * 60 * 15);

        protected static CameraDevice cameraDevice;
        protected static CameraCaptureSession captureSession;
        protected static MediaRecorder mediaRecorder;
        protected static HandlerThread backgroundThread;

        protected static boolean isRecording;
        protected static File file;

        public int onStartCommand(Intent intent, int flags, int startId) {
            Logger.logDebug(LOG_TAG, "onStartCommand");

            String command = intent.getAction();
            Context context = getApplicationContext();
            RecorderCommandHandler handler = getRecorderCommandHandler(command);
            handler.handle(context, intent);

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
                        postResult(context, intent, "", "Unknown command: " + command);
                        if (!isRecording)
                            context.stopService(intent);
                    };
            }
        }

        protected static void postResult(final Context context, final Intent intent,
                                          final String message, final String error) {
            ResultReturner.returnData(context, intent, out -> {
                out.append(message).append("\n");
                if (error != null && !error.isEmpty()) {
                    out.append(error).append("\n");
                }
                out.flush();
                out.close();
            });
        }

        public void onDestroy() {
            Logger.logDebug(LOG_TAG, "onDestroy");
            cleanup();
        }

        protected static void cleanup() {
            if (isRecording) {
                try {
                    mediaRecorder.stop();
                } catch (RuntimeException e) {
                    Logger.logInfo(LOG_TAG, "mediaRecorder.stop() threw: " + e.getMessage());
                }
                isRecording = false;
            }
            if (captureSession != null) {
                try {
                    captureSession.close();
                } catch (RuntimeException ignored) {}
                captureSession = null;
            }
            if (mediaRecorder != null) {
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;
            }
            if (cameraDevice != null) {
                try {
                    cameraDevice.close();
                } catch (RuntimeException ignored) {}
                cameraDevice = null;
            }
            if (backgroundThread != null) {
                backgroundThread.quitSafely();
                backgroundThread = null;
            }
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        protected static String getDefaultRecordingFilename() {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            Date date = new Date();
            return Environment.getExternalStorageDirectory().getAbsolutePath()
                    + "/TermuxCameraVideo_" + dateFormat.format(date) + ".mp4";
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

        static RecorderCommandHandler infoHandler = (context, intent) -> {
            postResult(context, intent, getRecordingInfoJSONString(), null);
            if (!isRecording)
                context.stopService(intent);
        };

        static RecorderCommandHandler recordHandler = (context, intent) -> {
            if (isRecording) {
                postResult(context, intent, "", "Recording already in progress!");
                return;
            }

            final String cameraId = Objects.toString(intent.getStringExtra("camera"), "0");
            final String filename = intent.hasExtra("file")
                    ? intent.getStringExtra("file") : getDefaultRecordingFilename();

            int duration = intent.getIntExtra("limit", DEFAULT_DURATION_LIMIT);
            if (duration > 0 && duration < MIN_DURATION_LIMIT)
                duration = MIN_DURATION_LIMIT;
            final int durationMs = duration;

            final boolean recordAudio = intent.getBooleanExtra("audio", true);
            final int bitrate = intent.getIntExtra("bitrate", 10_000_000);
            final int fps = intent.getIntExtra("fps", 30);

            file = new File(filename);
            if (file.exists()) {
                postResult(context, intent, "",
                        String.format("File: %s already exists! Please specify a different filename", file.getName()));
                return;
            }

            try {
                final CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                backgroundThread = new HandlerThread("CameraVideoBackground");
                backgroundThread.start();

                //noinspection MissingPermission
                manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(CameraDevice camera) {
                        cameraDevice = camera;
                        try {
                            startRecordingSession(context, intent, camera, characteristics,
                                    file, durationMs, recordAudio, bitrate, fps);
                        } catch (Exception e) {
                            Logger.logStackTraceWithMessage(LOG_TAG, "Error starting recording session", e);
                            postResult(context, intent, "", "Error starting recording: " + e.getMessage());
                            cleanup();
                            context.stopService(intent);
                        }
                    }

                    @Override
                    public void onDisconnected(CameraDevice camera) {
                        Logger.logInfo(LOG_TAG, "onDisconnected() from camera");
                        cleanup();
                    }

                    @Override
                    public void onError(CameraDevice camera, int error) {
                        Logger.logError(LOG_TAG, "Failed opening camera: " + error);
                        postResult(context, intent, "", "Failed opening camera, error code: " + error);
                        cleanup();
                        context.stopService(intent);
                    }
                }, new Handler(backgroundThread.getLooper()));

            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error opening camera", e);
                postResult(context, intent, "", "Error opening camera: " + e.getMessage());
                context.stopService(intent);
            }
        };

        static void startRecordingSession(final Context context, final Intent intent,
                                           final CameraDevice camera,
                                           final CameraCharacteristics characteristics,
                                           final File outputFile, final int durationMs,
                                           final boolean recordAudio, final int bitrate,
                                           final int fps) throws Exception {

            // Pick a sane recording size: largest video-capable size capped at 1080p,
            // since arbitrarily "largest" (as CameraPhotoAPI does for stills) can pick
            // sensor-max sizes the encoder / bitrate settings below aren't tuned for.
            android.hardware.camera2.params.StreamConfigurationMap map =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            List<Size> sizes = new ArrayList<>(Arrays.asList(map.getOutputSizes(MediaRecorder.class)));
            Size chosen = sizes.get(0);
            for (Size s : sizes) {
                boolean chosenOverCap = chosen.getHeight() > 1080;
                boolean sFits = s.getHeight() <= 1080;
                if ((sFits && (long) s.getWidth() * s.getHeight() > (long) chosen.getWidth() * chosen.getHeight())
                        || (chosenOverCap && sFits)) {
                    chosen = s;
                }
            }
            final Size videoSize = chosen;

            mediaRecorder = new MediaRecorder();
            if (recordAudio) {
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            }
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            if (recordAudio) {
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            }
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
            mediaRecorder.setVideoFrameRate(fps);
            mediaRecorder.setVideoEncodingBitRate(bitrate);
            mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
            if (durationMs > 0) {
                mediaRecorder.setMaxDuration(durationMs);
            }
            mediaRecorder.setOnInfoListener((mr, what, extra) -> {
                switch (what) {
                    case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
                    case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:
                        Logger.logInfo(LOG_TAG, "Recording limit reached, stopping");
                        cleanup();
                        context.stopService(intent);
                }
            });
            mediaRecorder.setOnErrorListener((mr, what, extra) -> {
                Logger.logError(LOG_TAG, "MediaRecorder error: what=" + what + " extra=" + extra);
                cleanup();
                context.stopService(intent);
            });
            mediaRecorder.prepare();

            final Surface recorderSurface = mediaRecorder.getSurface();
            final List<Surface> outputSurfaces = new ArrayList<>();
            outputSurfaces.add(recorderSurface);

            camera.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    captureSession = session;
                    try {
                        CaptureRequest.Builder req = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                        req.addTarget(recorderSurface);
                        req.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                        session.setRepeatingRequest(req.build(), null, new Handler(backgroundThread.getLooper()));

                        mediaRecorder.start();
                        isRecording = true;

                        postResult(context, intent, String.format(
                                "Recording started: %s\nSize: %dx%d\nMax Duration: %s",
                                outputFile.getAbsolutePath(), videoSize.getWidth(), videoSize.getHeight(),
                                durationMs <= 0 ? "unlimited" : MediaPlayerAPI.getTimeString(durationMs / 1000)),
                                null);
                    } catch (Exception e) {
                        Logger.logStackTraceWithMessage(LOG_TAG, "onConfigured() error", e);
                        postResult(context, intent, "", "Error starting capture: " + e.getMessage());
                        cleanup();
                        context.stopService(intent);
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Logger.logError(LOG_TAG, "onConfigureFailed()");
                    postResult(context, intent, "", "Camera session configuration failed");
                    cleanup();
                    context.stopService(intent);
                }
            }, new Handler(backgroundThread.getLooper()));
        }

        static RecorderCommandHandler quitHandler = (context, intent) -> {
            String message = isRecording
                    ? "Recording finished: " + file.getAbsolutePath()
                    : "No recording to stop";
            cleanup();
            postResult(context, intent, message, null);
            context.stopService(intent);
        };
    }

    interface RecorderCommandHandler {
        void handle(final Context context, final Intent intent);
    }
}
