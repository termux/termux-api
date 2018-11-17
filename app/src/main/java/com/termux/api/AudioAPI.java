package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.util.JsonWriter;

import com.termux.api.util.ResultReturner;

public class AudioAPI {

    static void onReceive(final Context context) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        final String SampleRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        final String framesPerBuffer = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        final boolean bluetootha2dp = am.isBluetoothA2dpOn();
        final boolean wiredhs = am.isWiredHeadsetOn();

        final int sr, bs, sr_ll, bs_ll, sr_ps, bs_ps, nosr;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            nosr = 0;
            AudioTrack at;
            at = new AudioTrack.Builder()
                .setBufferSizeInBytes(4) // one 16bit 2ch frame
                .build();
            sr = at.getSampleRate();
            bs = at.getBufferSizeInFrames();
            at.release();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                at = new AudioTrack.Builder()
                    .setBufferSizeInBytes(4) // one 16bit 2ch frame
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .build();
            } else {
                AudioAttributes aa = new AudioAttributes.Builder()
                    .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                    .build();
                at = new AudioTrack.Builder()
                    .setAudioAttributes(aa)
                    .setBufferSizeInBytes(4) // one 16bit 2ch frame
                    .build();
            }
            sr_ll = at.getSampleRate();
            bs_ll = at.getBufferSizeInFrames();
            at.release();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                at = new AudioTrack.Builder()
                    .setBufferSizeInBytes(4) // one 16bit 2ch frame
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
                    .build();
		sr_ps = at.getSampleRate();
		bs_ps = at.getBufferSizeInFrames();
		at.release();
            } else {
		sr_ps = sr;
		bs_ps = bs;
            }
        } else {
            sr = bs = sr_ll = bs_ll = sr_ps = bs_ps = 0;
            nosr = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        }

        ResultReturner.returnData(context, new ResultReturner.ResultJsonWriter() {
            public void writeJson(JsonWriter out) throws Exception {
                out.beginObject();
                out.name("PROPERTY_OUTPUT_SAMPLE_RATE").value(SampleRate);
                out.name("PROPERTY_OUTPUT_FRAMES_PER_BUFFER").value(framesPerBuffer);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    out.name("AUDIOTRACK_SAMPLE_RATE").value(sr);
                    out.name("AUDIOTRACK_BUFFER_SIZE_IN_FRAMES").value(bs);
                    if (sr_ll != sr || bs_ll != bs) { // all or nothing
                        out.name("AUDIOTRACK_SAMPLE_RATE_LOW_LATENCY").value(sr_ll);
                        out.name("AUDIOTRACK_BUFFER_SIZE_IN_FRAMES_LOW_LATENCY").value(bs_ll);
                    }
                    if (sr_ps != sr || bs_ps != bs) { // all or nothing
                        out.name("AUDIOTRACK_SAMPLE_RATE_POWER_SAVING").value(sr_ps);
                        out.name("AUDIOTRACK_BUFFER_SIZE_IN_FRAMES_POWER_SAVING").value(bs_ps);
                    }
                } else {
                    out.name("AUDIOTRACK_NATIVE_OUTPUT_SAMPLE_RATE").value(nosr);
                }
                out.name("BLUETOOTH_A2DP_IS_ON").value(bluetootha2dp);
                out.name("WIREDHEADSET_IS_CONNECTED").value(wiredhs);
                out.endObject();
            }
        });
    }

}
