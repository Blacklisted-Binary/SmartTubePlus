package com.liskovsoft.smartyoutubetv2.companion;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Main remote-control screen.
 *
 * <p>Layout sections:
 * <ol>
 *   <li>Status bar showing the selected device (or "Searching…")</li>
 *   <li>RecyclerView listing discovered SmartTubePlus TV devices</li>
 *   <li>D-pad remote-control buttons (play/pause, seek, volume, next/prev)</li>
 * </ol>
 */
public class MainActivity extends AppCompatActivity {

    private CompanionViewModel mViewModel;
    private TextView mTvStatus;
    private DeviceListAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mViewModel = new ViewModelProvider(this).get(CompanionViewModel.class);

        // Status bar
        mTvStatus = findViewById(R.id.tv_status);

        // Device list
        RecyclerView rvDevices = findViewById(R.id.rv_devices);
        rvDevices.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new DeviceListAdapter(new ArrayList<>(), device -> mViewModel.selectDevice(device));
        rvDevices.setAdapter(mAdapter);

        // Observe VM
        mViewModel.getDevices().observe(this, this::onDevicesUpdated);
        mViewModel.getStatusMessage().observe(this, mTvStatus::setText);

        // Remote-control buttons
        bindControlButton(R.id.btn_play,      () -> mViewModel.sendCommand("PLAY"));
        bindControlButton(R.id.btn_pause,     () -> mViewModel.sendCommand("PAUSE"));
        bindControlButton(R.id.btn_toggle,    () -> mViewModel.sendCommand("TOGGLE"));
        bindControlButton(R.id.btn_next,      () -> mViewModel.sendCommand("NEXT"));
        bindControlButton(R.id.btn_prev,      () -> mViewModel.sendCommand("PREV"));
        bindControlButton(R.id.btn_seek_fwd,  () -> mViewModel.sendCommand("SEEK_FWD"));
        bindControlButton(R.id.btn_seek_bwd,  () -> mViewModel.sendCommand("SEEK_BWD"));
        bindControlButton(R.id.btn_vol_up,    () -> mViewModel.sendCommand("VOL_UP"));
        bindControlButton(R.id.btn_vol_down,  () -> mViewModel.sendCommand("VOL_DOWN"));
    }

    private void onDevicesUpdated(List<TvDeviceDiscovery.TvDevice> devices) {
        mAdapter.setDevices(devices);
        if (devices.isEmpty()) {
            findViewById(R.id.tv_no_devices).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.tv_no_devices).setVisibility(View.GONE);
        }
    }

    private void bindControlButton(int viewId, Runnable action) {
        View btn = findViewById(viewId);
        if (btn != null) {
            btn.setOnClickListener(v -> action.run());
        }
    }
}
