package com.termux.api.cron;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import java.io.File;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Optional;

import static com.termux.api.TermuxAPIConstants.TERMUX_API_CRON_ALARM_SCHEME;
import static com.termux.api.TermuxAPIConstants.TERMUX_API_CRON_CONSTRAINT_SCHEME;
import static com.termux.shared.termux.TermuxConstants.TERMUX_API_PACKAGE_NAME;

public class CronEntry {

    private static final CronParser cronParser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
    private static final CronDescriptor descriptor = CronDescriptor.instance(Locale.UK);

    private final int id;
    private final String cronExpression;
    private final String scriptPath;

    private final boolean exact;

    private final NetworkType networkType;
    private final boolean batteryNotLow;
    private final boolean charging;
    private final boolean deviceIdle;
    private final boolean storageNotLow;
    private final long constraintTimeout;
    private final boolean continueOnConstraint;
    private final long maxRuntime;
    private final int gracePeriod;

    private CronEntry(int id, String cronExpression, String scriptPath, boolean exact,
                      NetworkType networkType, boolean batteryNotLow, boolean charging,
                      boolean deviceIdle, boolean storageNotLow, long constraintTimeout,
                      int gracePeriod, boolean continueOnConstraint, long maxRuntime) {
        this.id = id;
        this.cronExpression = cronExpression;
        this.scriptPath = scriptPath;
        this.exact = exact;
        this.networkType = networkType;
        this.batteryNotLow = batteryNotLow;
        this.charging = charging;
        this.deviceIdle = deviceIdle;
        this.storageNotLow = storageNotLow;
        this.constraintTimeout = constraintTimeout;
        this.continueOnConstraint = continueOnConstraint;
        this.maxRuntime = maxRuntime;
        this.gracePeriod = gracePeriod;
    }

    public int getId() {
        return id;
    }

    public String getScriptPath() {
        return scriptPath;
    }

    public boolean isExact() {
        return exact;
    }

    public long getConstraintTimeout() {
        return constraintTimeout;
    }

    public long getMaxRuntime() {
        return maxRuntime;
    }

    public boolean continueOnFailingConstraint() {
        return continueOnConstraint;
    }

