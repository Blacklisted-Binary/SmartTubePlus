package com.liskovsoft.smartyoutubetv2.companion;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * RecyclerView adapter that displays a list of discovered SmartTubePlus TV devices.
 */
public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.ViewHolder> {

    public interface OnDeviceClickListener {
        void onDeviceClicked(TvDeviceDiscovery.TvDevice device);
    }

    private List<TvDeviceDiscovery.TvDevice> mDevices;
    private final OnDeviceClickListener mListener;

    public DeviceListAdapter(List<TvDeviceDiscovery.TvDevice> devices, OnDeviceClickListener listener) {
        this.mDevices = devices;
        this.mListener = listener;
    }

    public void setDevices(List<TvDeviceDiscovery.TvDevice> devices) {
        this.mDevices = devices;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tv_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TvDeviceDiscovery.TvDevice device = mDevices.get(position);
        holder.tvName.setText(device.name);
        holder.tvAddress.setText(device.host + ":" + device.port);
        holder.itemView.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onDeviceClicked(device);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mDevices == null ? 0 : mDevices.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvAddress;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_device_name);
            tvAddress = itemView.findViewById(R.id.tv_device_address);
        }
    }
}
