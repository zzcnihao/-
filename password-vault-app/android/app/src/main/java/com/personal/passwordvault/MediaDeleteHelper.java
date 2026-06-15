package com.personal.passwordvault;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

/** Try delete screenshot URI from MediaStore after archived to vault. */
public class MediaDeleteHelper {
    private static final String TAG = "MishiMediaDelete";

    public static void tryDeleteScreenshot(Context ctx, Uri uri) {
        if (uri == null) return;
        try {
            ContentResolver cr = ctx.getContentResolver();
            int rows = cr.delete(uri, null, null);
            if (rows > 0) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Log.d(TAG, "delete returned 0 for " + uri);
            }
        } catch (SecurityException se) {
            Log.d(TAG, "no delete permission: " + se.getMessage());
        } catch (Exception e) {
            Log.d(TAG, "delete failed: " + e.getMessage());
        }
    }
}