package com.example.pushapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * BroadcastReceiver to facilitate local debugging of the push notification
 * handlers without requiring actual Firebase credentials. You can trigger
 * this receiver via an ADB shell broadcast command by sending an intent
 * with the action {@code ACTION_DEBUG_NOTIFICATION} and the appropriate
 * extras to simulate FCM data payloads.
 *
 * For example, to simulate an x1 notification:
 *
 * <pre>
 * adb shell am broadcast -a com.example.pushapp.DEBUG_NOTIFICATION -n com.example.pushapp/.DebugBroadcastReceiver \
 *    --es type x1 --es curlCommand "curl -I https://www.example.com" \
 *    --es endpointURL "https://webhook.site/your-endpoint"
 *
 * You can also avoid quoting problems by sending the curl command as a
 * base64‑encoded string instead of a plain extra.  Supply the encoded
 * command under the {@code curlCommandBase64} key and the receiver will
 * decode it before execution.  For example:
 *
 * <pre>
 * # Encode the curl command and broadcast it
 * base64Command=$(echo -n 'curl -I https://www.example.com' | base64 -w0)
 * adb shell am broadcast -a com.example.pushapp.DEBUG_NOTIFICATION \
 *    -n com.example.pushapp/.DebugBroadcastReceiver \
 *    --es type x1 \
 *    --es curlCommandBase64 "$base64Command" \
 *    --es endpointURL "https://webhook.site/your-endpoint"
 * </pre>
 * </pre>
 *
 * Or to simulate an x2 notification:
 *
 * <pre>
 * adb shell am broadcast -a com.example.pushapp.DEBUG_NOTIFICATION -n com.example.pushapp/.DebugBroadcastReceiver \
 *    --es type x2 --es webURL "https://www.example.com" \
 *    --es ClickText "About" --es stayTime "5"
 * </pre>
 */
public class DebugBroadcastReceiver extends BroadcastReceiver {

    public static final String ACTION_DEBUG_NOTIFICATION = "com.example.pushapp.DEBUG_NOTIFICATION";
    private static final String TAG = "DebugReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        if (!ACTION_DEBUG_NOTIFICATION.equals(intent.getAction())) {
            Log.d(TAG, "Received unknown action: " + intent.getAction());
            return;
        }
        // Extract extras into a Map<String,String> expected by NotificationHandler.
        Map<String, String> data = new HashMap<>();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                if (value != null) {
                    data.put(key, String.valueOf(value));
                }
            }
        }
        NotificationHandler.handleNotification(context.getApplicationContext(), data);
    }
}