package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.util.JsonWriter;

import com.termux.api.util.ResultReturner;

public class AudioAPI {

    static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        final String SampleRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        final String framesPerBuffer = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        final String AudioUnprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED);
        final int volume_level = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        final int maxvolume_level = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final boolean bluetootha2dp = am.isBluetoothA2dpOn();
        final boolean wiredhs = am.isWiredHeadsetOn();
        final int nativeoutput = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        int _bs = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack at = new AudioTrack(
                new AudioAttributes.Builder()
                    .setFlags(256) // FLAG_LOW_LATENCY
                    .build(),
                new AudioFormat.Builder()
                    .build(),
                4, // bytes; i.e. one 16bit 2ch frame
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            );
            _bs = at.getBufferSizeInFrames();
            //final int bc = at.getBufferCapacityInFrames(); only available api 24 and up and apparently
            // always returns same value as the initial getbuffersizeinframes.
            at.release();
        }
        final int bs = _bs;

        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            public void writeJson(JsonWriter out) throws Exception {
                out.beginObject();
                out.name("PROPERTY_OUTPUT_SAMPLE_RATE").value(SampleRate);
                out.name("PROPERTY_OUTPUT_FRAMES_PER_BUFFER").value(framesPerBuffer);
                out.name("PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED").value(AudioUnprocessed);
                out.name("STREAM_MUSIC_VOLUME").value(volume_level);
                out.name("STREAM_MUSIC_MAXVOLUME").value(maxvolume_level);
                out.name("BLUETOOTH_A2DP_IS_ON").value(bluetootha2dp);
                out.name("WIREDHEADSET_IS_CONNECTED").value(wiredhs);
                out.name("CURRENT_NATIVE_OUTPUT_SAMPLERATE").value(nativeoutput);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    out.name("AUDIOTRACK_BUFFER_SIZE_IN_FRAMES").value(bs);
                }
                out.endObject();
            }
        });
    }

}