    public Constraints getConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresBatteryNotLow(batteryNotLow)
                .setRequiresCharging(charging)
                .setRequiresDeviceIdle(deviceIdle)
                .setRequiresStorageNotLow(storageNotLow)
                .build();
    }

    public boolean hasNoConstraints() {
        return networkType == NetworkType.NOT_REQUIRED
                && !batteryNotLow
                && !charging
                && !deviceIdle
                && !storageNotLow;
    }

    public int getGracePeriod() {
        return gracePeriod;
    }

    public Intent getIntent(Context context, Class<? extends BroadcastReceiver> receiver) {
        Uri uri = new Uri.Builder()
                .scheme(TERMUX_API_CRON_ALARM_SCHEME)
                .appendPath("id")
                .appendPath(String.valueOf(id))
                .build();

        Intent intent = new Intent(context, receiver);
        intent.setData(uri);
        intent.putExtra(TERMUX_API_PACKAGE_NAME + ".cron.expression", cronExpression);
        intent.putExtra(TERMUX_API_PACKAGE_NAME + ".cron.script", scriptPath);

        return intent;
    }

    public Intent getTimeoutIntent(Context context, Class<? extends BroadcastReceiver> receiver) {
        Intent intent = new Intent(context, receiver);
        intent.setData(new Uri.Builder()
                .scheme(TERMUX_API_CRON_CONSTRAINT_SCHEME)
                .appendPath("id")
                .appendPath(String.valueOf(id))
                .build());

        return intent;
    }

    public long getNextExecutionTime() {
        ExecutionTime executionTime = ExecutionTime.forCron(cronParser.parse(cronExpression));
        Optional<ZonedDateTime> nextExecution = executionTime.nextExecution(ZonedDateTime.now());
        if (!nextExecution.isPresent()) {
            throw new IllegalStateException(String.format("Failing to calculate next execution time for job %d", id));
        }
        return nextExecution.get().toEpochSecond() * 1000L;
    }

    public static CronEntry fromIntent(Intent intent, int newId) {
        int id = intent.getIntExtra("job_id", newId);

        String cronExpression = getNonEmptyStringFromIntent(intent, "cron");
        Cron parse = cronParser.parse(cronExpression);
        parse.validate();

        String scriptPath = validateScriptPath(intent);

        final boolean exact = intent.getBooleanExtra("exact", false);

        String networkTypeString = intent.getStringExtra("network");
        final boolean batteryNotLow = intent.getBooleanExtra("battery_not_low", false);
        final boolean charging = intent.getBooleanExtra("charging", false);
        final boolean deviceIdle = intent.getBooleanExtra("idle", false);
        final boolean storageNotLow = intent.getBooleanExtra("storage_not_low", false);

        NetworkType networkType = networkTypeString == null
                ? NetworkType.NOT_REQUIRED
                : NetworkType.valueOf(networkTypeString.toUpperCase());

        long constraintTimeout = intent.getIntExtra("constraint_timeout", 60) * 1_000L;
        int gracePeriod = intent.getIntExtra("grace_period", 5_000);
        boolean continueOnConstraints = intent.getBooleanExtra("constraint_continue", false);
        long maxRuntime = intent.getIntExtra("max_runtime", 60) * 1_000L;

        return new CronEntry(id, cronExpression, scriptPath, exact, networkType, batteryNotLow, charging,
                deviceIdle, storageNotLow, constraintTimeout, gracePeriod, continueOnConstraints, maxRuntime);
    }

    private static String validateScriptPath(Intent intent) {
        String script = getNonEmptyStringFromIntent(intent, "script");
        String path = getNonEmptyStringFromIntent(intent, "path");

        String scriptPath;
        if (!script.startsWith("/")) {
            scriptPath = String.format("%s/%s", path, script);
        } else {
            scriptPath = script;
        }

        File file = new File(scriptPath);
        if (!file.isFile() || !file.canRead() || !file.canExecute()) {
            throw new IllegalArgumentException(scriptPath + " is either missing or cannot be executed!");
        }

        return scriptPath;
    }

    private static String getNonEmptyStringFromIntent(Intent intent, String name) {
        String stringExtra = intent.getStringExtra(name);
        if (stringExtra == null || stringExtra.isEmpty()) {
            throw new IllegalArgumentException(String.format("Parameter %s is required", name));
        }
        return stringExtra;
    }

    @NonNull
    @Override
    public String toString() {
        return CronTab.gson.toJson(this);
    }

    private String getConstraintsCrontabEntry() {
        if (hasNoConstraints() && !exact) {
            return "";
        }

        String networkString = networkType != NetworkType.NOT_REQUIRED ? networkType.toString().charAt(0) + "-" : "";
        return String.format("%s%s%s%s%s%s",
                networkString,
                batteryNotLow ? "B" : "",
                charging ? "C" : "",
                deviceIdle ? "I" : "",
                storageNotLow ? "S" : "",
                exact ? "!" : "");
    }

    public String toListEntry() {
        return String.format(Locale.getDefault(), "%4d | %15s | %8s | %s", id, cronExpression, getConstraintsCrontabEntry(), scriptPath);
    }

    public String describe() {
        String exactWarning = exact && !hasNoConstraints() ? "When constraints are used exact scheduling is probably unnecessary!\n" : "";

        String runtimeString = maxRuntime > 0 ? (maxRuntime / 1000) + " seconds" : "no limit";
        String timeoutString = constraintTimeout > 0 ? (constraintTimeout / 1000) + " seconds timeout" : "no timeout";
        String constraintContinue = continueOnConstraint ? " (running worker continues)" : "";

        String constraintString = hasNoConstraints()
                ? "none"
                : getConstraintsCrontabEntry() + constraintContinue + " " + timeoutString;

        ExecutionTime executionTime = ExecutionTime.forCron(cronParser.parse(cronExpression));
        Optional<ZonedDateTime> nextExecution = executionTime.nextExecution(ZonedDateTime.now());

        return exactWarning
                + String.format(Locale.getDefault(),
                "%s %s%n", cronExpression, scriptPath)
                + String.format(Locale.getDefault(),
                "Description: %s%n", descriptor.describe(cronParser.parse(cronExpression)))
                + String.format(Locale.getDefault(),
                "Max runtime: %s - grace period: %d msec%n", runtimeString, gracePeriod)
                + String.format(Locale.getDefault(),
                "Constraints: %s%n", constraintString)
                + String.format(Locale.getDefault(),
                "Next run   : %s", nextExecution
                        .map(z -> z.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)))
                        .orElse("???"));
    }
}
