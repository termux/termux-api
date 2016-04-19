package com.termux.api.util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.util.JsonWriter;
import android.view.View;
import android.widget.TextView;

import com.termux.api.R;

import java.util.ArrayList;

public class TermuxApiPermissionActivity extends Activity {

    /** Intent extra containing the permissions to request. */
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
                context.startActivity(new Intent(context, TermuxApiPermissionActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putStringArrayListExtra(TermuxApiPermissionActivity.PERMISSIONS_EXTRA, permissionsToRequest));
                ResultReturner.returnData(context, intent, new ResultReturner.ResultJsonWriter() {
                    @Override
                    public void writeJson(JsonWriter out) throws Exception {
                        // Empty response until permission is granted.
                    }
                });
                return false;
            }
        } else {
            return true;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.termux.api.R.layout.activity_permission);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        permissionValues = getIntent().getStringArrayListExtra(PERMISSIONS_EXTRA);

        Resources res = getResources();
        String permissionDescription = res.getString(R.string.permission_description);

        for (String permission : permissionValues) {
            permissionDescription += "\n" + permission.substring(permission.lastIndexOf('.') + 1);
        }

        ((TextView) findViewById(R.id.grant_permission_description)).setText(permissionDescription);
    }

    @TargetApi(Build.VERSION_CODES.M)
    public void onOkButton(View view) {
        requestPermissions(permissionValues.toArray(new String[0]), 123);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        finish();
    }


}
