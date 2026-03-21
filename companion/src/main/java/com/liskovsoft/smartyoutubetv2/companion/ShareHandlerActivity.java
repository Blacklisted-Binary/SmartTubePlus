package com.liskovsoft.smartyoutubetv2.companion;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Transparent activity that acts as the share / VIEW target for YouTube links.
 *
 * <p>Flow:
 * <ol>
 *   <li>User taps "Share" in the YouTube app and picks "Cast to SmartTubePlus".</li>
 *   <li>Android starts this activity with an {@code ACTION_SEND} or {@code ACTION_VIEW} intent.</li>
 *   <li>If a TV device is already selected, the URL is forwarded immediately.</li>
 *   <li>If multiple devices are discovered, a picker dialog is shown.</li>
 *   <li>If no devices are found yet, a brief toast is shown and discovery waits briefly.</li>
 * </ol>
 */
public class ShareHandlerActivity extends AppCompatActivity {

    private static final long DISCOVERY_WAIT_MS = 3_000;

    private CompanionViewModel mViewModel;
    private String mPendingUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPendingUrl = extractUrl(getIntent());
        if (mPendingUrl == null) {
            finish();
            return;
        }

        mViewModel = new ViewModelProvider(this).get(CompanionViewModel.class);

        // If a device is already paired, send immediately
        TvDeviceDiscovery.TvDevice current = mViewModel.getSelectedDevice().getValue();
        if (current != null) {
            sendAndFinish(current, mPendingUrl);
            return;
        }

        // Wait briefly for discovery, then show picker or error
        List<TvDeviceDiscovery.TvDevice> found = mViewModel.getDevices().getValue();
        if (found != null && !found.isEmpty()) {
            showDevicePicker(mPendingUrl, found);
        } else {
            Toast.makeText(this, R.string.status_searching, Toast.LENGTH_SHORT).show();
            mViewModel.getDevices().observe(this, devices -> {
                if (devices != null && !devices.isEmpty()) {
                    mViewModel.getDevices().removeObservers(this);
                    if (devices.size() == 1) {
                        sendAndFinish(devices.get(0), mPendingUrl);
                    } else {
                        showDevicePicker(mPendingUrl, devices);
                    }
                }
            });
            // Timeout fallback: finish after wait if still no devices
            getWindow().getDecorView().postDelayed(() -> {
                if (!isFinishing()) {
                    Toast.makeText(this, R.string.error_no_device, Toast.LENGTH_LONG).show();
                    finish();
                }
            }, DISCOVERY_WAIT_MS);
        }
    }

    private void showDevicePicker(String url, List<TvDeviceDiscovery.TvDevice> devices) {
        String[] names = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            names[i] = devices.get(i).name + " – " + devices.get(i).host;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.cast_to_tv)
                .setItems(names, (dialog, which) -> sendAndFinish(devices.get(which), url))
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void sendAndFinish(TvDeviceDiscovery.TvDevice device, String url) {
        mViewModel.selectDevice(device);
        mViewModel.play(url);
        Toast.makeText(this, getString(R.string.status_casting_to, device.name), Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * Extracts a YouTube URL from either an {@code ACTION_SEND} (share) or
     * {@code ACTION_VIEW} (deep link) intent.
     */
    private static String extractUrl(Intent intent) {
        if (intent == null) return null;

        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                // The YouTube app typically sends the URL either alone or embedded in text.
                // Extract the first http(s) URL found.
                return extractFirstUrl(text);
            }
        }

        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            Uri data = intent.getData();
            String host = data.getHost();
            if (host != null && (host.contains("youtube.com") || host.contains("youtu.be"))) {
                return data.toString();
            }
        }

        return null;
    }

    private static String extractFirstUrl(String text) {
        if (text == null) return null;
        // Split on whitespace and look for a youtube URL token
        for (String token : text.split("\\s+")) {
            if (token.startsWith("http://") || token.startsWith("https://")) {
                if (token.contains("youtube.com") || token.contains("youtu.be")) {
                    return token;
                }
            }
        }
        // Fallback: return the whole text if it looks like a URL
        if (text.trim().startsWith("http") && (text.contains("youtube.com") || text.contains("youtu.be"))) {
            return text.trim();
        }
        return null;
    }
}
