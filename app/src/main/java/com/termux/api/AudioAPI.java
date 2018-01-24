package com.termux.api;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.JsonWriter;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.ResultReturner.ResultWriter;



public class AudioAPI {
    static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
	ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
	    
	    AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
	    String SampleRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
	    String framesPerBuffer = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
	    public void writeJson(JsonWriter out) throws Exception {
		out.beginObject(); 
		out.name("PROPERTY_OUTPUT_SAMPLE_RATE").value(SampleRate);
		out.name("PROPERTY_OUTPUT_FRAMES_PER_BUFFER").value(framesPerBuffer);
		out.endObject();
		}
	});
    }
}

