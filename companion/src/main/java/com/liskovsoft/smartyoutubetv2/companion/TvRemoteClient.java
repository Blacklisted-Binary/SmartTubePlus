package com.liskovsoft.smartyoutubetv2.companion;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTTP client for sending commands to a SmartTubePlus TV device.
 *
 * <p>All network calls are performed on a background thread.  The {@link Callback} is
 * always invoked back on the main thread.</p>
 */
public class TvRemoteClient {

    private static final String TAG = TvRemoteClient.class.getSimpleName();
    private static final int TIMEOUT_MS = 5_000;

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private TvDeviceDiscovery.TvDevice mDevice;

    /** Set the target TV device. */
    public void setDevice(TvDeviceDiscovery.TvDevice device) {
        this.mDevice = device;
    }

    /** Returns the currently targeted device, or null if none. */
    public TvDeviceDiscovery.TvDevice getDevice() {
        return mDevice;
    }

    /**
     * Sends a YouTube URL to the TV app for immediate playback.
     *
     * @param youtubeUrl Full YouTube URL (e.g. {@code https://www.youtube.com/watch?v=dQw4w9WgXcQ})
     * @param callback   Result callback — may be null
     */
    public void play(String youtubeUrl, Callback callback) {
        post("/play", "url=" + urlEncode(youtubeUrl), callback);
    }

    /**
     * Sends a remote-control action to the TV.
     *
     * @param action One of: {@code PLAY}, {@code PAUSE}, {@code TOGGLE}, {@code NEXT},
     *               {@code PREV}, {@code SEEK_FWD}, {@code SEEK_BWD}, {@code VOL_UP}, {@code VOL_DOWN}
     */
    public void command(String action, Callback callback) {
        post("/command", "action=" + urlEncode(action), callback);
    }

    /**
     * Sends a search query to the TV app.
     *
     * @param text     Search query text
     * @param callback Result callback — may be null
     */
    public void search(String text, Callback callback) {
        post("/search", "text=" + urlEncode(text), callback);
    }

    // ---- Status / handoff ---------------------------------------------------

    /**
     * Value object returned by {@link #getStatus}.
     */
    public static class StatusInfo {
        public final String videoId;
        public final long   positionMs;
        public final long   durationMs;
        public final String title;
        public final boolean isPlaying;

        public StatusInfo(String videoId, long positionMs, long durationMs,
                          String title, boolean isPlaying) {
            this.videoId    = videoId;
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.title      = title;
            this.isPlaying  = isPlaying;
        }

        /** Returns true when the TV is currently playing something. */
        public boolean hasVideo() {
            return videoId != null && !videoId.isEmpty();
        }
    }

    /** Callback for {@link #getStatus}. */
    public interface StatusCallback {
        void onStatus(StatusInfo info);
        void onError(String message);
    }

    /**
     * Queries the TV for current playback state via {@code GET /status}.
     */
    public void getStatus(StatusCallback callback) {
        if (mDevice == null) {
            if (callback != null) mMainHandler.post(() -> callback.onError("No device selected"));
            return;
        }
        final String host = mDevice.host;
        final int    port = mDevice.port;
        mExecutor.submit(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection)
                        new URL("http://" + host + ":" + port + "/status").openConnection();
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                if (code != 200) {
                    conn.disconnect();
                    deliverStatusError(callback, "HTTP " + code);
                    return;
                }
                java.io.InputStream is = conn.getInputStream();
                java.util.Scanner sc = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
                String json = sc.hasNext() ? sc.next() : "";
                conn.disconnect();

                StatusInfo info = parseStatus(json);
                if (callback != null) {
                    mMainHandler.post(() -> callback.onStatus(info));
                }
            } catch (Exception e) {
                Log.w(TAG, "getStatus error: " + e.getMessage());
                deliverStatusError(callback, e.getMessage());
            }
        });
    }

    /**
     * Tells the TV to seek to {@code positionMs} and resume playback via {@code POST /resume}.
     */
    public void resume(long positionMs, Callback callback) {
        post("/resume", "positionMs=" + positionMs, callback);
    }

    // ---- Helpers ------------------------------------------------------------

    private void deliverStatusError(StatusCallback callback, String msg) {
        if (callback != null) {
            mMainHandler.post(() -> callback.onError(msg));
        }
    }

    /** Minimal hand-rolled JSON parser for the /status response (no external libs needed). */
    private static StatusInfo parseStatus(String json) {
        String videoId   = jsonString(json, "videoId");
        long   posMs     = jsonLong(json,   "positionMs", 0);
        long   durMs     = jsonLong(json,   "durationMs", 0);
        String title     = jsonString(json, "title");
        boolean playing  = jsonBool(json,   "isPlaying", false);
        return new StatusInfo(videoId, posMs, durMs, title, playing);
    }

    private static String jsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return "";
        start += search.length();
        int end = json.indexOf('"', start);
        if (end < 0) return "";
        return json.substring(start, end)
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\\\", "\\");
    }

    private static long jsonLong(String json, String key, long def) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) return def;
        start += search.length();
        int end = start;
        // Allow a leading '-' for negative numbers; only digits after that.
        if (start < json.length() && json.charAt(start) == '-') end++;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        try { return Long.parseLong(json.substring(start, end)); }
        catch (NumberFormatException e) { return def; }
    }

    private static boolean jsonBool(String json, String key, boolean def) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) return def;
        String rest = json.substring(start + search.length()).trim();
        if (rest.startsWith("true"))  return true;
        if (rest.startsWith("false")) return false;
        return def;
    }

    /**
     * Quick health-check.  Returns silently on success; calls {@code onError} on failure.
     */
    public void ping(Callback callback) {
        if (mDevice == null) {
            deliverError(callback, "No device selected");
            return;
        }
        mExecutor.submit(() -> {
            try {
                String base = "http://" + mDevice.host + ":" + mDevice.port;
                HttpURLConnection conn = (HttpURLConnection) new URL(base + "/ping").openConnection();
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                conn.disconnect();
                if (code == 200) {
                    deliverSuccess(callback);
                } else {
                    deliverError(callback, "Unexpected status " + code);
                }
            } catch (Exception e) {
                Log.w(TAG, "ping error: " + e.getMessage());
                deliverError(callback, e.getMessage());
            }
        });
    }

    // -------------------------------------------------------------------------

    private void post(String path, String formBody, Callback callback) {
        if (mDevice == null) {
            deliverError(callback, "No device selected");
            return;
        }
        final String deviceHost = mDevice.host;
        final int devicePort = mDevice.port;
        mExecutor.submit(() -> {
            try {
                byte[] data = formBody.getBytes(StandardCharsets.UTF_8);
                String base = "http://" + deviceHost + ":" + devicePort;
                HttpURLConnection conn = (HttpURLConnection) new URL(base + path).openConnection();
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(data.length);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.connect();
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(data);
                }
                int code = conn.getResponseCode();
                conn.disconnect();
                if (code == 200) {
                    deliverSuccess(callback);
                } else {
                    deliverError(callback, "Unexpected status " + code);
                }
            } catch (Exception e) {
                Log.w(TAG, "POST " + path + " error: " + e.getMessage());
                deliverError(callback, e.getMessage());
            }
        });
    }

    private void deliverSuccess(Callback callback) {
        if (callback != null) {
            mMainHandler.post(callback::onSuccess);
        }
    }

    private void deliverError(Callback callback, String message) {
        if (callback != null) {
            mMainHandler.post(() -> callback.onError(message));
        }
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value;
        }
    }

    public void shutdown() {
        mExecutor.shutdownNow();
    }
}
