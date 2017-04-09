package com.termux.api;

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

import java.io.PrintWriter;

public class DialogActivity extends Activity {

    boolean mResultReturned = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String title = null;
        Intent i = getIntent();
        if (i != null) {
            title = i.getStringExtra("input_title");
        }

        if (title == null) {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        } else {
            setTitle(title);
        }
        setContentView(R.layout.dialog_textarea_input);

        setFinishOnTouchOutside(false);

        EditText textInput = (EditText) findViewById(R.id.text_input);

        String inputHint = getIntent().getStringExtra("input_hint");
        if (inputHint != null) textInput.setHint(inputHint);

        boolean multiLine = getIntent().getBooleanExtra("multiple_lines", false);
        String inputType = getIntent().getStringExtra("input_type");
        if ("password".equals(inputType)) {
            textInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else if (multiLine) {
            textInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        } else {
            textInput.setInputType(InputType.TYPE_CLASS_TEXT);
        }

        findViewById(R.id.cancel_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
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
                        mResultReturned = true;
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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!mResultReturned) {
            mResultReturned = true;
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
    }

}
