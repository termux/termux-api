package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.JsonWriter;
import android.util.SparseArray;

import com.termux.api.util.ResultReturner;

import java.io.IOException;
import java.io.PrintWriter;

public class VolumeAPI {
    private static final int STREAM_UNKNOWN = -1;

    // string representations for each of the available audio streams
    private static SparseArray<String> streamMap = new SparseArray<>();
    static {
        streamMap.append(AudioManager.STREAM_ALARM,         "alarm");
        streamMap.append(AudioManager.STREAM_MUSIC,         "music");
        streamMap.append(AudioManager.STREAM_NOTIFICATION,  "notification");
        streamMap.append(AudioManager.STREAM_RING,          "ring");
        streamMap.append(AudioManager.STREAM_SYSTEM,        "system");
        streamMap.append(AudioManager.STREAM_VOICE_CALL,    "call");
    }


    static void onReceive(final TermuxApiReceiver receiver, final Context context, final Intent intent) {
        final AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        String action = intent.getAction();

        if ("set-volume".equals(action)) {
            final String streamName = intent.getStringExtra("stream");
            final int stream = getAudioStream(streamName);

            if (stream == STREAM_UNKNOWN) {
                String error = "ERROR: Unknown stream: " + streamName;
                printError(context, intent, error);
            } else {
                setStreamVolume(intent, audioManager, stream);
                ResultReturner.noteDone(receiver, intent);
            }
        } else {
            printAllStreamInfo(context, intent, audioManager);
        }
    }

    /**
     * Prints error to console
     * @param context
     * @param intent
     * @param error
     */
    private static void printError(Context context, Intent intent, final String error) {
        ResultReturner.returnData(context, intent, new ResultReturner.ResultWriter() {
            @Override
            public void writeResult(PrintWriter out) throws Exception {
                out.append(error + "\n");
                out.flush();
                out.close();
            }
        });
    }

    /**
     * Set volume for the specified audio stream
     * @param intent
     * @param audioManager
     * @param stream
     */
    private static void setStreamVolume(Intent intent, AudioManager audioManager, int stream) {
        int volume = intent.getIntExtra("volume", audioManager.getStreamVolume(stream));
        int maxVolume = audioManager.getStreamMaxVolume(stream);

        if (volume <= 0) {
            volume = 0;
        } else if (volume >= maxVolume) {
            volume = maxVolume;
        }
        audioManager.setStreamVolume(stream, volume, 0);
    }

    /**
     * Print information about all available audio streams
     * @param context
     * @param intent
     * @param audioManager
     */
    private static void printAllStreamInfo(Context context, Intent intent, final AudioManager audioManager) {
        ResultReturner.returnData(context, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws Exception {
                getStreamsInfo(audioManager, out);
                out.close();
            }
        });
    }

    /**
     * Get info for all streams
     * @param audioManager
     * @param out
     * @throws IOException
     */
    private static void getStreamsInfo(AudioManager audioManager, JsonWriter out) throws IOException {
        out.beginArray();

        for (int j = 0; j < streamMap.size(); ++j) {
            int stream = streamMap.keyAt(j);
            getStreamInfo(audioManager, out, stream);
        }
        out.endArray();
    }

    /**
     * Get info for specific stream
     * @param audioManager
     * @param out
     * @param stream
     * @throws IOException
     */
    protected static void getStreamInfo(AudioManager audioManager, JsonWriter out, int stream) throws IOException {
        out.beginObject();

        out.name("stream").value(streamMap.get(stream));
        out.name("volume").value(audioManager.getStreamVolume(stream));
        out.name("max_volume").value(audioManager.getStreamMaxVolume(stream));

        out.endObject();
    }

    /**
     * Get proper audio stream based on String type
     * @param type
     * @return
     */
    protected static int getAudioStream(String type) {
        switch (type == null ? "" : type) {
            case "alarm":           return AudioManager.STREAM_ALARM;
            case "call":            return AudioManager.STREAM_VOICE_CALL;
            case "notification":    return AudioManager.STREAM_NOTIFICATION;
            case "ring":            return AudioManager.STREAM_RING;
            case "system":          return AudioManager.STREAM_SYSTEM;
            case "music":           return AudioManager.STREAM_MUSIC;
            default:                return STREAM_UNKNOWN;
        }
    }
}
