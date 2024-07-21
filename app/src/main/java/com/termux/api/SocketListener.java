package com.termux.api;

import android.app.Application;
import android.content.Intent;
import android.net.LocalServerSocket;
import android.net.LocalSocket;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SocketListener {

    public static final String LISTEN_ADDRESS = TermuxConstants.TERMUX_API_PACKAGE_NAME + "://listen";
    private static final Pattern EXTRA_STRING = Pattern.compile("(-e|--es|--esa) +([^ ]+) +\"(.*?)(?<!\\\\)\"", Pattern.DOTALL);
    private static final Pattern EXTRA_BOOLEAN = Pattern.compile("--ez +([^ ]+) +([^ ]+)");
    private static final Pattern EXTRA_INT = Pattern.compile("--ei +([^ ]+) +(-?[0-9]+)");
    private static final Pattern EXTRA_FLOAT = Pattern.compile("--ef +([^ ]+) +(-?[0-9]+(?:\\.[0-9]+))");
    private static final Pattern EXTRA_INT_LIST = Pattern.compile("--eia +([^ ]+) +(-?[0-9]+(?:,-?[0-9]+)*)");
    private static final Pattern EXTRA_LONG_LIST = Pattern.compile("--ela +([^ ]+) +(-?[0-9]+(?:,-?[0-9]+)*)");
    private static final Pattern EXTRA_UNSUPPORTED = Pattern.compile("--e[^izs ] +[^ ]+ +[^ ]+");
    private static final Pattern ACTION = Pattern.compile("-a *([^ ]+)");
    
    private static Thread listener = null;

    private static final String LOG_TAG = "SocketListener";

    public static void createSocketListener(Application app) {
        if (listener == null) {
            listener = new Thread(() -> {
                try (LocalServerSocket listen = new LocalServerSocket(LISTEN_ADDRESS)) {
                    while (true) {
                        try (LocalSocket con = listen.accept();
                             DataInputStream in = new DataInputStream(con.getInputStream());
                             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(con.getOutputStream()))) {
                            // only accept connections from Termux programs
                            if (con.getPeerCredentials().getUid() != app.getApplicationInfo().uid) {
                                continue;
                            }
                            try {
                                //System.out.println("connection");
                                int length = in.readUnsignedShort();
                                byte[] b = new byte[length];
                                in.readFully(b);
                                String cmdline = new String(b, StandardCharsets.UTF_8);
                        
                                Intent intent = new Intent(app.getApplicationContext(), TermuxApiReceiver.class);
                                //System.out.println(cmdline.replaceAll("--es socket_input \".*?\"","").replaceAll("--es socket_output \".*?\"",""));
                                HashMap<String, String> stringExtras = new HashMap<>();
                                HashMap<String, String[]> stringArrayExtras = new HashMap<>();
                                HashMap<String, Boolean> booleanExtras = new HashMap<>();
                                HashMap<String, Integer> intExtras = new HashMap<>();
                                HashMap<String, Float> floatExtras = new HashMap<>();
                                HashMap<String, int[]> intArrayExtras = new HashMap<>();
                                HashMap<String, long[]> longArrayExtras = new HashMap<>();
                                boolean err = false;
                        
                                // extract and remove the string extras first, so another argument embedded in a string isn't counted as an argument
                                Matcher m = EXTRA_STRING.matcher(cmdline);
                                while (m.find()) {
                                    String option = m.group(1);
                                    if ("-e".equals(option) || "--es".equals(option)) {
                                        // unescape "
                                        stringExtras.put(m.group(2), Objects.requireNonNull(m.group(3)).replaceAll("\\\\\"", "\""));
                                    }
                                    else {
                                        // split the list
                                        String[] list = Objects.requireNonNull(m.group(3)).split("(?<!\\\\),");
                                        for (int i = 0; i < list.length; i++) {
                                            /// unescape the ","
                                            list[i] = list[i].replaceFirst("\\\\,", ",");
                                        }
                                        stringArrayExtras.put(m.group(2), list);
                                    }
                            
                                }
                                cmdline = m.replaceAll("");
                        
                                m = EXTRA_BOOLEAN.matcher(cmdline);
                                while (m.find()) {
                                    String value = m.group(2);
                                    value = value != null ? value.toLowerCase() : null;
                                    Boolean arg = null;

                                    if ("true".equals(value) || "t".equals(value)) {
                                        arg = true;
                                    } else if ("false".equals(value) || "f".equals(value)) {
                                        arg = false;
                                    } else {
                                        try {
                                            if (value != null)
                                                arg = Integer.decode(value) != 0;
                                        } catch (NumberFormatException ex) {
                                            // Ignore
                                        }
                                    }
                                    if (arg == null) {
                                        String msg = "Invalid boolean extra: " + m.group(0) + "\n";
                                        Logger.logInfo(LOG_TAG, msg);
                                        out.write(msg);
                                        err = true;
                                        break;
                                    }
                                    booleanExtras.put(m.group(1), arg);
                                }
                                cmdline = m.replaceAll("");
                        
                                m = EXTRA_INT.matcher(cmdline);
                                while (m.find()) {
                                    try {
                                        intExtras.put(m.group(1), Integer.parseInt(Objects.requireNonNull(m.group(2))));
                                    }
                                    catch (NumberFormatException e) {
                                        String msg = "Invalid integer extra: " + m.group(0) + "\n";
                                        Logger.logInfo(LOG_TAG, msg);
                                        out.write(msg);
                                        err = true;
                                        break;
                                    }
                                }
                                cmdline = m.replaceAll("");
                        
                                m = EXTRA_FLOAT.matcher(cmdline);
                                while (m.find()) {
                                    try {
                                        floatExtras.put(m.group(1), Float.parseFloat(Objects.requireNonNull(m.group(2))));
                                    }
                                    catch (NumberFormatException e) {
                                        String msg = "Invalid float extra: " + m.group(0) + "\n";
                                        Logger.logInfo(LOG_TAG, msg);
                                        out.write(msg);
                                        err = true;
                                        break;
                                    }
                                }
                                cmdline = m.replaceAll("");
                        
                                m = EXTRA_INT_LIST.matcher(cmdline);
                                while (m.find()) {
                                    try {
                                        String[] parts = Objects.requireNonNull(m.group(2)).split(",");
                                        int[] ints = new int[parts.length];
                                        for (int i = 0; i < parts.length; i++) {
                                            ints[i] = Integer.parseInt(parts[i]);
                                        }
                                        intArrayExtras.put(m.group(1), ints);
                                    }
                                    catch (NumberFormatException e) {
                                        String msg = "Invalid int array extra: " + m.group(0) + "\n";
                                        Logger.logInfo(LOG_TAG, msg);
                                        out.write(msg);
                                        err = true;
                                        break;
                                    }
                                }
                                cmdline = m.replaceAll("");
                        
                                m = EXTRA_LONG_LIST.matcher(cmdline);
                                while (m.find()) {
                                    try {
                                        String[] parts = Objects.requireNonNull(m.group(2)).split(",");
                                        long[] longs = new long[parts.length];
                                        for (int i = 0; i < parts.length; i++) {
                                            longs[i] = Long.parseLong(parts[i]);
                                        }
                                        longArrayExtras.put(m.group(1), longs);
                                    }
                                    catch (NumberFormatException e) {
                                        String msg = "Invalid long array extra: " + m.group(0) + "\n";
                                        Logger.logInfo(LOG_TAG, msg);
                                        out.write(msg);
                                        err = true;
                                        break;
                                    }
                                }
                                cmdline = m.replaceAll("");
                        
                                m = ACTION.matcher(cmdline);
                                while (m.find()) {
                                    intent.setAction(m.group(1));
                                }
                                cmdline = m.replaceAll("");
                        
                                m = EXTRA_UNSUPPORTED.matcher(cmdline);
                                if (m.find()) {
                                    String msg = "Unsupported argument type: " + m.group(0) + "\n";
                                    Logger.logInfo(LOG_TAG, msg);
                                    out.write(msg);
                                    err = true;
                                }
                                cmdline = m.replaceAll("");
                        
                                // check if there are any non-whitespace characters left after parsing all the options
                                cmdline = cmdline.replaceAll("\\s", "");
                                if (!"".equals(cmdline)) {
                                    String msg = "Unsupported options: " + cmdline + "\n";
                                    Logger.logInfo(LOG_TAG, msg);
                                    out.write(msg);
                                    err = true;
                                }
                        
                                if (err) {
                                    out.flush();
                                    continue;
                                }
                        
                                // set the intent extras
                                for (Map.Entry<String, String> e : stringExtras.entrySet()) {
                                    intent.putExtra(e.getKey(), e.getValue());
                                }
                                for (Map.Entry<String, String[]> e : stringArrayExtras.entrySet()) {
                                    intent.putExtra(e.getKey(), e.getValue());
                                }
                                for (Map.Entry<String, Integer> e : intExtras.entrySet()) {
                                    intent.putExtra(e.getKey(), e.getValue());
                                }
                                for (Map.Entry<String, Boolean> e : booleanExtras.entrySet()) {
                                    intent.putExtra(e.getKey(), e.getValue());
                                }
                                for (Map.Entry<String, Float> e : floatExtras.entrySet()) {
                                    intent.putExtra(e.getKey(), e.getValue());
                                }
                                for (Map.Entry<String, int[]> e : intArrayExtras.entrySet()) {
                                    intent.putExtra(e.getKey(), e.getValue());
                                }
                                for (Map.Entry<String, long[]> e : longArrayExtras.entrySet()) {
                                    intent.putExtra(e.getKey(), e.getValue());
                                }
                                app.getApplicationContext().sendOrderedBroadcast(intent, null);
                                // send a null byte as a sign that the arguments have been successfully received, parsed and the broadcast receiver is called
                                con.getOutputStream().write(0);
                                con.getOutputStream().flush();
                            }
                            catch (Exception e) {
                                Logger.logStackTraceWithMessage(LOG_TAG, "Error parsing arguments", e);
                                out.write("Exception in the plugin\n");
                                out.flush();
                            }
                        }
                        catch (java.io.IOException e) {
                            Logger.logStackTraceWithMessage(LOG_TAG, "Connection error", e);
                        }
                    }
                }
                catch (Exception e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Error listening for connections", e);
                }
            });
            listener.start();
        }
    }

}
