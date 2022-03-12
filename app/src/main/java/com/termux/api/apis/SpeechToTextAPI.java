package com.termux.api.apis;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import com.termux.api.util.ResultReturner;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.logger.Logger;

import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class SpeechToTextAPI {

    private static final String LOG_TAG = "SpeechToTextAPI";

    public static class SpeechToTextService extends IntentService {

        private static final String STOP_ELEMENT = "";

        public SpeechToTextService() {
            this(SpeechToTextService.class.getSimpleName());
        }

        public SpeechToTextService(String name) {
            super(name);
        }

        protected SpeechRecognizer mSpeechRecognizer;
        final LinkedBlockingQueue<String> queueu = new LinkedBlockingQueue<>();

        private static final String LOG_TAG = "SpeechToTextService";

        @Override
        public void onCreate() {
            Logger.logDebug(LOG_TAG, "onCreate");

            super.onCreate();
            final Context context = this;

            mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

            mSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onRmsChanged(float rmsdB) {
                    // Do nothing.
                }

                @Override
                public void onResults(Bundle results) {
                    List<String> recognitions = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    Logger.logError(LOG_TAG, "RecognitionListener#onResults(" + recognitions + ")");
                    queueu.addAll(recognitions);
                }

                @Override
                public void onReadyForSpeech(Bundle params) {
                    // Do nothing.
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    // Do nothing.
                    List<String> strings = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    Logger.logError(LOG_TAG, "RecognitionListener#onPartialResults(" + strings + ")");
                    queueu.addAll(strings);
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                    // Do nothing.
                }

                @Override
                public void onError(int error) {
                    String description;
                    switch (error) {
                        case SpeechRecognizer.ERROR_CLIENT:
                            description = "ERROR_CLIENT";
                            break;
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                            description = "ERROR_SPEECH_TIMEOUT";
                            break;
                        case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                            description = "ERROR_RECOGNIZER_BUSY";
                            break;
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                            description = "ERROR_INSUFFICIENT_PERMISSIONS";
                            break;
                        default:
                            description = Integer.toString(error);
                    }
                    Logger.logError(LOG_TAG, "RecognitionListener#onError(" + description + ")");
                    queueu.add(STOP_ELEMENT);
                }

                @Override
                public void onEndOfSpeech() {
                    Logger.logError(LOG_TAG, "RecognitionListener#onEndOfSpeech()");
                    queueu.add(STOP_ELEMENT);
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                    // Do nothing.
                }

                @Override
                public void onBeginningOfSpeech() {
                    // Do nothing.
                }
            });

            PackageManager pm = context.getPackageManager();
            List<ResolveInfo> installedList = pm.queryIntentActivities(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
            boolean speechRecognitionInstalled = !installedList.isEmpty();

            if (!speechRecognitionInstalled) {
                // confirm
// button
// Install Button click handler
                new AlertDialog.Builder(context).setMessage("For recognition it’s necessary to install \"Google Voice Search\"")
                        .setTitle("Install Voice Search from Google Play?").setPositiveButton("Install", (dialog, which) -> {
                            Intent installIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.voicesearch"));
                            // setting flags to avoid going in application history (Activity call
                            // stack)
                            installIntent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                            context.startActivity(installIntent);
                        }).setNegativeButton("Cancel", null) // cancel button
                        .create().show();
            }

            Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Enter shell command");
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            mSpeechRecognizer.startListening(recognizerIntent);
        }

        @Override
        public void onDestroy() {
            Logger.logDebug(LOG_TAG, "onDestroy");

            super.onDestroy();
            mSpeechRecognizer.destroy();
        }

        @Override
        protected void onHandleIntent(final Intent intent) {
            Logger.logDebug(LOG_TAG, "onHandleIntent:\n" + IntentUtils.getIntentString(intent));

            ResultReturner.returnData(this, intent, new ResultReturner.WithInput() {
                @Override
                public void writeResult(PrintWriter out) throws Exception {
                    while (true) {
                        String s = queueu.take();
                        if (s == STOP_ELEMENT) {
                            return;
                        } else {
                            out.println(s);
                        }
                    }
                }
            });

        }
    }

    public static void onReceive(final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        context.startService(new Intent(context, SpeechToTextService.class).putExtras(intent.getExtras()));
    }

    public static void runFromActivity(final Activity context) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> installedList = pm.queryIntentActivities(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
        boolean speechRecognitionInstalled = !installedList.isEmpty();

        if (speechRecognitionInstalled) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Select an application"); // user hint
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1); // quantity of results we want to receive
            // context.startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);
        } else {
            // confirm
// button
// Install Button click handler
            new AlertDialog.Builder(context).setMessage("For recognition it’s necessary to install \"Google Voice Search\"")
                    .setTitle("Install Voice Search from Google Play?").setPositiveButton("Install", (dialog, which) -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.voicesearch"));
                        // setting flags to avoid going in application history (Activity call stack)
                        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                        context.startActivity(intent);
                    }).setNegativeButton("Cancel", null) // cancel button
                    .create().show();
        }
    }

}
