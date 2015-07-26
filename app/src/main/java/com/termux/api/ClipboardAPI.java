package com.termux.api;

import java.io.PrintWriter;

import android.content.ClipData;
import android.content.ClipData.Item;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.ResultReturner.ResultWriter;

public class ClipboardAPI {

	static void onReceive(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
		final ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
		final ClipData clipData = clipboard.getPrimaryClip();
		final String newClipText = intent.getStringExtra("text");
		if (newClipText != null) {
			// Set clip.
			clipboard.setPrimaryClip(ClipData.newPlainText("", newClipText));
		}

		ResultReturner.returnData(apiReceiver, intent, new ResultWriter() {
			@Override
			public void writeResult(PrintWriter out) {
				if (newClipText == null) {
					// Get clip.
					if (clipData == null) {
						out.println();
					} else {
						int itemCount = clipData.getItemCount();
						for (int i = 0; i < itemCount; i++) {
							Item item = clipData.getItemAt(i);
							CharSequence text = item.coerceToText(context);
							if (text != null) {
								out.print(text);
								if (i + 1 != itemCount) {
									out.println();
								}
							}
						}
					}
				} else {
					// Set clip - already done in main thread.
				}
			}
		});
	}

}
