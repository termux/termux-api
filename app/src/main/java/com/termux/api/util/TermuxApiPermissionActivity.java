package com.termux.api.util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.JsonWriter;

import java.util.ArrayList;

public class TermuxApiPermissionActivity extends Activity {

    /**
     * Intent extra containing the permissions to request.
     */
    public static final String PERMISSIONS_EXTRA = "com.termux.api.permission_extra";

    private ArrayList<String> permissionValues;

    /**
     * Check for and request permissions if necessary.
     *
     * @return if all permissions were already granted
     */
    public static boolean checkAndRequestPermissions(Context context, Intent intent, String... permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> permissionsToRequest = new ArrayList<>();
            for (String permission : permissions) {
                if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED) {
                    permissionsToRequest.add(permission);
                }
            }

            if (permissionsToRequest.isEmpty()) {
                return true;
            } else {
                ResultReturner.returnData(context, intent, new ResultReturner.ResultJsonWriter() {
                    @Override
                    public void writeJson(JsonWriter out) throws Exception {
                        out.beginObject().name("error").value("Requesting API permission - try again").endObject();
                    }
                });

                Intent startIntent = new Intent(context, TermuxApiPermissionActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putStringArrayListExtra(TermuxApiPermissionActivity.PERMISSIONS_EXTRA, permissionsToRequest);
                ResultReturner.copyIntentExtras(intent, startIntent);
                context.startActivity(startIntent);
                return false;
            }
        } else {
            return true;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    protected void onResume() {
        super.onResume();
        permissionValues = getIntent().getStringArrayListExtra(PERMISSIONS_EXTRA);
        requestPermissions(permissionValues.toArray(new String[0]), 123);
        finish();
    }

}
