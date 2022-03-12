package com.termux.api.apis;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.Engine;
import android.speech.tts.TextToSpeech.EngineInfo;
import android.speech.tts.UtteranceProgressListener;
import android.util.JsonWriter;

import com.termux.api.util.ResultReturner;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.logger.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TextToSpeechAPI {

    private static final String LOG_TAG = "TextToSpeechAPI";

    public static void onReceive(final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        context.startService(new Intent(context, TextToSpeechService.class).putExtras(intent.getExtras()));
    }

    public static class TextToSpeechService extends IntentService {
        TextToSpeech mTts;
        final CountDownLatch mTtsLatch = new CountDownLatch(1);

        private static final String LOG_TAG = "TextToSpeechService";

        public TextToSpeechService() {
            super(TextToSpeechService.class.getName());
        }

        @Override
        public void onCreate() {
            Logger.logDebug(LOG_TAG, "onCreate");

            super.onCreate();
        }

        @Override
        public void onDestroy() {
            Logger.logDebug(LOG_TAG, "onDestroy");

            if (mTts != null)
                mTts.shutdown();
            super.onDestroy();
        }

        @Override
        protected void onHandleIntent(final Intent intent) {
            Logger.logDebug(LOG_TAG, "onHandleIntent:\n" + IntentUtils.getIntentString(intent));

            final String speechLanguage = intent.getStringExtra("language");
            final String speechRegion = intent.getStringExtra("region");
            final String speechVariant = intent.getStringExtra("variant");
            final String speechEngine = intent.getStringExtra("engine");
            final float speechPitch = intent.getFloatExtra("pitch", 1.0f);

            // STREAM_MUSIC is the default audio stream for TTS, see:
            // http://stackoverflow.com/questions/6877272/what-is-the-default-audio-stream-of-tts/6979025#6979025
            int streamToUseInt = AudioManager.STREAM_MUSIC;
            String streamToUseString = intent.getStringExtra("stream");
            if (streamToUseString != null) {
                switch (streamToUseString) {
                    case "NOTIFICATION":
                        streamToUseInt = AudioManager.STREAM_NOTIFICATION;
                        break;
                    case "ALARM":
                        streamToUseInt = AudioManager.STREAM_ALARM;
                        break;
                    case "MUSIC":
                        streamToUseInt = AudioManager.STREAM_MUSIC;
                        break;
                    case "RING":
                        streamToUseInt = AudioManager.STREAM_RING;
                        break;
                    case "SYSTEM":
                        streamToUseInt = AudioManager.STREAM_SYSTEM;
                        break;
                    case "VOICE_CALL":
                        streamToUseInt = AudioManager.STREAM_VOICE_CALL;
                        break;
                }
            }
            final int streamToUse = streamToUseInt;

            mTts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    mTtsLatch.countDown();
                } else {
                    Logger.logError(LOG_TAG, "Failed tts initialization: status=" + status);
                    stopSelf();
                }
            }, speechEngine);

            ResultReturner.returnData(this, intent, new ResultReturner.WithInput() {
                @Override
                public void writeResult(PrintWriter out) {

                    try {
                        try {
                            if (!mTtsLatch.await(10, TimeUnit.SECONDS)) {
                                Logger.logError(LOG_TAG, "Timeout waiting for TTS initialization");
                                return;
                            }
                        } catch (InterruptedException e) {
                            Logger.logError(LOG_TAG, "Interrupted awaiting TTS initialization");
                            return;
                        }

                        if ("LIST_AVAILABLE".equals(speechEngine)) {
                            try (JsonWriter writer = new JsonWriter(out)) {
                                writer.setIndent("  ");
                                String defaultEngineName = mTts.getDefaultEngine();
                                writer.beginArray();
                                for (EngineInfo info : mTts.getEngines()) {
                                    writer.beginObject();
                                    writer.name("name").value(info.name);
                                    writer.name("label").value(info.label);
                                    writer.name("default").value(defaultEngineName.equals(info.name));
                                    writer.endObject();
                                }
                                writer.endArray();
                            }
                            out.println();
                            return;
                        }

                        final AtomicInteger ttsDoneUtterancesCount = new AtomicInteger();

                        mTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                            @Override
                            public void onStart(String utteranceId) {
                                // Ignore.
                            }

                            @Override
                            public void onError(String utteranceId) {
                                Logger.logError(LOG_TAG, "UtteranceProgressListener.onError() called");
                                synchronized (ttsDoneUtterancesCount) {
                                    ttsDoneUtterancesCount.incrementAndGet();
                                    ttsDoneUtterancesCount.notify();
                                }
                            }

                            @Override
                            public void onDone(String utteranceId) {
                                synchronized (ttsDoneUtterancesCount) {
                                    ttsDoneUtterancesCount.incrementAndGet();
                                    ttsDoneUtterancesCount.notify();
                                }
                            }
                        });

                        if (speechLanguage != null) {
                            int setLanguageResult = mTts.setLanguage(getLocale(speechLanguage, speechRegion, speechVariant));
                            if (setLanguageResult != TextToSpeech.LANG_AVAILABLE) {
                                Logger.logError(LOG_TAG, "tts.setLanguage('" + speechLanguage + "') returned " + setLanguageResult);
                            }
                        }

                        mTts.setPitch(speechPitch);
                        mTts.setSpeechRate(intent.getFloatExtra("rate", 1.0f));

                        String utteranceId = "utterance_id";
                        Bundle params = new Bundle();
                        params.putInt(Engine.KEY_PARAM_STREAM, streamToUse);
                        params.putString(Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);

                        int submittedUtterances = 0;

                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.isEmpty()) {
                                    submittedUtterances++;
                                    mTts.speak(line, TextToSpeech.QUEUE_ADD, params, utteranceId);
                                }
                            }
                        }

                        synchronized (ttsDoneUtterancesCount) {
                            while (ttsDoneUtterancesCount.get() != submittedUtterances) {
                                ttsDoneUtterancesCount.wait();
                            }
                        }
                    } catch (Exception e) {
                        Logger.logStackTraceWithMessage(LOG_TAG, "TTS error", e);
                    }
                }
            });
        }
    }

    private static Locale getLocale(String language, String region, String variant) {
        Locale result;
        if (region != null) {
            if (variant != null) {
                result = new Locale(language, region, variant);
            } else {
                result = new Locale(language, region);
            }
        } else {
            result = new Locale(language);
        }
        return result;
    }
}
