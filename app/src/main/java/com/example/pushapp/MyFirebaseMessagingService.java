package com.example.pushapp;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Custom implementation of {@link FirebaseMessagingService} that delegates
 * processing of data notifications to {@link NotificationHandler}. This
 * service is invoked automatically by Firebase when a message arrives. It
 * extracts the data payload and calls the centralized handler.
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "MyFCMService";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Refreshed FCM token: " + token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        // Delegate only data payload; ignore notification payload here.
        if (remoteMessage.getData() != null && !remoteMessage.getData().isEmpty()) {
            NotificationHandler.handleNotification(getApplicationContext(), remoteMessage.getData());
        }
    }
}