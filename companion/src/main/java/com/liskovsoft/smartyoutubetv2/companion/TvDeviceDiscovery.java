package com.liskovsoft.smartyoutubetv2.companion;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers SmartTubePlus TV devices on the local network using Android NSD (mDNS).
 *
 * <p>The TV app advertises itself as {@code _smarttube._tcp} on port 8787.  This class
 * browses for those services, resolves their addresses, and notifies a listener.</p>
 */
public class TvDeviceDiscovery {

    public static final String TAG = TvDeviceDiscovery.class.getSimpleName();
    private static final String SERVICE_TYPE = "_smarttube._tcp.";

    /** Immutable value-object representing a discovered TV device. */
    public static class TvDevice {
        public final String name;
        public final String host;
        public final int port;

        public TvDevice(String name, String host, int port) {
            this.name = name;
            this.host = host;
            this.port = port;
        }

        @Override
        public String toString() {
            return name + " (" + host + ":" + port + ")";
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TvDevice)) return false;
            TvDevice d = (TvDevice) o;
            return port == d.port && name.equals(d.name) && host.equals(d.host);
        }

        @Override
        public int hashCode() {
            return name.hashCode() * 31 + host.hashCode();
        }
    }

    public interface Listener {
        void onDevicesChanged(List<TvDevice> devices);
    }

    private final NsdManager mNsdManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Listener mListener;
    private NsdManager.DiscoveryListener mDiscoveryListener;
    private boolean mDiscovering = false;

    /** Keyed by NSD service name so that lost/found events stay consistent. */
    private final Map<String, TvDevice> mDevices = new LinkedHashMap<>();

    public TvDeviceDiscovery(NsdManager nsdManager, Listener listener) {
        this.mNsdManager = nsdManager;
        this.mListener = listener;
    }

    /** Start browsing for TV devices. Safe to call multiple times. */
    public void start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN || mDiscovering) {
            return;
        }
        mDiscoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.w(TAG, "Discovery start failed: " + errorCode);
                mDiscovering = false;
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.w(TAG, "Discovery stop failed: " + errorCode);
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.d(TAG, "Discovery started for " + serviceType);
                mDiscovering = true;
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "Discovery stopped");
                mDiscovering = false;
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service found: " + serviceInfo.getServiceName());
                resolveService(serviceInfo);
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service lost: " + serviceInfo.getServiceName());
                mDevices.remove(serviceInfo.getServiceName());
                notifyListener();
            }
        };

        mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    /** Stop browsing.  Safe to call even if not started. */
    public void stop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return;
        }
        if (mDiscoveryListener != null && mDiscovering) {
            try {
                mNsdManager.stopServiceDiscovery(mDiscoveryListener);
            } catch (Exception e) {
                Log.w(TAG, "stopServiceDiscovery: " + e.getMessage());
            }
        }
        mDiscoveryListener = null;
        mDiscovering = false;
    }

    private void resolveService(NsdServiceInfo serviceInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return;
        }
        mNsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo info, int errorCode) {
                Log.w(TAG, "Resolve failed for " + info.getServiceName() + " code=" + errorCode);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo info) {
                InetAddress addr = info.getHost();
                if (addr == null) {
                    Log.w(TAG, "Resolved but host is null: " + info.getServiceName());
                    return;
                }
                String host = addr.getHostAddress();
                TvDevice device = new TvDevice(info.getServiceName(), host, info.getPort());
                Log.d(TAG, "Resolved: " + device);
                mDevices.put(info.getServiceName(), device);
                notifyListener();
            }
        });
    }

    private void notifyListener() {
        List<TvDevice> snapshot = new ArrayList<>(mDevices.values());
        mMainHandler.post(() -> mListener.onDevicesChanged(snapshot));
    }
}
