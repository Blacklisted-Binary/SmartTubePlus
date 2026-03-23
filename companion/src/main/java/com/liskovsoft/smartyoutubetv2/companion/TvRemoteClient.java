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
