package com.termux.api.apis;

import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.util.JsonWriter;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.termux.api.TermuxApiReceiver;
import com.termux.api.util.ResultReturner;
import com.termux.shared.logger.Logger;

import java.io.File;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class PackageManagerAPI {
    private static final String LOG_TAG = "PackageManagerAPI";

    public static void onReceivePMGetApplicationInfo(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceivePMGetApplicationInfo");
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws IOException {
                PackageManager manager = context.getPackageManager();
                String file = intent.getStringExtra("file");
                if (file == null || file.isEmpty()) {
                    out.beginObject().name("API_ERROR").value("No file path specified").endObject();
                    return;
                }

                File apk = new File(file);
                if (!apk.exists()) {
                    out.beginObject().name("API_ERROR").value("The file path does not exist").endObject();
                    return;
                }

                if (!apk.isFile()) {
                    out.beginObject().name("API_ERROR").value("Not a file").endObject();
                    return;
                }

                PackageInfo app = manager.getPackageArchiveInfo(file, PackageManager.GET_META_DATA | PackageManager.GET_PERMISSIONS | PackageManager.GET_SIGNATURES);

                if (app != null){
                    ApplicationInfo appInfo = app.applicationInfo;

                    out.beginObject();
                    out.name("name").value(manager.getApplicationLabel(appInfo).toString());
                    out.name("package").value(app.packageName);
                    out.name("version").value(app.versionName);
                    out.name("target_sdk").value(appInfo.targetSdkVersion);
                    out.name("min_sdk").value(appInfo.minSdkVersion);
                    out.name("size").value(apk.length());
                    out.name("src").value(file);
                    out.name("permissions");
                    out.beginArray();
                    String[] perms = app.requestedPermissions;
                    if (perms != null) {
                        for (String perm : perms) {
                            out.value(perm);
                        }
                    }
                    out.endArray();
                    out.name("debuggable").value((appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
                    out.name("process").value(appInfo.processName);
                    out.name("install_location");
                    switch (app.installLocation) {
                        case PackageInfo.INSTALL_LOCATION_AUTO: 
                            out.value("auto");
                            break;
                        case PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY:
                            out.value("internal");
                            break;
                        case PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL:
                            out.value("external");
                            break;
                        default:
                            out.value("unknown");
                            break;
                    }
                    Signature[] signatures = app.signatures;
                    out.name("certificate");
                    out.beginObject();
                    if (signatures != null && signatures.length > 0) {
                      Signature signature = signatures[0];
                      byte[] certBytes = signature.toByteArray();
                      InputStream input = new ByteArrayInputStream(certBytes);
                      try {
                          CertificateFactory factory = CertificateFactory.getInstance("X.509");
                          X509Certificate cert = (X509Certificate) factory.generateCertificate(input);
                          out.name("sign_algorithm").value(cert.getSigAlgName());
                          out.name("valid_from").value(cert.getNotBefore().getTime()/1000);
                          out.name("valid_to").value(cert.getNotAfter().getTime()/1000);
                          out.name("issuer").value(cert.getIssuerDN().getName());
                          out.name("subject").value(cert.getSubjectDN().getName());
                      } catch (CertificateException e) {}
                    }
                    out.endObject();
                    out.endObject();
                }
            }
        });
    }

    public static void onReceivePMListPackages(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceivePMListPackages");
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws IOException {
                PackageManager manager = context.getPackageManager();
                List<PackageInfo> packages = manager.getInstalledPackages(PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA | PackageManager.GET_PERMISSIONS | PackageManager.GET_SIGNATURES);

                boolean querySystem = intent.getBooleanExtra("query_system", false);
                boolean queryAll = intent.getBooleanExtra("query_all", false);
                String queryName = intent.getStringExtra("query");
                boolean queryPackageName = intent.getBooleanExtra("query_package_name", false);
                boolean queryExact = intent.getBooleanExtra("query_exact", false);

                out.beginArray();
                for (PackageInfo info : packages) {
                    ApplicationInfo appInfo = info.applicationInfo;
                        
                    if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && !querySystem && !queryAll) {
                        continue;
                    } else if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0 && querySystem && !queryAll) {
                        continue;
                    }

                    
                    if (queryName != null && !queryName.isEmpty()) {
                        if (!queryExact) {
                            if (!manager.getApplicationLabel(appInfo).toString().contains(queryName) && !queryPackageName) { continue; }
                            if (!info.packageName.contains(queryName) && queryPackageName) { continue; }
                        } else {
                          if (!manager.getApplicationLabel(appInfo).toString().equals(queryName) && !queryPackageName) { continue; }
                          if (!info.packageName.equals(queryName) && queryPackageName) { continue; }
                        }
                    }
                    out.beginObject();
                    out.name("name").value(manager.getApplicationLabel(appInfo).toString());
                    out.name("package").value(info.packageName);
                    out.name("version").value(info.versionName);
                    out.name("version_code").value(info.versionCode);
                    out.name("target_sdk").value(appInfo.targetSdkVersion);
                    out.name("min_sdk").value(appInfo.minSdkVersion);
                    out.name("size").value(new File(appInfo.sourceDir).length());
                    out.name("src").value(appInfo.sourceDir);
                    Intent launchIntent = manager.getLaunchIntentForPackage(info.packageName);
                    if (launchIntent != null) {
                        out.name("launch_activity").value(launchIntent.getComponent().getClassName());
                    }
                    out.name("activities");
                    out.beginArray();
                    ActivityInfo[] activities = info.activities;
                    if (activities != null) {
                        for (ActivityInfo activity : activities) {
                            out.value(activity.name);
                        }
                    }
                    out.endArray();
                    out.name("permissions");
                    out.beginObject();
                    String[] perms = info.requestedPermissions;
                    if (perms != null) {
                        for (String permission : perms) {
                            int state = manager.checkPermission(permission, appInfo.packageName);
                            out.name(permission).value(state == PackageManager.PERMISSION_GRANTED);
                        }
                    }
                    out.endObject();

                    out.name("uid").value(appInfo.uid);
                    out.name("debuggable").value((appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
                    out.name("process").value(appInfo.processName);
                    out.name("install_location");
                    switch (info.installLocation) {
                        case PackageInfo.INSTALL_LOCATION_AUTO:
                            out.value("auto");
                            break;
                        case PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY:
                            out.value("internal");
                            break;
                        case PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL:
                            out.value("external");
                            break;
                        default:
                            out.value("unknown");
                            break;
                    }
                    out.name("is_system").value((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                    try {
                        String init_installer = null;
                        String installer = null;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            init_installer = manager.getInstallSourceInfo(appInfo.packageName).getInitiatingPackageName();
                            installer = manager.getInstallSourceInfo(appInfo.packageName).getInstallingPackageName();

                        } else {
                            installer = manager.getInstallerPackageName(appInfo.packageName);
                        }

                        if (installer != null) {
                            out.name("installer").value(installer);
                        }
                        if (init_installer != null) {
                            out.name("init_installer").value(init_installer);
                        }

                    } catch (PackageManager.NameNotFoundException e) {
                          // isnt this always true?
                    }
                    Signature[] signatures = info.signatures;
                    out.name("certificate");
                    out.beginObject();
                    if (signatures != null && signatures.length > 0) {
                        Signature signature = signatures[0];
                        byte[] certBytes = signature.toByteArray();
                        try {
                            InputStream input = new ByteArrayInputStream(certBytes);
                            CertificateFactory factory = CertificateFactory.getInstance("X.509");
                            X509Certificate cert = (X509Certificate) factory.generateCertificate(input);
                            out.name("sign_algorithm").value(cert.getSigAlgName());
                            out.name("valid_from").value(cert.getNotBefore().getTime()/1000);
                            out.name("valid_to").value(cert.getNotAfter().getTime()/1000);
                            out.name("issuer").value(cert.getIssuerDN().getName());
                            out.name("subject").value(cert.getSubjectDN().getName());
                        } catch (CertificateException e) {}
                    }
                    out.endObject();
                    out.endObject();
                }
                out.endArray();

            }
        });
    }

    public static void onReceivePMInstallPackage(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceivePMInstallPackage");
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws IOException {
                String filePath = intent.getStringExtra("file");
                if (filePath == null || filePath.isEmpty()) {
                    out.beginObject().name("API_ERROR").value("No file path specified").endObject();
                    return;
                } 

                PackageManager manager = context.getPackageManager();

                File apk = new File(filePath);

                if (!apk.exists()) {
                    out.beginObject().name("API_ERROR").value("The file path does not exist").endObject();
                    return;
                }

                if (!apk.isFile()){
                  out.beginObject().name("API_ERROR").value("Not a file").endObject();
                  return;
                }


                Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);

                install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                Uri apkUri;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", apk);
                    install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                  apkUri = Uri.fromFile(apk);
                }

                install.setDataAndType(apkUri, "application/vnd.android.package-archive");
                install.putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://" + context.getPackageName()));
                install.putExtra(Intent.EXTRA_ORIGINATING_URI, apkUri);

                context.startActivity(install);
            }
        });
    }

    public static void onReceivePMUninstallPackage(TermuxApiReceiver apiReceiver, final Context context, Intent intent) {
        Logger.logDebug(LOG_TAG, "onReceivePMUninstallPackage");
        ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {
            @Override
            public void writeJson(JsonWriter out) throws IOException {
                String packageName = intent.getStringExtra("package");
                if (packageName == null || packageName.isEmpty()) {
                    out.beginObject().name("API_ERROR").value("No package name specified").endObject();
                }

                PackageManager manager = context.getPackageManager();
                try {
                    ApplicationInfo appInfo = manager.getApplicationInfo(packageName, 0);

                    if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                        out.beginObject().name("API_ERROR").value("You can't uninstall a system application").endObject();
                    } else {
                        Uri pkgUri = Uri.parse("package:" + packageName);

                        Intent uninstall = new Intent(Intent.ACTION_DELETE, pkgUri);

                        uninstall.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                        context.startActivity(uninstall);
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    out.beginObject().name("API_ERROR").value(packageName + " does not exist or not installed").endObject();
                }
            }
        });
    }
}
