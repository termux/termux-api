package com.termux.api.cron;

import android.content.Intent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

public class CronTab {

    private static final String CRON_TAB_JSON_FILE = TermuxConstants.TERMUX_HOME_DIR_PATH + "/.crontab.json";
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    private static final String LOG_TAG = "CronTab";

    static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private CronTab() {
        /* static class */
    }

    public static List<CronEntry> clear() {
        List<CronEntry> cronEntries = loadFromFile();
        saveToFile(Collections.emptyList());
        return cronEntries;
    }

    public static CronEntry add(Intent intent) {
        List<CronEntry> cronEntries = loadFromFile();

        int id = cronEntries.stream()
                .mapToInt(CronEntry::getId)
                .max()
                .orElse(0) + 1;

        CronEntry entry = CronEntry.fromIntent(intent, id);
        cronEntries.add(entry);
        saveToFile(cronEntries);
        return entry;
    }

    public static List<CronEntry> getAll() {
        return loadFromFile();
    }

    public static CronEntry delete(int id) {
        List<CronEntry> cronEntries = loadFromFile();
        CronEntry entry = getByIdOrNull(id, cronEntries);
        if (entry == null) {
            return null;
        } else {
            cronEntries.remove(entry);
            saveToFile(cronEntries);
            return entry;
        }
    }

    public static CronEntry getById(int id) {
        List<CronEntry> cronEntries = loadFromFile();
        CronEntry entry = getByIdOrNull(id, cronEntries);
        if (entry == null) {
            Logger.logWarn(LOG_TAG, String.format("Job with id %s not found!", id));
            return null;
        }
        return entry;
    }

    public static String print() {
        List<CronEntry> entries = loadFromFile();

        if (entries.isEmpty()) {
            return "==== empty ====";
        }

        StringJoiner sj = new StringJoiner("\n");
        sj.add("Constraints: C- connected | U- unmetered | N- notRoaming | M- metered");
        sj.add("B=batteryNotLow C=charging I=deviceIdle S=storageNotLow !=exact");
        sj.add("");
        sj.add(String.format("%4s | %15s | %8s | %s", "id", "cron", "constr", "script"));
        for (CronEntry entry : entries) {
            sj.add(entry.toListEntry());
        }
        return sj.toString();
    }

    private static CronEntry getByIdOrNull(int id, List<CronEntry> entries) {
        return entries.stream()
                .filter(e -> e.getId() == id)
                .findFirst()
                .orElse(null);
    }

    private static List<CronEntry> loadFromFile() {
        try {
            File file = new File(CRON_TAB_JSON_FILE);
            if (!file.exists()) {
                return Collections.emptyList();
            }

            String json = getTextFileContents(file);
            Type listType = (new TypeToken<List<CronEntry>>() {
            }).getType();
            return gson.fromJson(json, listType);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void saveToFile(List<CronEntry> entries) {
        File file = new File(CRON_TAB_JSON_FILE);
        String json = gson.toJson(entries);
        try {
            writeStringToFile(file, json);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String getTextFileContents(File file) throws IOException {
        try (
                FileInputStream fileInputStream = new FileInputStream(file);
                InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, DEFAULT_CHARSET);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader)
        ) {
            StringJoiner sj = new StringJoiner("\n");

            String line = bufferedReader.readLine();
            while (line != null) {
                sj.add(line);
                line = bufferedReader.readLine();
            }
            return sj.toString();
        }
    }

    private static void writeStringToFile(File file, String content) throws IOException {
        if (!file.exists() && (!file.createNewFile())) {
            throw new IOException("File creation failed");
        }

        try (
                FileOutputStream fileOutputStream = new FileOutputStream(file, false);
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, DEFAULT_CHARSET);
                BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter)
        ) {
            bufferedWriter.write(content);
        }
    }
}
