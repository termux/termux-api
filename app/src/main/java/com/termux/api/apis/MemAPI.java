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

import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.util.List;
import java.io.PrintWriter;
import java.io.StringWriter;

public class MemAPI {

	private static final String LOG_TAG = "MemAPI";
	static Context context;
	static JsonWriter out;
	static TermuxApiReceiver receiver;
	static Intent intent;
	static StringWriter sw;

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
			exitReason();
			appMem();
			totalMem();
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
		
	static void exitReason() throws Exception {
		out.name("exitReason");
		out.beginArray();
		final ActivityManager am = context.getSystemService(ActivityManager.class);
		List<ApplicationExitInfo> list = am.getHistoricalProcessExitReasons("com.termux", 0, 10);
		//out.name("Exit reason").value (i.toString());
		for (ApplicationExitInfo i : list) {	
			out.value(i.toString());
		}
		out.endArray();
	}

	static void appMem() throws Exception {
		Runtime rt = Runtime.getRuntime();
		out.name("appMem");
		out.beginObject();
		out.name("freeMemory").value(rt.freeMemory()/1024/1024);
		out.name("maxMemory").value(rt.maxMemory()/1024/1024);
		out.name("totalMemory").value(rt.totalMemory()/1024/1024);
		out.endObject();
	}

	static void totalMem() throws Exception {
		out.name("totalMem");
		out.beginObject();
		final ActivityManager am = context.getSystemService(ActivityManager.class);
		MemoryInfo mem = new
 ActivityManager.MemoryInfo();
		am.getMemoryInfo(mem);
		out.name("availMem").value(mem.availMem/1024/1024);
		out.name("lowMemory").value(mem.lowMemory);
		out.name("threshold").value(mem.threshold/1024/1024);
		out.name("totalMem").value(mem.totalMem/1024/1024);
		out.endObject();
	}
}
