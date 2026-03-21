package com.liskovsoft.smartyoutubetv2.companion;

import android.app.Application;
import android.net.nsd.NsdManager;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel shared between {@link MainActivity} and {@link ShareHandlerActivity}.
 *
 * <p>Holds the list of discovered TV devices, the currently selected device, and
 * the {@link TvRemoteClient} singleton used to send commands.</p>
 */
public class CompanionViewModel extends AndroidViewModel implements TvDeviceDiscovery.Listener {

    private final TvDeviceDiscovery mDiscovery;
    private final TvRemoteClient mClient = new TvRemoteClient();

    private final MutableLiveData<List<TvDeviceDiscovery.TvDevice>> mDevices =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<TvDeviceDiscovery.TvDevice> mSelectedDevice =
            new MutableLiveData<>(null);
    private final MutableLiveData<String> mStatusMessage =
            new MutableLiveData<>("");

    public CompanionViewModel(@NonNull Application application) {
        super(application);
        NsdManager nsdManager = (NsdManager) application.getSystemService(Application.NSD_SERVICE);
        mDiscovery = new TvDeviceDiscovery(nsdManager, this);
        mDiscovery.start();
    }

    // ---- LiveData accessors -------------------------------------------------

    public LiveData<List<TvDeviceDiscovery.TvDevice>> getDevices() {
        return mDevices;
    }

    public LiveData<TvDeviceDiscovery.TvDevice> getSelectedDevice() {
        return mSelectedDevice;
    }

    public LiveData<String> getStatusMessage() {
        return mStatusMessage;
    }

    // ---- Device selection ---------------------------------------------------

    public void selectDevice(TvDeviceDiscovery.TvDevice device) {
        mSelectedDevice.setValue(device);
        mClient.setDevice(device);
        if (device != null) {
            setStatus(getApplication().getString(R.string.status_connected, device.name));
        }
    }

    // ---- Playback / command -------------------------------------------------

    public void play(String youtubeUrl) {
        if (mClient.getDevice() == null) {
            setStatus(getApplication().getString(R.string.error_no_device));
            return;
        }
        setStatus(getApplication().getString(R.string.status_sending));
        mClient.play(youtubeUrl, new TvRemoteClient.Callback() {
            @Override
            public void onSuccess() {
                setStatus(getApplication().getString(R.string.status_sent_ok));
            }

            @Override
            public void onError(String message) {
                setStatus(getApplication().getString(R.string.error_send_failed, message));
            }
        });
    }

    public void sendCommand(String action) {
        if (mClient.getDevice() == null) {
            setStatus(getApplication().getString(R.string.error_no_device));
            return;
        }
        mClient.command(action, new TvRemoteClient.Callback() {
            @Override
            public void onSuccess() { /* silent */ }

            @Override
            public void onError(String message) {
                setStatus(getApplication().getString(R.string.error_send_failed, message));
            }
        });
    }

    // ---- TvDeviceDiscovery.Listener -----------------------------------------

    @Override
    public void onDevicesChanged(List<TvDeviceDiscovery.TvDevice> devices) {
        mDevices.setValue(new ArrayList<>(devices));

        // Auto-select the first device if nothing is selected yet
        TvDeviceDiscovery.TvDevice current = mSelectedDevice.getValue();
        if ((current == null || !devices.contains(current)) && !devices.isEmpty()) {
            selectDevice(devices.get(0));
        } else if (devices.isEmpty()) {
            selectDevice(null);
            setStatus(getApplication().getString(R.string.status_no_devices));
        }
    }

    // ---- Internal -----------------------------------------------------------

    private void setStatus(String message) {
        mStatusMessage.postValue(message);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        mDiscovery.stop();
        mClient.shutdown();
    }
}
