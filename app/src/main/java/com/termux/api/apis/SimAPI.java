package com.termux.api.apis;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.content.pm.PackageManager;
import android.se.omapi.SEService;
import android.se.omapi.SEService.OnConnectedListener;
import android.se.omapi.Reader;
import android.util.JsonWriter;

import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.util.concurrent.Executor;
import java.io.PrintWriter;
import java.io.StringWriter;

public class SimAPI {

	private static final String LOG_TAG = "SimAPI";
	static Context context;
	static JsonWriter out;
	static SEService service;
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
			out.beginArray();
			readSubscription();
			readPm();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
				readOmapi();

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

	static void readPm() throws Exception {
		out.beginObject();
		final PackageManager pm = context.getPackageManager();
		if(pm != null)
			out.name("omapi").value (pm.hasSystemFeature(PackageManager.FEATURE_SE_OMAPI_UICC));
		out.endObject();
	}

	static void readOmapi() throws Exception {
		Executor exec = new Executor() {
			public void execute(Runnable r) {
				r.run();
			}
		};

		OnConnectedListener listener = new OnConnectedListener() {
			@Override public void onConnected() {
				Logger.logInfo(LOG_TAG, "connected");
				try {
					out.beginObject();
					Reader[] readers = service.getReaders();
					for (Reader reader : readers) {
						out.name ("name").value(reader.getName());
						out.name("status").value(reader.isSecureElementPresent());
					}
					if (readers.length == 0) {
						out.name("omapi").value("No reader available");
					}
					out.endObject();
					out.endArray();
					write();
				} catch (Exception e) {
					Logger.logError(LOG_TAG, e.getMessage());
				}
			}
		};

		service = new SEService(context.getApplicationContext(), exec, listener);
	}

	static void readSubscription() throws Exception {
		out.beginObject();
		SubscriptionManager sm = context.getSystemService(SubscriptionManager.class);
		for(SubscriptionInfo si: sm.getActiveSubscriptionInfoList()) {
			out.name("slot").value(si.getSimSlotIndex());
			out.name("id").value(si.getSubscriptionId());
			out.name("name").value(si.getCarrierName().toString());
		}
		out.endObject();
	}
}
