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

    static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        final String SampleRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        final String framesPerBuffer = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        final String AudioUnprocessed;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            AudioUnprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED);
        } else {
            AudioUnprocessed = null;
        }
        final int volume_level = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        final int maxvolume_level = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final boolean bluetootha2dp = am.isBluetoothA2dpOn();
        final boolean wiredhs = am.isWiredHeadsetOn();

        int _sr, _bs, _sr_ll, _bs_ll, _nosr;
        _sr = _bs = _sr_ll = _bs_ll = _nosr = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int[] modes = {AudioTrack.PERFORMANCE_MODE_POWER_SAVING,
                           AudioTrack.PERFORMANCE_MODE_LOW_LATENCY};
            for (int mode: modes) {
                AudioTrack at = new AudioTrack.Builder()
                    .setBufferSizeInBytes(4) // one 16bit 2ch frame
                    .setPerformanceMode(mode)
                    .build();
                if (mode == AudioTrack.PERFORMANCE_MODE_POWER_SAVING) {
                    _sr = at.getSampleRate();
                    _bs = at.getBufferSizeInFrames();
                } else if (mode == AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) {
                    _sr_ll = at.getSampleRate();
                    _bs_ll = at.getBufferSizeInFrames();
                }
                at.release();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            int[] flags = {0,AudioAttributes.FLAG_LOW_LATENCY};
            for (int flag: flags) {
                AudioAttributes aa = new AudioAttributes.Builder()
                    .setFlags(flag)
                    .build();
                AudioTrack at = new AudioTrack.Builder()
                    .setAudioAttributes(aa)
                    .setBufferSizeInBytes(4) // one 16bit 2ch frame
                    .build();
                if (flag == 0) {
                    _sr = at.getSampleRate();
                    _bs = at.getBufferSizeInFrames();
                } else if (flag == AudioAttributes.FLAG_LOW_LATENCY) {
                    _sr_ll = at.getSampleRate();
                    _bs_ll = at.getBufferSizeInFrames();
                }
                at.release();
            }
        } else {
            _nosr = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        }
        final int sr = _sr;
        final int bs = _bs;
        final int sr_ll = _sr_ll;
        final int bs_ll = _bs_ll;
        final int nosr = _nosr;

        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            public void writeJson(JsonWriter out) throws Exception {
                out.beginObject();
                out.name("PROPERTY_OUTPUT_SAMPLE_RATE").value(SampleRate);
                out.name("PROPERTY_OUTPUT_FRAMES_PER_BUFFER").value(framesPerBuffer);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    out.name("PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED").value(AudioUnprocessed);
                }
                out.name("STREAM_MUSIC_VOLUME").value(volume_level);
                out.name("STREAM_MUSIC_MAXVOLUME").value(maxvolume_level);
                out.name("BLUETOOTH_A2DP_IS_ON").value(bluetootha2dp);
                out.name("WIREDHEADSET_IS_CONNECTED").value(wiredhs);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    out.name("AUDIOTRACK_SAMPLE_RATE").value(sr);
                    out.name("AUDIOTRACK_BUFFER_SIZE_IN_FRAMES").value(bs);
                    if (sr_ll != sr || bs_ll != bs) { // all or nothing
                        out.name("AUDIOTRACK_SAMPLE_RATE_LOW_LATENCY").value(sr_ll);
                        out.name("AUDIOTRACK_BUFFER_SIZE_IN_FRAMES_LOW_LATENCY").value(bs_ll);
                    }
                } else {
                    out.name("AUDIOTRACK_NATIVE_OUTPUT_SAMPLE_RATE").value(nosr);
                }
                out.endObject();
            }
        });
    }

}
