package com.termux.api.apis;

import static com.termux.api.apis.NsdApi.ResultJson.resultJson;
import static java.util.Objects.requireNonNull;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.ext.SdkExtensions;
import android.util.JsonWriter;

import androidx.annotation.Nullable;

import com.termux.api.util.ResultReturner;
import com.termux.api.util.ResultReturner.ResultJsonWriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class NsdApi {

    @FunctionalInterface
    public interface JsonConsumer extends Consumer<JsonWriter> {
        void write(JsonWriter writer) throws Exception;

        default void accept(JsonWriter jsonWriter) {
            try {
                write(jsonWriter);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class ResultCallback {
        private final Context context;
        private final Intent intent;
        private Runnable onSuccess;

        public ResultCallback(Context applicationContext, Intent intent) {
            this.context = applicationContext;
            this.intent = intent;
        }

        public void send(Consumer<JsonWriter> visitor) {
            ResultReturner.returnData(context, intent, new ResultJsonWriter() {
                @Override
                public void writeJson(JsonWriter out) throws Exception {
                    out.beginObject();
                    visitor.accept(out);
                    out.endObject();
                }
            });
        }
        
        public void success(Consumer<JsonWriter> data) {
            if (onSuccess != null) onSuccess.run();
            send(resultJson().code(0).andThen(data));
        }

        public void error(int errorCode, String msg, Object... args) {
            send(resultJson().code(errorCode).message(msg, args));
        }

        public ResultCallback onSuccess(Runnable r) {
            this.onSuccess = r;
            return this;
        }
    }

    private static class RegistrationListener implements NsdManager.RegistrationListener {

        private ResultCallback result;
        private final UUID id;
        private final NsdServiceInfo serviceInfo;

        public RegistrationListener(NsdServiceInfo info) {
            this.serviceInfo = info;
            this.id = UUID.randomUUID();
        }

        @Override
        public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            if (result != null) {
                result.error(errorCode, "%s registration failed", serviceInfo);
                result = null;
            }
        }

        @Override
        public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            if (result != null) {
                result.error(errorCode, "%s unregistration failed", serviceInfo);
                result = null;
            }
        }

        @Override
        public void onServiceRegistered(NsdServiceInfo regInfo) {
            final var registeredName = regInfo.getServiceName();
            serviceInfo.setServiceName(registeredName);
            if (result != null) {
                result.success(resultJson().id(id)
                        .message("registered %s", serviceInfo)
                        .stringField("name", registeredName));

                result = null;
            }
        }

        @Override
        public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
            if (result != null) {
                result.success(resultJson().message("unregistered %s", serviceInfo));
                result = null;
            }
        }

        public RegistrationListener setResultCallback(ResultCallback result) {
            this.result = result;
            return this;
        }
    }

    public static class NsdService extends Service {
        private final ArrayList<RegistrationListener> registrations = new ArrayList<>();
        private WifiManager.MulticastLock multicastLock;

        private WifiManager.MulticastLock multicastLock() {
            if (multicastLock == null) {
                final var wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                multicastLock = wifiManager.createMulticastLock(this.getClass().getSimpleName());
                multicastLock.setReferenceCounted(true);
            }
            return multicastLock;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            final var nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);

            final var command = intent.getStringExtra("command");
            final var callback = new ResultCallback(getApplicationContext(), intent);
            try {
                if ("register".equals(command)) {
                    var info = nsdServiceInfo(intent);
                    var registration = new RegistrationListener(info);
                    registration.setResultCallback(callback.onSuccess(() -> {
                        registrations.add(registration);
                        multicastLock().acquire();
                    }));
                    nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration);
                } else if ("unregister".equals(command)) {
                    findListener(intent).ifPresentOrElse(r -> {
                        r.setResultCallback(callback.onSuccess(() -> {
                            registrations.remove(r);
                            multicastLock().release();
                        }));
                        nsdManager.unregisterService(r);
                    }, () -> callback.error(-1, "registration not found"));
                } else if ("list".equals(command)) {
                    callback.success((JsonConsumer) out -> {
                        out.name("registrations");
                        out.beginArray();
                        for (var r : registrations) {
                            out.beginObject()
                                    .name("id").value(r.id.toString())
                                    .name("name").value(r.serviceInfo.getServiceName())
                                    .name("type").value(r.serviceInfo.getServiceType())
                                    .name("port").value(r.serviceInfo.getPort())
                                    .endObject();
                        }
                        out.endArray();
                    });
                } else {
                    callback.error(-1, "Unsupported command: %s", command);
                }
            } catch (Exception e) {
                callback.error(-2, "Exception: %s", e.getMessage());
            }

            return START_NOT_STICKY;
        }

        @Override
        public void onDestroy() {
            final var nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
            registrations.forEach(nsdManager::unregisterService);
        }

        private static Predicate<RegistrationListener> search(Intent intent) {
            var id = intent.getStringExtra("id");
            if (id != null) {
                return r -> r.id.toString().equals(id);
            }

            var name = requireNonNull(intent.getStringExtra("name"));
            var type = requireNonNull(intent.getStringExtra("type"));
            return r -> name.equals(r.serviceInfo.getServiceName())
                    && type.equals(r.serviceInfo.getServiceType());
        }

        private Optional<RegistrationListener> findListener(Intent intent) {
            return registrations.stream()
                    .filter(r -> r.serviceInfo != null)
                    .filter(search(intent))
                    .findFirst();
        }

        private static NsdServiceInfo nsdServiceInfo(Intent intent) {
            final var nsdServiceInfo = new NsdServiceInfo();
            nsdServiceInfo.setServiceName(intent.getStringExtra("name"));
            nsdServiceInfo.setServiceType(intent.getStringExtra("type"));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 12) {
                Optional.ofNullable(intent.getStringArrayExtra("subTypes"))
                        .map(Set::of)
                        .ifPresent(nsdServiceInfo::setSubtypes);
            }

            Optional.ofNullable(intent.getStringArrayExtra("attributes"))
                    .stream().flatMap(Arrays::stream)
                    .map(s -> s.split("=", 2))
                    .forEach(a -> nsdServiceInfo.setAttribute(a[0], a[1]));

            int port = intent.getIntExtra("port", 0);
            if (port <= 0) {
                throw new IllegalArgumentException("invalid port value");
            }

            nsdServiceInfo.setPort(port);
            return nsdServiceInfo;
        }

        @Nullable
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }

    public static void onReceive(final Context context, Intent intent) {
        final var serviceIntent = new Intent(context, NsdService.class);
        Optional.ofNullable(intent.getExtras()).ifPresent(serviceIntent::putExtras);
        context.startService(serviceIntent);
    }

    static class ResultJson implements Consumer<JsonWriter> {
        private Consumer<JsonWriter> delegate;

        public ResultJson() {
            this.delegate = out -> {
            };
        }

        public static ResultJson resultJson() {
            return new ResultJson();
        }

        public ResultJson longField(String name, long value) {
            delegate = delegate.andThen((JsonConsumer) (out) -> out.name(name).value(value));
            return this;
        }

        public ResultJson stringField(String name, Object value) {
            delegate = delegate.andThen((JsonConsumer) (out) -> out.name(name).value(value.toString()));
            return this;
        }

        public ResultJson code(int errorCode) {
            return longField("code", errorCode);
        }

        public ResultJson message(String message, Object... args) {
            return stringField("message", String.format(message, args));
        }

        public ResultJson id(Object id) {
            return stringField("id", id);
        }

        @Override
        public void accept(JsonWriter jsonWriter) {
            delegate.accept(jsonWriter);
        }
    }
}
