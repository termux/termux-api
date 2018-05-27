package com.termux.api;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.util.JsonWriter;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.TermuxApiPermissionActivity;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * API that allows receiving user input interactively in a variety of different ways
 */
public class DialogActivity extends Activity {

    static boolean resultReturned = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        final Context context = this;


        String methodType = intent.hasExtra("input_method") ? intent.getStringExtra("input_method") : "";

        InputMethod method = InputMethodFactory.get(methodType, this);
        method.create(this, new InputResultListener() {
            @Override
            public void onResult(final InputResult result) {
                postResult(context, result);
                finish();
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

        if (!resultReturned) {
            postResult(this, null);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (!resultReturned) {
            postResult(this, null);
        }
    }

    /**
     * Writes the InputResult to the console
     * @param context
     * @param result
     */
    protected void postResult(final Context context, final InputResult result) {
        ResultReturner.returnData(context, getIntent(), new ResultReturner.ResultJsonWriter() {

            @Override
            public void writeJson(JsonWriter out) throws Exception {
                out.beginObject();

                out.name("code").value(result.code);
                out.name("text").value(result.text);

                if (!result.error.equals("")) {
                    out.name("error").value(result.error);
                }

                out.endObject();
                out.flush();
                resultReturned = true;
            }
        });
    }


    /**
     * Factory for returning proper input method type that we received in our incoming intent
     */
    static class InputMethodFactory {

        public static InputMethod get(final String type, final Activity activity) {

            switch (type == null ? "" : type) {
                case "confirm":
                    return new ConfirmInputMethod(activity);
                case "date":
                    return new DateInputMethod(activity);
                case "text":
                    return new TextInputMethod(activity);
                case "time":
                    return new TimeInputMethod(activity);
                case "speech":
                    return new SpeechInputMethod(activity);
                case "spinner":
                    return new SpinnerInputMethod(activity);
                default:
                    return new InputMethod() {
                        @Override
                        public void create(Activity activity, InputResultListener resultListener) {
                            InputResult result = new InputResult();
                            result.error = "Unknown Input Method: " + type;
                            resultListener.onResult(result);
                        }
                    };
            }
        }
    }


    /**
     * Interface for creating an input method type
     */
    interface InputMethod {
        void create(Activity activity, InputResultListener resultListener);
    }


    /**
     * Callback interface for receiving an InputResult
     */
    interface InputResultListener {
        void onResult(InputResult result);
    }


    /**
     * Simple POJO to store the result of input methods
     */
    static class InputResult {
        public String text = "";
        public String error = "";
        public int code = 0;
    }


    /**
     * --------------------------------------
     * InputMethod Implementations
     * --------------------------------------
     */


    /**
     * Confirm InputMethod
     * Allow users to confirm YES or NO.
     */
    static class ConfirmInputMethod extends InputDialog<TextView> {

        ConfirmInputMethod(Activity activity) {
            super(activity);
        }

        @Override
        InputResult onDialogClick(int button) {
            inputResult.text = button == Dialog.BUTTON_POSITIVE ? "yes" : "no";
            return inputResult;
        }

        @Override
        TextView createWidgetView(Activity activity) {
            TextView textView = new TextView(activity);
            // TODO make this an option for the user to set?
            textView.setText("Confirm");
            return textView;
        }

        @Override
        String getNegativeButtonText() {
            return "No";
        }

        @Override
        String getPositiveButtonText() {
            return "Yes";
        }
    }


    /**
     * Date InputMethod
     * Allow users to pick a specific date
     */
    static class DateInputMethod extends InputDialog<DatePicker> {

        DateInputMethod(Activity activity) {
            super(activity);
        }

        @Override
        String getResult() {
            int month = widgetView.getMonth();
            int day = widgetView.getDayOfMonth();
            int year = widgetView.getYear();

            Calendar calendar = Calendar.getInstance();
            calendar.set(year, month, day, 0, 0, 0);

            return calendar.getTime().toString();
        }

        @Override
        DatePicker createWidgetView(Activity activity) {
            return new DatePicker(activity);
        }
    }


    /**
     * Text InputMethod
     * Allow users to enter plaintext or a password
     */
    static class TextInputMethod extends InputDialog<EditText> {

        TextInputMethod(Activity activity) {
            super(activity);
        }

        @Override
        String getResult() {
            return widgetView.getText().toString();
        }

        @Override
        EditText createWidgetView(Activity activity) {
            final Intent intent = activity.getIntent();
            EditText editText = new EditText(activity);

            if (intent.hasExtra("input_hint")) {
                editText.setHint(intent.getStringExtra("input_hint"));
            }

            boolean multiLine = intent.getBooleanExtra("multiple_lines", false);
            String type = intent.hasExtra("input_type") ? intent.getStringExtra("input_type") : "";

            int flags = InputType.TYPE_CLASS_TEXT;

            if (type.equalsIgnoreCase("password")) {
                flags |= InputType.TYPE_TEXT_VARIATION_PASSWORD;
            } else if (multiLine) {
                flags |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
                editText.setLines(4);
            }
            editText.setInputType(flags);

            return editText;
        }
    }


    /**
     * Time InputMethod
     * Allow users to pick a specific time
     */
    static class TimeInputMethod extends InputDialog<TimePicker> {

        TimeInputMethod(Activity activity) {
            super(activity);
        }

        @Override
        String getResult() {
            String result;

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                result = String.format(Locale.getDefault(), "%02d:%02d", widgetView.getHour(), widgetView.getMinute());
            } else {
                result = String.format(Locale.getDefault(), "%02d:%02d", widgetView.getCurrentHour(), widgetView.getCurrentMinute());
            }
            return result;
        }

        @Override
        TimePicker createWidgetView(Activity activity) {
            return new TimePicker(activity);
        }
    }


    /**
     * Spinner InputMethod
     * Allow users to make a selection based on a list of specified values
     */
    static class SpinnerInputMethod extends InputDialog<Spinner> {

        SpinnerInputMethod(Activity activity) {
            super(activity);
        }

        @Override
        String getResult() {
            return widgetView.getSelectedItem().toString();
        }

        @Override
        Spinner createWidgetView(Activity activity) {
            Spinner spinner = new Spinner(activity);

            final Intent intent = activity.getIntent();
            final String[] items = getSpinnerItems(intent);
            final ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, R.layout.spinner_item, items);

            spinner.setAdapter(adapter);
            return spinner;
        }

        String[] getSpinnerItems(Intent intent) {
            String[] items = new String[] { };

            if (intent.hasExtra("input_values")) {
                String[] temp = intent.getStringExtra("input_values").split(",");
                items = new String[temp.length];

                // remove possible whitespace from strings in temp array
                for (int j = 0; j < temp.length; ++j) {
                    String s = temp[j];
                    items[j] = s.trim();
                }
            }
            return items;
        }
    }


    /**
     * Speech InputMethod
     * Allow users to use the built in microphone to get text from speech
     */
    static class SpeechInputMethod extends InputDialog<TextView> {

        SpeechInputMethod(Activity activity) {
            super(activity);
        }

        @Override
        TextView createWidgetView(Activity activity) {
            TextView textView = new TextView(activity);
            textView.setText("Listening for speech...");
            textView.setTextSize(20);
            return textView;
        }

        @Override
        public void create(final Activity activity, final InputResultListener resultListener) {
            // Since we're using the microphone, we need to make sure we have proper permission
            if (!TermuxApiPermissionActivity.checkAndRequestPermissions(activity, activity.getIntent(), Manifest.permission.RECORD_AUDIO)) {
                activity.finish();
            }

            if (!hasSpeechRecognizer(activity)) {
                Toast.makeText(activity, "No voice recognition found!", Toast.LENGTH_SHORT).show();
                activity.finish();
            }


            Intent speechIntent = createSpeechIntent();
            final SpeechRecognizer recognizer = createSpeechRecognizer(activity, resultListener);

            // create intermediate InputResultListener so that we can stop our speech listening
            // if user hits the cancel button
            DialogInterface.OnClickListener clickListener = getClickListener(new InputResultListener() {
                @Override
                public void onResult(InputResult result) {
                    recognizer.stopListening();
                    resultListener.onResult(result);
                }
            });

            Dialog dialog = getDialogBuilder(activity, clickListener)
                    .setPositiveButton(null, null)
                    .create();

            dialog.show();

            recognizer.startListening(speechIntent);
        }

        private boolean hasSpeechRecognizer(Context context) {
            List<ResolveInfo> installList = context.getPackageManager().queryIntentActivities(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
            return !installList.isEmpty();
        }

        private Intent createSpeechIntent() {
            Intent speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            return speechIntent;
        }

        private SpeechRecognizer createSpeechRecognizer(Activity activity, final InputResultListener listener) {
            SpeechRecognizer recognizer = SpeechRecognizer.createSpeechRecognizer(activity);
            recognizer.setRecognitionListener(new RecognitionListener() {

                @Override
                public void onResults(Bundle results) {
                    List<String> voiceResults = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                    if (voiceResults != null && voiceResults.size() > 0) {
                        inputResult.text = voiceResults.get(0);
                    }
                    listener.onResult(inputResult);
                }

                /**
                 * Get string description for error code
                 * @param error
                 */
                @Override
                public void onError(int error) {
                    String errorDescription = null;

                    switch (error) {
                        case SpeechRecognizer.ERROR_AUDIO:
                            errorDescription = "ERROR_AUDIO";
                            break;
                        case SpeechRecognizer.ERROR_CLIENT:
                            errorDescription = "ERROR_CLIENT";
                            break;
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                            errorDescription = "ERROR_INSUFFICIENT_PERMISSIONS";
                            break;
                        case SpeechRecognizer.ERROR_NETWORK:
                            errorDescription = "ERROR_NETWORK";
                            break;
                        case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                            errorDescription = "ERROR_NETWORK_TIMEOUT";
                            break;
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                            errorDescription = "ERROR_SPEECH_TIMEOUT";
                            break;
                    }

                    if (errorDescription != null) {
                        inputResult.error = errorDescription;
                    }

                    listener.onResult(inputResult);
                }


                // unused
                @Override
                public void onEndOfSpeech() { }

                @Override
                public void onReadyForSpeech(Bundle bundle) { }

                @Override
                public void onBeginningOfSpeech() { }

                @Override
                public void onRmsChanged(float v) { }

                @Override
                public void onBufferReceived(byte[] bytes) { }

                @Override
                public void onPartialResults(Bundle bundle) { }

                @Override
                public void onEvent(int i, Bundle bundle) { }
            });
            return recognizer;
        }
    }


    /**
     * Base Dialog class to extend from for adding specific views / widgets to a Dialog interface
     * @param <T> Main view type that will be displayed within dialog
     */
    abstract static class InputDialog<T extends View> implements InputMethod {
        // result that belongs to us
        InputResult inputResult = new InputResult();

        // view that will be placed in our dialog
        T widgetView;

        // method to be implemented that handles creating view that is placed in our dialog
        abstract T createWidgetView(Activity activity);

        // method that should be implemented that handles returning a result obtained through user input
        String getResult() {
            return null;
        }


        InputDialog(Activity activity) {
            widgetView = createWidgetView(activity);
            initActivityDisplay(activity:w);
        }


        @Override
        public void create(Activity activity, final InputResultListener resultListener) {
            // Handle OK and Cancel button clicks
            DialogInterface.OnClickListener clickListener = getClickListener(resultListener);

            // Dialog interface that will display to user
            Dialog dialog = getDialogBuilder(activity, clickListener).create();
            dialog.show();
        }

        void initActivityDisplay(Activity activity) {
            activity.setFinishOnTouchOutside(false);
            activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
        }

        /**
         * Places our generic widget view type inside a FrameLayout
         * @param activity
         * @param view
         * @return
         */
        View getLayoutView(Activity activity, T view) {
            FrameLayout layout = getFrameLayout(activity);
            ViewGroup.LayoutParams params = layout.getLayoutParams();
            view.setLayoutParams(params);
            layout.addView(view);
            return layout;
        }

        DialogInterface.OnClickListener getClickListener(final InputResultListener listener) {
            return new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int button) {
                            InputResult result = onDialogClick(button);
                            listener.onResult(result);
                        }
                    };
        }

        /**
         * Creates a dialog builder to initialize a dialog w/ a view and button click listeners
         * @param activity
         * @param clickListener
         * @return
         */
        AlertDialog.Builder getDialogBuilder(Activity activity, DialogInterface.OnClickListener clickListener) {
            final Intent intent = activity.getIntent();
            final View layoutView = getLayoutView(activity, widgetView);

            return new AlertDialog.Builder(activity)
                    .setTitle(intent.hasExtra("input_title") ? intent.getStringExtra("input_title") : "")
                    .setNegativeButton(getNegativeButtonText(), clickListener)
                    .setPositiveButton(getPositiveButtonText(), clickListener)
                    .setView(layoutView);

        }

        String getNegativeButtonText() {
            return "Cancel";
        }

        String getPositiveButtonText() {
            return "OK";
        }

        /**
         * Create a basic frame layout that will add a margin around our main widget view
         * @param activity
         * @return
         */
        FrameLayout getFrameLayout(Activity activity) {
            FrameLayout layout = new FrameLayout(activity);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

            final int margin = 56;
            params.setMargins(margin, margin, margin, margin);

            layout.setLayoutParams(params);
            return layout;
        }

        /**
         * Returns an InputResult containing code of our button and the text if we hit OK
         * @param button
         * @return
         */
        InputResult onDialogClick(int button) {
            // receive indication of whether the OK or CANCEL button is clicked
            inputResult.code = button;

            // OK clicked
            if (button == Dialog.BUTTON_POSITIVE) {
                inputResult.text = getResult();
            }
            return inputResult;
        }
    }
}
