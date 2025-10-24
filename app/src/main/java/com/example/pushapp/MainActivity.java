package com.example.pushapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * MainActivity is a simple activity that displays a message indicating that
 * the application is running. Most of the interesting behaviour of this
 * application happens in the {@link MyFirebaseMessagingService} which
 * listens for Firebase Cloud Messaging (FCM) data notifications and
 * processes them in the background.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "NotificationHandler";

    private Button btnTest;

    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private final long TIMEOUT_MS = 20_000; // 30 seconds


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements
//        tvLog = findViewById(R.id.tvLog);
        btnTest = findViewById(R.id.btnTest);

        // Show initial status message
//        tvLog.setText("Firebase PushApp is running\n");

        // Set up click listener to trigger a test notification using NotificationHandler
        btnTest.setOnClickListener(v -> runTestNotification());

        // auto click on test
        btnTest.performClick();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void handleScriptRunning(String site, String script) {
        WebView webView = findViewById(R.id.webview);
        webView.getSettings().setJavaScriptEnabled(true);

        webView.loadUrl(site);
        webView.setWebViewClient(new WebViewClient() {

            private Handler handler = new Handler();
            private Runnable pageFinishRunnable;
            private static final int DEBOUNCE_DELAY_MS = 1_000; // adjust as needed

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (pageFinishRunnable != null) {
                    handler.removeCallbacks(pageFinishRunnable);
                }

                pageFinishRunnable = () -> {
                    // Do Tasks here
                    Log.d("WebView", "Debounced finish: " + url);
                    view.evaluateJavascript(script,null);

                    timeoutHandler.removeCallbacksAndMessages(null);
                    timeoutHandler.postDelayed(() -> webView.post(() -> {
                        try {
                            webView.removeAllViews();
                            webView.destroy();
                        } catch (Exception e) {
                            Log.e(TAG, "Error destroying WebView", e);
                        }
                    }), TIMEOUT_MS);
                };

                handler.postDelayed(pageFinishRunnable, DEBOUNCE_DELAY_MS);
            }
        });
    }

}