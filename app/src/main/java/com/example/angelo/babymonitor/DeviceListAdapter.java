package com.example.angelo.babymonitor;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class DeviceListAdapter extends BaseAdapter {

    private Activity activity;
    private List<WifiP2pDevice> devicePeers;
    private static LayoutInflater inflater = null;


    public DeviceListAdapter(Activity activity, List<WifiP2pDevice> deviceList){
        this.activity = activity;
        this.devicePeers = deviceList;
        inflater = (LayoutInflater)activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return devicePeers.size();
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {

        View vi = view;

        if(vi == null)
            vi = inflater.inflate(R.layout.device_list_row, null);

        TextView name = (TextView)vi.findViewById(R.id.deviceName);
        TextView address = (TextView)vi.findViewById(R.id.deviceAddress);
        TextView status = (TextView)vi.findViewById(R.id.deviceStatus);

        name.setText(activity.getString(R.string.my_device_name_textview) + devicePeers.get(i).deviceName);
        address.setText(activity.getString(R.string.my_device_address_textview) + devicePeers.get(i).deviceAddress);

        if(ListenerActivity.class == activity.getClass())
            status.setText(activity.getString(R.string.my_device_status_textview) + ((ListenerActivity)activity).getStatus(devicePeers.get(i).status));

        if(SensorActivity.class == activity.getClass())
            status.setText(activity.getString(R.string.my_device_status_textview) + ((SensorActivity)activity).getStatus(devicePeers.get(i).status));
        return vi;
    }

}

