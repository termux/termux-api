package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.JsonWriter;
import android.media.AudioTrack;
import android.media.AudioFormat;
import android.media.AudioAttributes;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.ResultReturner.ResultWriter;

public class AudioAPI {
    static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioTrack at = new AudioTrack (
                new AudioAttributes.Builder()
                .setFlags(256) // FLAG_LOW_LATENCY
                .build(),
                new AudioFormat.Builder()
                .build(),
                4, // bytes; i.e. one 16bit 2ch frame
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
        );

        String SampleRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        String framesPerBuffer = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        String AudioUnprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED);
        int nativeoutput = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        int volume_level= am.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maxvolume_level = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int sr = at.getSampleRate();
        int pr = at.getPlaybackRate();
        int bc = at.getBufferCapacityInFrames();
        int bs = at.getBufferSizeInFrames();

        at.release();

        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            public void writeJson(JsonWriter out) throws Exception {
                out.beginObject();
                out.name("PROPERTY_OUTPUT_SAMPLE_RATE").value(SampleRate);
                out.name("PROPERTY_OUTPUT_FRAMES_PER_BUFFER").value(framesPerBuffer);
                out.name("PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED").value(AudioUnprocessed);
                out.name("STREAM_MUSIC_VOLUME").value(volume_level);
                out.name("STREAM_MUSIC_MAXVOLUME").value(maxvolume_level);
                out.name("CURRENT_NATIVE_OUTPUT_SAMPLERATE").value(nativeoutput);
                out.name("SR").value(sr);
                out.name("PR").value(pr);
                out.name("BC").value(bc);
                out.name("BS").value(bs);
                out.endObject();
                }
        });
    }
}
