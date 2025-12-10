package com.termux.api.apis;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.util.JsonWriter;
import android.view.InputDevice;
import android.view.KeyEvent;

import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.util.List;
import java.io.PrintWriter;
import java.io.StringWriter;

public class InputAPI {

	private static final String LOG_TAG = "InputAPI";
	static Context context;
	static TermuxApiReceiver receiver;
	static Intent intent;
	static StringWriter sw;
	static JsonWriter out;

	public static void onReceive(TermuxApiReceiver _receiver, final Context _context, Intent _intent) {
		Logger.logDebug(LOG_TAG, "onReceive");
		context = _context;
		receiver = _receiver;
		intent = _intent;
		sw = new StringWriter();
		out = new JsonWriter(sw);

		out.setIndent("  ");

		try {
			out.beginObject();
			// jsonTest();
			devices();
			out.endObject();
			write();
			Logger.logInfo(LOG_TAG, sw.toString());
		} catch (Exception e) {
			Logger.logError(LOG_TAG, e.getMessage());
		}
	}

	static void write() {
		ResultReturner.returnData(context.getApplicationContext(), intent, new ResultReturner.WithInput() {
			@Override public void writeResult(PrintWriter _out) throws Exception {
			try {
				_out.print(sw);
			} catch (Exception e) {
				Logger.logError(LOG_TAG, e.getMessage());
			}
			}
		});
	}

	static void jsonTest() throws Exception {
		// out.beginArray();
		// out.beginObject();
		out.name("devices");
		out.beginObject();
		out.name("123");
		out.beginObject();
		out.name("on").value(0);
		// out.endObject();
		out.name("keys");
		out.beginObject();
		out.name("f").value(1);
		out.endObject();
		out.endObject();
		// out.endArray();
	}

	static void devices() throws Exception {

		out.name("devices");
		// out.beginArray();
		out.beginObject();

		for (int id : InputDevice.getDeviceIds()) {
			InputDevice dev = InputDevice.getDevice(id);
			if (dev == null) continue;
			String name = dev.getName();
			int vend = dev.getVendorId();
			int prod = dev.getProductId();
			String desc = dev.getDescriptor();
			int s = dev.getSources();
			String sources = String.format("0x%X", s);
			boolean on = dev.isEnabled();

			// boolean[] keys = dev.hasKeys(ESCAPE, F1);
			boolean[] keys = dev.hasKeys(
					KeyEvent.KEYCODE_ESCAPE,
					KeyEvent.KEYCODE_F1,
					KeyEvent.KEYCODE_F2,
					KeyEvent.KEYCODE_F3,
					KeyEvent.KEYCODE_F4
					);

			// there Is more internally to perhaps find the files
			// String conf = dev.getInputDeviceConfigurationFilePathByDeviceIdentifier(id);

			// out.value(name);
			out.name(name);
			// out.beginArray();
			out.beginObject();
			// out.beginObject(name);
			out.name("vendor").value(vend);
			out.name("product").value(prod);
			out.name("descriptor").value(desc);
			out.name("sources").value(sources);
			out.name("on").value(on);
			// out.endObject();
			// out.endObject();
			// out.endArray();

			if (name.equals("fp-keys")) {
				out.name("hasKeys");
				out.beginObject();
				out.name("ESCAPE").value(keys[0]);
				out.name("F1").value(keys[1]);
				out.name("F2").value(keys[2]);
				out.name("F3").value(keys[3]);
				out.name("F4").value(keys[4]);
				out.endObject();
			}
			// out.endArray();
			out.endObject();

			// int sources = inputDevice.getSources();

		}
		out.endObject();
		// out.endArray();
	}

}
