package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.view.KeyEvent;
import androidx.annotation.Nullable;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SplashPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class CompanionServerService extends Service {
    private static final String TAG = CompanionServerService.class.getSimpleName();
    private static final int NOTIFICATION_ID = CompanionServerService.class.hashCode();
    private static final int SERVER_PORT = 8787;
    private static final String NSD_SERVICE_TYPE = "_smarttube._tcp.";
    private static final String NSD_SERVICE_NAME = "SmartTubePlus";
    private static final String APP_VERSION = "30.91";
    private static final long SEEK_AMOUNT_MS = 10_000;

    private volatile boolean mRunning = false;
    private ServerSocket mServerSocket;
    private Thread mServerThread;
    private NsdManager mNsdManager;
    private NsdManager.RegistrationListener mNsdRegistrationListener;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            startForeground(NOTIFICATION_ID, createNotification());
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        startHttpServer();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            registerNsdService();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopHttpServer();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            unregisterNsdService();
        }
    }

    private Notification createNotification() {
        String title = getString(R.string.companion_server);
        String serviceStarted = getString(R.string.background_service_started);
        return Utils.createNotification(
                getApplicationContext(),
                getApplicationInfo().icon,
                String.format("%s: %s", title, serviceStarted),
                ViewManager.instance(getApplicationContext()).getRootActivity());
    }

    private void startHttpServer() {
        mRunning = true;
        mServerThread = new Thread(() -> {
            try {
                mServerSocket = new ServerSocket(SERVER_PORT);
                while (mRunning) {
                    try {
                        Socket client = mServerSocket.accept();
                        handleClient(client);
                    } catch (IOException e) {
                        if (mRunning) {
                            Log.e(TAG, e);
                        }
                    }
                }
            } catch (IOException e) {
                if (mRunning) {
                    Log.e(TAG, e);
                }
            }
        });
        mServerThread.setDaemon(true);
        mServerThread.start();
    }

    private void stopHttpServer() {
        mRunning = false;
        try {
            if (mServerSocket != null) {
                mServerSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, e);
        }
    }

    private void handleClient(Socket client) {
        try (Socket s = client) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
            OutputStream out = s.getOutputStream();

            // Parse request line
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                return;
            }
            String method = parts[0];
            String path = parts[1];

            // Read headers
            int contentLength = 0;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                String lower = line.toLowerCase();
                if (lower.startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            // Read body if present
            String body = "";
            if (contentLength > 0) {
                char[] buf = new char[contentLength];
                int read = reader.read(buf, 0, contentLength);
                if (read > 0) {
                    body = new String(buf, 0, read);
                }
            }

            String response;
            if ("GET".equalsIgnoreCase(method) && "/ping".equals(path)) {
                response = buildResponse(200, "{\"app\":\"SmartTubePlus\",\"version\":\"" + APP_VERSION + "\"}");
            } else if ("POST".equalsIgnoreCase(method) && "/play".equals(path)) {
                response = handlePlay(body);
            } else if ("POST".equalsIgnoreCase(method) && "/command".equals(path)) {
                response = handleCommand(body);
            } else {
                response = buildResponse(404, "{\"error\":\"Not Found\"}");
            }

            out.write(response.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception e) {
            Log.e(TAG, e);
        }
    }

    private String handlePlay(String body) {
        try {
            String url = parseFormValue(body, "url");
            if (url == null || url.isEmpty()) {
                return buildResponse(400, "{\"error\":\"Missing url\"}");
            }
            final String decodedUrl = URLDecoder.decode(url, "UTF-8");
            if (!decodedUrl.startsWith("http://") && !decodedUrl.startsWith("https://")) {
                return buildResponse(400, "{\"error\":\"Invalid URL scheme\"}");
            }
            Utils.post(() -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(decodedUrl));
                SplashPresenter.instance(getApplicationContext()).applyNewIntent(intent);
            });
            return buildResponse(200, "{\"status\":\"ok\"}");
        } catch (Exception e) {
            Log.e(TAG, e);
            return buildResponse(500, "{\"error\":\"Internal Server Error\"}");
        }
    }

    private String handleCommand(String body) {
        try {
            String action = parseFormValue(body, "action");
            if (action == null || action.isEmpty()) {
                return buildResponse(400, "{\"error\":\"Missing action\"}");
            }
            Utils.post(() -> dispatchCommand(action));
            return buildResponse(200, "{\"status\":\"ok\"}");
        } catch (Exception e) {
            Log.e(TAG, e);
            return buildResponse(500, "{\"error\":\"Internal Server Error\"}");
        }
    }

    private void dispatchCommand(String action) {
        PlaybackPresenter playbackPresenter = PlaybackPresenter.instance(getApplicationContext());
        PlaybackView player = playbackPresenter.getPlayer();
        switch (action.toUpperCase()) {
            case "PLAY":
                if (player != null) player.setPlayWhenReady(true);
                break;
            case "PAUSE":
                if (player != null) player.setPlayWhenReady(false);
                break;
            case "TOGGLE":
                if (player != null) player.setPlayWhenReady(!player.getPlayWhenReady());
                break;
            case "NEXT":
                playbackPresenter.onKeyDown(KeyEvent.KEYCODE_MEDIA_NEXT);
                break;
            case "PREV":
                playbackPresenter.onKeyDown(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                break;
            case "SEEK_FWD":
                if (player != null) {
                    player.setPositionMs(Math.min(player.getPositionMs() + SEEK_AMOUNT_MS, player.getDurationMs()));
                }
                break;
            case "SEEK_BWD":
                if (player != null) {
                    player.setPositionMs(Math.max(player.getPositionMs() - SEEK_AMOUNT_MS, 0));
                }
                break;
            case "VOL_UP":
                playbackPresenter.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP);
                break;
            case "VOL_DOWN":
                playbackPresenter.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN);
                break;
            default:
                Log.d(TAG, "Unknown command action: " + action);
                break;
        }
    }

    private String parseFormValue(String body, String key) {
        if (body == null) return null;
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String k = pair.substring(0, eq).trim();
            String v = pair.substring(eq + 1).trim();
            if (key.equals(k)) {
                return v;
            }
        }
        return null;
    }

    private String buildResponse(int statusCode, String jsonBody) {
        String statusText = statusCode == 200 ? "OK" : statusCode == 400 ? "Bad Request" : statusCode == 404 ? "Not Found" : "Internal Server Error";
        byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        return "HTTP/1.1 " + statusCode + " " + statusText + "\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n"
                + jsonBody;
    }

    private void registerNsdService() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return;
        }
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(NSD_SERVICE_NAME);
        serviceInfo.setServiceType(NSD_SERVICE_TYPE);
        serviceInfo.setPort(SERVER_PORT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            serviceInfo.setAttribute("version", APP_VERSION);
        }

        mNsdRegistrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onRegistrationFailed(NsdServiceInfo info, int errorCode) {
                Log.d(TAG, "NSD registration failed: " + errorCode);
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo info, int errorCode) {
                Log.d(TAG, "NSD unregistration failed: " + errorCode);
            }

            @Override
            public void onServiceRegistered(NsdServiceInfo info) {
                Log.d(TAG, "NSD service registered: " + info.getServiceName());
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo info) {
                Log.d(TAG, "NSD service unregistered: " + info.getServiceName());
            }
        };

        mNsdManager = (NsdManager) getSystemService(NSD_SERVICE);
        if (mNsdManager != null) {
            mNsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, mNsdRegistrationListener);
        }
    }

    private void unregisterNsdService() {
        if (mNsdManager != null && mNsdRegistrationListener != null) {
            try {
                mNsdManager.unregisterService(mNsdRegistrationListener);
            } catch (Exception e) {
                Log.e(TAG, e);
            }
            mNsdRegistrationListener = null;
        }
    }
}
