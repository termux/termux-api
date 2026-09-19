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

import android.content.ContentResolver;
import android.provider.Settings;

// INDENTATION NOTICE this file indentation is configured for mobile screens . small tall screen  . DON'T CHANGE  it if you edit from desktop computer with large screen and you think it looks wrong to you . it's intentional not a mistake . to reduce unnecessary nesting horizontally and allow set ts=2  in vim tab width 2  spaces only 

// after I wrote JSON I realized all output was only one line . so this will convert to simpler output without json
// class JsonWriter {
class myWriter {

StringWriter sw;
public myWriter(StringWriter s) {
	sw = s;
}
void setIndent(String s){}
void beginArray(){}
void endArray(){}
void value(String s) {
	if (s == null) {
		s = "null";
	// if (s.trim().isEmpty()
	}	else if (s.isEmpty())
		s = "nmempty";
	s += "\n";
	sw.append(s);
}

}

public class SettingsAPI {

private static final String LOG_TAG = "SettingsAPI";
static Context context;
static TermuxApiReceiver receiver;
static Intent intent;
static StringWriter sw;
// static JsonWriter out;
static myWriter out;

static ContentResolver cr;
static String setting;
static String type;
static String domain;

public static void onReceive(TermuxApiReceiver _receiver, final Context _context, Intent _intent) {
	Logger.logDebug(LOG_TAG, "onReceive");
	context = _context;
	receiver = _receiver;
	intent = _intent;
	sw = new StringWriter();
	// out = new JsonWriter(sw);
	out = new myWriter(sw);
	
	// out.setIndent("  ");
	out.setIndent("	");

	cr = context.getContentResolver();
	setting = intent.getStringExtra("setting");
	type = intent.getStringExtra("type");
	domain = intent.getStringExtra("domain");

	if (type == null)
		type = "string";

	Logger.logInfo(LOG_TAG, "==*==*==*==");
	Logger.logInfo(LOG_TAG, "received " + intent.toString());

	try {
		try {
			// jsonTest();
			settings();
		} catch (Exception e) {
			Logger.logError(LOG_TAG, e.toString());
			out.value(e.toString());
		}
		write();
	} catch (Exception e) {
		Logger.logError(LOG_TAG, e.toString());
	}
}

static void write() {
	ResultReturner.returnData(context.getApplicationContext(), intent, new ResultReturner.WithInput() {
		@Override public void writeResult(PrintWriter _out) throws Exception {
		try {
			// Logger.logInfo(LOG_TAG, "printing \n\""+sw+"\"");
			// out.value("\n");
			_out.print(sw);
		} catch (Exception e) {
			Logger.logError(LOG_TAG, e.toString());
		}
		}
	});
}

// # [ array of values
// # { object of key value pair
/*
static void jsonTest() throws Exception {
	Logger.logInfo(LOG_TAG, "JSON test");
	out.beginArray();
	out.value("123");

	// out.beginObject();
	// out.name("on").value(0);
	// out.value(name);
	// out.endObject();
	
	out.endArray();
}
*/

static String getString() throws Exception {
	switch(domain) {
		case "global":
			return Settings.Global.getString(cr, setting);
		case "secure":
			return Settings.Secure.getString(cr, setting);
		case "system":
			return Settings.System.getString(cr, setting);
		default:
			return domain +" is invalid. valid global/system";
	}
}

static boolean setString(String value) throws Exception {
	switch(domain) {
		case "global":
			return Settings.Global.putString(cr, setting, value);
		case "secure":
			return Settings.Secure.putString(cr, setting, value);
		case "system":
			return Settings.System.putString(cr, setting, value);
		default:
			out.value("invalid domain "+domain +" did you mean global/system");
			return false;
	}
}

static void get() throws Exception {
	switch(type) {
		case "string":
			String get = getString();
			out.value(get);
			Logger.logInfo(LOG_TAG, "received string \"" + get + "\"");
			break;
		case "int":
			int geti = Settings.Global.getInt(cr, setting);
			// out.name(geti.toString());
			out.value(geti+"");
			Logger.logInfo(LOG_TAG, "received int \"" + geti + "\"");
			break;
		default:
			out.value(type +" is invalid type did you mean string/int");
			break;
	}
}

static void set() throws Exception {
	String value = intent.getStringExtra("value");
	Logger.logInfo(LOG_TAG, "received value \""+value+"\"");
	boolean result = false;
	switch(type) {
		case "string":
			// if (value == null || value.trim().isEmpty()) {
				// out.value("notice setting empty value");
			if (value == null)
				value = "";
			out.value("setting "+setting+" to \""+value+"\"");
			result = setString(value);
			break;
		case "int":
			if (value == null || value.trim().isEmpty()) {
				out.value("int value cannot be empty");
				return;
			}
			out.value("setting "+setting+"  to \""+value+"\"");
			result = Settings.Global.putInt(cr, setting, Integer.parseInt(value));
			break;
		default:
			out.value(type +" is invalid type did you mean string/int");
			// out.value("correct usage termux-api Settings -a get/set --es type string/int --es setting name");
			break;
	}
	out.value("boolean result: "+result);
}

static void settings() throws Exception {
	String action = intent.getAction();
	Logger.logInfo(LOG_TAG, "received action "+action);
	// out.beginArray();
	// out.beginObject();
	switch(action) {
		case "get":
			get();
			break;
		case "set":
			set();
			break;
		default:
			out.value(action+" is invalid action. only get/set accepted");
			break;
	}
	// out.endObject();
	// out.endArray();
}
} // class
