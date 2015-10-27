package com.termux.api;

import java.io.PrintWriter;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.EditText;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.ResultReturner.ResultWriter;

public class DialogActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.dialog_textarea_input);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
	}

	@Override
	protected void onResume() {
		super.onResume();

		EditText textInput = (EditText) findViewById(R.id.text_input);

		String inputHint = getIntent().getStringExtra("input_hint");
		if (inputHint != null) {
			textInput.setHint(inputHint);
		}

		String inputType = getIntent().getStringExtra("input_type");
		if ("password".equals(inputType)) {
			textInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
		}

        findViewById(R.id.cancel_button).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				ResultReturner.returnData(DialogActivity.this, getIntent(), new ResultWriter() {
					@Override
					public void writeResult(PrintWriter out) throws Exception {
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								finish();
							}
						});
					}
				});
			}
		});

		findViewById(R.id.ok_button).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				ResultReturner.returnData(DialogActivity.this, getIntent(), new ResultWriter() {
					@Override
					public void writeResult(PrintWriter out) throws Exception {
						String text = ((EditText) findViewById(R.id.text_input)).getText().toString();
						out.println(text.trim());
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								finish();
							}
						});
					}
				});
			}
		});
	}
}
