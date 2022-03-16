package com.termux.api.apis;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.util.JsonWriter;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.termux.api.R;
import com.termux.api.util.ResultReturner;
import com.termux.api.activities.TermuxApiPermissionActivity;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.theme.ThemeUtils;
import com.termux.shared.view.KeyboardUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * API that allows receiving user input interactively in a variety of different ways
 */
public class DialogAPI {

    private static final String LOG_TAG = "DialogAPI";

    public static void onReceive(final Context context, final Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceive");

        context.startActivity(new Intent(context, DialogActivity.class).putExtras(intent.getExtras()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }



    public static class DialogActivity extends AppCompatActivity {

        private static final String LOG_TAG = "DialogActivity";

        private volatile boolean resultReturned = false;
        private InputMethod mInputMethod;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            Logger.logDebug(LOG_TAG, "onCreate");

            super.onCreate(savedInstanceState);
            final Intent intent = getIntent();
            final Context context = this;


            String methodType = intent.hasExtra("input_method") ? intent.getStringExtra("input_method") : "";

            // Set NightMode.APP_NIGHT_MODE
            TermuxThemeUtils.setAppNightMode(context);
            boolean shouldEnableDarkTheme = ThemeUtils.shouldEnableDarkTheme(this, NightMode.getAppNightMode().getName());
            if (shouldEnableDarkTheme)
                this.setTheme(R.style.DialogTheme_Dark);

            mInputMethod = InputMethodFactory.get(methodType, this);
            if (mInputMethod != null) {
                mInputMethod.create(this, result -> {
                    postResult(context, result);
                    finish();
                });
            } else {
                InputResult result = new InputResult();
                result.error = "Unknown Input Method: " + methodType;
                postResult(context, result);
            }
        }

        @Override
        protected void onNewIntent(Intent intent) {
            Logger.logDebug(LOG_TAG, "onNewIntent");

            super.onNewIntent(intent);
            setIntent(intent);
        }

        @Override
        protected void onDestroy() {
            Logger.logDebug(LOG_TAG, "onDestroy");

            super.onDestroy();

            postResult(this, null);

            if (mInputMethod != null) {
                Dialog dialog = mInputMethod.getDialog();
                dismissDialog(dialog);
            }
        }

        private static void dismissDialog(Dialog dialog) {
            try {
                if (dialog != null)
                    dialog.dismiss();
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed tp dismiss dialog", e);
            }
        }

        /**
         * Extract value extras from intent into String array
         */
        static String[] getInputValues(Intent intent) {
            String[] items = new String[] { };

            if (intent != null && intent.hasExtra("input_values")) {
                String[] temp = intent.getStringExtra("input_values").split("(?<!\\\\),");
                items = new String[temp.length];

                // remove possible whitespace from strings in temp array
                for (int j = 0; j < temp.length; ++j) {
                    String s = temp[j];
                    items[j] = s.trim().replace("\\,", ",");
                }
            }
            return items;
        }

        /**
         * Writes the InputResult to the console
         */
        protected synchronized void postResult(final Context context, final InputResult resultParam) {
            if (resultReturned) {
                Logger.logDebug(LOG_TAG, "Ignoring call to postResult");
                return;
            } else {
                Logger.logDebug(LOG_TAG, "postResult");
            }

            ResultReturner.returnData(context, getIntent(), new ResultReturner.ResultJsonWriter() {

                @Override
                public void writeJson(JsonWriter out) throws Exception {
                    out.beginObject();

                    InputResult result = resultParam;
                    if (result == null) {
                        result = new InputResult();
                        result.code = Dialog.BUTTON_NEGATIVE;
                    }

                    out.name("code").value(result.code);
                    out.name("text").value(result.text);
                    if(result.index > -1) {
                        out.name("index").value(result.index);
                    }
                    if (result.values.size() > 0) {
                        out.name("values");
                        out.beginArray();
                        for (Value value : result.values) {
                            out.beginObject();
                            out.name("index").value(value.index);
                            out.name("text").value(value.text);
                            out.endObject();
                        }
                        out.endArray();
                    }
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

            public static InputMethod get(final String type, final AppCompatActivity activity) {

                switch (type == null ? "" : type) {
                    case "confirm":
                        return new ConfirmInputMethod(activity);
                    case "checkbox":
                        return new CheckBoxInputMethod(activity);
                    case "counter":
                        return new CounterInputMethod(activity);
                    case "date":
                        return new DateInputMethod(activity);
                    case "radio":
                        return new RadioInputMethod(activity);
                    case "sheet":
                        return new BottomSheetInputMethod();
                    case "speech":
                        return new SpeechInputMethod(activity);
                    case "spinner":
                        return new SpinnerInputMethod(activity);
                    case "text":
                        return new TextInputMethod(activity);
                    case "time":
                        return new TimeInputMethod(activity);
                    default:
                        return null;
                }
            }
        }


        /**
         * Interface for creating an input method type
         */
        interface InputMethod {
            Dialog getDialog();

            void create(AppCompatActivity activity, InputResultListener resultListener);
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
            public static int index = -1;
            public List<Value> values = new ArrayList<>();
        }


        public static class Value {
            public int index = -1;
            public String text = "";
        }

        /*
         * --------------------------------------
         * InputMethod Implementations
         * --------------------------------------
         */


        /**
         * CheckBox InputMethod
         * Allow users to select multiple options from a range of values
         */
        static class CheckBoxInputMethod extends InputDialog<LinearLayout> {

            CheckBoxInputMethod(AppCompatActivity activity) {
                super(activity);
            }

            @Override
            LinearLayout createWidgetView(AppCompatActivity activity) {
                LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                layoutParams.topMargin = 32;
                layoutParams.bottomMargin = 32;

                String[] values = getInputValues(activity.getIntent());

                for (int j = 0; j < values.length; ++j) {
                    String value = values[j];

                    CheckBox checkBox = new CheckBox(activity);
                    checkBox.setText(value);
                    checkBox.setId(j);
                    checkBox.setTextSize(18);
                    checkBox.setPadding(16, 16, 16, 16);
                    checkBox.setLayoutParams(layoutParams);

                    layout.addView(checkBox);
                }
                return layout;
            }

            @Override
            String getResult() {
                int checkBoxCount = widgetView.getChildCount();

                List<Value> values = new ArrayList<>();
                StringBuilder sb = new StringBuilder();
                sb.append("[");

                for (int j = 0; j < checkBoxCount; ++j) {
                    CheckBox box = widgetView.findViewById(j);
                    if (box.isChecked()) {
                        Value value = new Value();
                        value.index = j;
                        value.text = box.getText().toString();
                        values.add(value);
                        sb.append(box.getText().toString()).append(", ");
                    }
                }
                inputResult.values = values;
                // remove trailing comma and add closing bracket
                return sb.toString().replaceAll(", $", "") + "]";
            }
        }


        /**
         * Confirm InputMethod
         * Allow users to confirm YES or NO.
         */
        static class ConfirmInputMethod extends InputDialog<TextView> {

            ConfirmInputMethod(AppCompatActivity activity) {
                super(activity);
            }

            @Override
            InputResult onDialogClick(int button) {
                inputResult.text = button == Dialog.BUTTON_POSITIVE ? "yes" : "no";
                return inputResult;
            }

            @Override
            TextView createWidgetView(AppCompatActivity activity) {
                TextView textView = new TextView(activity);
                final Intent intent = activity.getIntent();

                String text = intent.hasExtra("input_hint") ? intent.getStringExtra("input_hint") : "Confirm";
                textView.setText(text);
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
         * Counter InputMethod
         * Allow users to increment or decrement a number in a given range
         */
        static class CounterInputMethod extends InputDialog<View> {
            static final int DEFAULT_MIN = 0;
            static final int DEFAULT_MAX = 100;
            static final int RANGE_LENGTH = 3;

            int min;
            int max;
            int counter;

            TextView counterLabel;

            CounterInputMethod(AppCompatActivity activity) {
                super(activity);
            }

            @Override
            View createWidgetView(AppCompatActivity activity) {
                View layout = View.inflate(activity, R.layout.dialog_counter, null);
                counterLabel = layout.findViewById(R.id.counterTextView);

                final Button incrementButton = layout.findViewById(R.id.incrementButton);
                incrementButton.setOnClickListener(view -> increment());

                final Button decrementButton = layout.findViewById(R.id.decrementButton);
                decrementButton.setOnClickListener(view -> decrement());
                updateCounterRange();

                return layout;
            }

            void updateCounterRange() {
                final Intent intent = activity.getIntent();

                if (intent.hasExtra("input_range")) {
                    int[] values = intent.getIntArrayExtra("input_range");
                    if (values.length != RANGE_LENGTH) {
                        inputResult.error = "Invalid range! Must be 3 int values!";
                        postCanceledResult();
                        dismissDialog(dialog);
                    } else {
                        min = Math.min(values[0], values[1]);
                        max = Math.max(values[0], values[1]);
                        counter = values[2];
                    }
                } else {
                    min = DEFAULT_MIN;
                    max = DEFAULT_MAX;

                    // halfway
                    counter = (DEFAULT_MAX - DEFAULT_MIN) /  2;
                }
                updateLabel();
            }

            @Override
            String getResult() {
                return counterLabel.getText().toString();
            }

            void updateLabel() {
                counterLabel.setText(String.valueOf(counter));
            }

            void increment() {
                if ((counter + 1) <= max) {
                    ++counter;
                    updateLabel();
                }
            }

            void decrement() {
                if ((counter - 1) >= min) {
                    --counter;
                    updateLabel();
                }
            }
        }


        /**
         * Date InputMethod
         * Allow users to pick a specific date
         */
        static class DateInputMethod extends InputDialog<DatePicker> {

            DateInputMethod(AppCompatActivity activity) {
                super(activity);
            }

            @Override
            String getResult() {
                int month = widgetView.getMonth();
                int day = widgetView.getDayOfMonth();
                int year = widgetView.getYear();

                Calendar calendar = Calendar.getInstance();
                calendar.set(year, month, day, 0, 0, 0);

                final Intent intent = activity.getIntent();
                if (intent.hasExtra("date_format")) {
                    String date_format = intent.getStringExtra("date_format");
                    try {
                        SimpleDateFormat dateFormat = new SimpleDateFormat(date_format);
                        dateFormat.setTimeZone(calendar.getTimeZone());
                        return dateFormat.format(calendar.getTime());
                    } catch (Exception e) {
                        inputResult.error = e.toString();
                        postCanceledResult();
                    }
                }
                return calendar.getTime().toString();
            }

            @Override
            DatePicker createWidgetView(AppCompatActivity activity) {
                return new DatePicker(activity);
            }
        }


        /**
         * Text InputMethod
         * Allow users to enter plaintext or a password
         */
        static class TextInputMethod extends InputDialog<EditText> {

            TextInputMethod(AppCompatActivity activity) {
                super(activity);
            }

            @Override
            String getResult() {
                return widgetView.getText().toString();
            }

            @Override
            EditText createWidgetView(AppCompatActivity activity) {
                final Intent intent = activity.getIntent();
                EditText editText = new EditText(activity);

                if (intent.hasExtra("input_hint")) {
                    editText.setHint(intent.getStringExtra("input_hint"));
                }

                boolean multiLine = intent.getBooleanExtra("multiple_lines", false);
                boolean numeric = intent.getBooleanExtra("numeric", false);
                boolean password = intent.getBooleanExtra("password", false);

                int flags = InputType.TYPE_CLASS_TEXT;

                if (password) {
                    flags = numeric ? (flags | InputType.TYPE_NUMBER_VARIATION_PASSWORD) : (flags | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }

                if (multiLine) {
                    flags |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
                    editText.setLines(4);
                }

                if (numeric) {
                    flags &= ~InputType.TYPE_CLASS_TEXT; // clear to allow only numbers
                    flags |= InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL;
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

            TimeInputMethod(AppCompatActivity activity) {
                super(activity);
            }

            @Override
            String getResult() {
                return String.format(Locale.getDefault(), "%02d:%02d", widgetView.getHour(), widgetView.getMinute());
            }

            @Override
            TimePicker createWidgetView(AppCompatActivity activity) {
                return new TimePicker(activity);
            }
        }


        /**
         * Radio InputMethod
         * Allow users to confirm from radio button options
         */
        static class RadioInputMethod extends InputDialog<RadioGroup> {
            RadioGroup radioGroup;

            RadioInputMethod(AppCompatActivity activity) {
                super(activity);
            }

            @Override
            RadioGroup createWidgetView(AppCompatActivity activity) {
                radioGroup = new RadioGroup(activity);
                radioGroup.setPadding(16, 16, 16, 16);

                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                layoutParams.topMargin = 32;
                layoutParams.bottomMargin = 32;

                String[] values = getInputValues(activity.getIntent());

                for (int j = 0; j < values.length; ++j) {
                    String value = values[j];

                    RadioButton button = new RadioButton(activity);
                    button.setText(value);
                    button.setId(j);
                    button.setTextSize(18);
                    button.setPadding(16, 16, 16, 16);
                    button.setLayoutParams(layoutParams);

                    radioGroup.addView(button);
                }
                return radioGroup;
            }

            @Override
            String getResult() {
                int radioIndex = radioGroup.indexOfChild(widgetView.findViewById(radioGroup.getCheckedRadioButtonId()));
                RadioButton radioButton = (RadioButton) radioGroup.getChildAt(radioIndex);
                InputResult.index = radioIndex;
                return (radioButton != null) ? radioButton.getText().toString() : "";
            }
        }


        /**
         * BottomSheet InputMethod
         * Allow users to select from a variety of options in a bottom sheet dialog
         */
        public static class BottomSheetInputMethod extends BottomSheetDialogFragment implements InputMethod {
            private InputResultListener resultListener;


            @Override
            public void create(AppCompatActivity activity, InputResultListener resultListener) {
                this.resultListener = resultListener;
                show(activity.getSupportFragmentManager(), "BOTTOM_SHEET");
            }

            @NonNull
            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                // create custom BottomSheetDialog that has friendlier dismissal behavior
                return new BottomSheetDialog(requireActivity(), getTheme()) {
                    @Override
                    public void onBackPressed() {
                        super.onBackPressed();
                        // make it so that user only has to hit back key one time to get rid of bottom sheet
                        requireActivity().onBackPressed();
                        postCanceledResult();
                    }

                    @Override
                    public void cancel() {
                        super.cancel();

                        if (isCurrentAppTermux()) {
                            showKeyboard();
                        }
                        // dismiss on single touch outside of dialog
                        requireActivity().onBackPressed();
                        postCanceledResult();
                    }
                };
            }

            @SuppressLint("RestrictedApi")
            @Override
            public void setupDialog(final Dialog dialog, int style) {
                LinearLayout layout = new LinearLayout(getContext());
                layout.setMinimumHeight(100);
                layout.setPadding(16, 16, 16, 16);
                layout.setOrientation(LinearLayout.VERTICAL);

                NestedScrollView scrollView = new NestedScrollView(requireContext());
                final String[] values = getInputValues(requireActivity().getIntent());

                for (int i = 0; i < values.length; ++i) {
                    final int j = i;
                    final TextView textView = new TextView(getContext());
                    textView.setText(values[j]);
                    textView.setTextSize(20);
                    textView.setPadding(56, 56, 56, 56);
                    textView.setOnClickListener(view -> {
                        InputResult result = new InputResult();
                        result.text = values[j];
                        result.index = j;
                        dismissDialog(dialog);
                        resultListener.onResult(result);
                    });

                    layout.addView(textView);
                }
                scrollView.addView(layout);
                dialog.setContentView(scrollView);
                hideKeyboard();
            }

            /**
             * These keyboard methods exist to work around inconsistent show / hide behavior
             * from canceling BottomSheetDialog and produces the desired result of hiding keyboard
             * on creation of dialog and showing it after a selection or cancellation, as long as
             * we are still within the Termux application
             */

            protected void hideKeyboard() {
                KeyboardUtils.setSoftKeyboardAlwaysHiddenFlags(getActivity());
            }

            protected void showKeyboard() {
                KeyboardUtils.showSoftKeyboard(getActivity(), getView());
            }

            /**
             * Checks to see if foreground application is Termux
             */
            protected boolean isCurrentAppTermux() {
                final ActivityManager activityManager = (ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
                final List<ActivityManager.RunningAppProcessInfo> runningProcesses = Objects.requireNonNull(activityManager).getRunningAppProcesses();
                for (final ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
                    if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        for (final String activeProcess : processInfo.pkgList) {
                            if (activeProcess.equals(TermuxConstants.TERMUX_PACKAGE_NAME)) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }

            protected void postCanceledResult() {
                InputResult result = new InputResult();
                result.code = Dialog.BUTTON_NEGATIVE;
                resultListener.onResult(result);
            }
        }


        /**
         * Spinner InputMethod
         * Allow users to make a selection based on a list of specified values
         */
        static class SpinnerInputMethod extends InputDialog<Spinner> {

            SpinnerInputMethod(AppCompatActivity activity) {
                super(activity);
            }

            @Override
            String getResult() {
                InputResult.index = widgetView.getSelectedItemPosition();
                return widgetView.getSelectedItem().toString();
            }

            @Override
            Spinner createWidgetView(AppCompatActivity activity) {
                Spinner spinner = new Spinner(activity);

                final Intent intent = activity.getIntent();
                final String[] items = getInputValues(intent);
                final ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, R.layout.spinner_item, items);

                spinner.setAdapter(adapter);
                return spinner;
            }
        }


        /**
         * Speech InputMethod
         * Allow users to use the built in microphone to get text from speech
         */
        static class SpeechInputMethod extends InputDialog<TextView> {

            SpeechInputMethod(AppCompatActivity activity) {
                super(activity);
            }

            @Override
            TextView createWidgetView(AppCompatActivity activity) {
                TextView textView = new TextView(activity);
                final Intent intent = activity.getIntent();

                String text = intent.hasExtra("input_hint") ? intent.getStringExtra("input_hint") : "Listening for speech...";

                textView.setText(text);
                textView.setTextSize(20);
                return textView;
            }

            @Override
            public void create(final AppCompatActivity activity, final InputResultListener resultListener) {
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
                DialogInterface.OnClickListener clickListener = getClickListener(result -> {
                    recognizer.stopListening();
                    resultListener.onResult(result);
                });

                Dialog dialog = getDialogBuilder(activity, clickListener)
                        .setPositiveButton(null, null)
                        .setOnDismissListener(null)
                        .create();

                dialog.setCanceledOnTouchOutside(false);
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

            private SpeechRecognizer createSpeechRecognizer(AppCompatActivity activity, final InputResultListener listener) {
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
                     */
                    @Override
                    public void onError(int error) {
                        String errorDescription;

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
                            default:
                                errorDescription = "ERROR_UNKNOWN";
                                break;
                        }
                        inputResult.error = errorDescription;
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

            // listener for our input result
            InputResultListener resultListener;

            // view that will be placed in our dialog
            T widgetView;

            // dialog that holds everything
            Dialog dialog;

            // our activity context
            AppCompatActivity activity;


            // method to be implemented that handles creating view that is placed in our dialog
            abstract T createWidgetView(AppCompatActivity activity);

            // method that should be implemented that handles returning a result obtained through user input
            String getResult() {
                return null;
            }


            InputDialog(AppCompatActivity activity) {
                this.activity = activity;
                widgetView = createWidgetView(activity);
                initActivityDisplay(activity);
            }

            @Override
            public Dialog getDialog() {
                return dialog;
            }

            @Override
            public void create(AppCompatActivity activity, final InputResultListener resultListener) {
                this.resultListener = resultListener;

                // Handle OK and Cancel button clicks
                DialogInterface.OnClickListener clickListener = getClickListener(resultListener);

                // Dialog interface that will display to user
                dialog = getDialogBuilder(activity, clickListener).create();
                dialog.show();
            }

            void postCanceledResult() {
                inputResult.code = Dialog.BUTTON_NEGATIVE;
                resultListener.onResult(inputResult);
            }

            void initActivityDisplay(Activity activity) {
                activity.setFinishOnTouchOutside(false);
                activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
            }

            /**
             * Places our generic widget view type inside a FrameLayout
             */
            View getLayoutView(AppCompatActivity activity, T view) {
                FrameLayout layout = getFrameLayout(activity);
                ViewGroup.LayoutParams params = layout.getLayoutParams();

                view.setLayoutParams(params);
                layout.addView(view);
                layout.setScrollbarFadingEnabled(false);

                // wrap everything in scrollview
                ScrollView scrollView = new ScrollView(activity);
                scrollView.addView(layout);

                return scrollView;
            }

            DialogInterface.OnClickListener getClickListener(final InputResultListener listener) {
                return (dialogInterface, button) -> {
                    InputResult result = onDialogClick(button);
                    listener.onResult(result);
                };
            }

            DialogInterface.OnDismissListener getDismissListener() {
                return dialogInterface -> {
                    // force dismiss behavior on single tap outside of dialog
                    activity.onBackPressed();
                    onDismissed();
                };
            }

            /**
             * Creates a dialog builder to initialize a dialog w/ a view and button click listeners
             */
            AlertDialog.Builder getDialogBuilder(AppCompatActivity activity, DialogInterface.OnClickListener clickListener) {
                final Intent intent = activity.getIntent();
                final View layoutView = getLayoutView(activity, widgetView);

                return new AlertDialog.Builder(activity)
                        .setTitle(intent.hasExtra("input_title") ? intent.getStringExtra("input_title") : "")
                        .setNegativeButton(getNegativeButtonText(), clickListener)
                        .setPositiveButton(getPositiveButtonText(), clickListener)
                        .setOnDismissListener(getDismissListener())
                        .setView(layoutView);

            }

            String getNegativeButtonText() {
                return "Cancel";
            }

            String getPositiveButtonText() {
                return "OK";
            }

            void onDismissed() {
                postCanceledResult();
            }

            /**
             * Create a basic frame layout that will add a margin around our main widget view
             */
            FrameLayout getFrameLayout(AppCompatActivity activity) {
                FrameLayout layout = new FrameLayout(activity);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

                final int margin = 56;
                params.setMargins(margin, margin, margin, margin);

                params.setMargins(56, 56, 56, 56);
                layout.setLayoutParams(params);
                return layout;
            }

            /**
             * Returns an InputResult containing code of our button and the text if we hit OK
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

}
