package com.example.angelo.babymonitor;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.widget.Toast;


public class WifiP2pBroadcastReceiver extends BroadcastReceiver {

    /*Notification to the Listener and Sensor Activity*/
    public static final String DISCONNECT_NOTIFY = "DISCONNECT_NOTIFY";

    private WifiP2pManager manager;
    private android.net.wifi.p2p.WifiP2pManager.Channel channel;
    private Activity activity;

    public WifiP2pBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel, Activity activity) {

        this.manager = manager;
        this.channel = channel;
        this.activity = activity;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        /*
        * Handling when the Wifi is disabled --> Enable it automatically!
        * */
        if(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)){

            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);

            if(state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                if(ListenerActivity.class == activity.getClass())
                    ((ListenerActivity)activity).setIsWifiP2pEnabled(true);

                if(SensorActivity.class == activity.getClass())
                    ((SensorActivity)activity).setIsWifiP2pEnabled(true);
            }
            else{
                if(ListenerActivity.class == activity.getClass())
                    ((ListenerActivity)activity).setIsWifiP2pEnabled(false);

                if(SensorActivity.class == activity.getClass())
                    ((SensorActivity)activity).setIsWifiP2pEnabled(false);

                WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                wifi.setWifiEnabled(true);

                Toast.makeText(activity, "Wifi has been automatically Enabled!", Toast.LENGTH_SHORT).show();
            }

        }
        /*
        * Handling when the P2P connection status change --> requesting connection info to trigger the onConnectionInfoAvailable
        *                                               --> requesting group info (for the Sensor) to trigger onGroupInfoAvailable
        * Handling button visibility!
        * */
        else if(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)){

            if(manager == null)
                return;

            NetworkInfo netInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

            if(netInfo.isConnected()){
                if(ListenerActivity.class == activity.getClass()){
                    /*requesting info about connection --> i.e. group ownerIP Address*/
                    manager.requestConnectionInfo(channel, (ListenerActivity)activity);

                    ((ListenerActivity)activity).disconnectButton.setVisibility(View.VISIBLE);
                    ((ListenerActivity)activity).showDeviceList();
                }
                if(SensorActivity.class == activity.getClass()){
                    /*requesting info about connection --> i.e. group ownerIP Address*/
                    manager.requestConnectionInfo(channel, (SensorActivity)activity);

                    /*requesting group info to update connected clients!*/
                    manager.requestGroupInfo(channel, (SensorActivity)activity);

                    /*Check if it is a new start! (to handle connectins not disconnected)*/
                    ((SensorActivity)activity).disconnectButton.setVisibility(View.VISIBLE);
                    ((SensorActivity)activity).showDeviceList();
                }
                //Toast.makeText(activity, "WiFi P2P Connected!", Toast.LENGTH_SHORT).show();
            }
            else{
                if(ListenerActivity.class == activity.getClass()){

                    ((ListenerActivity)activity).disconnectButton.setVisibility(View.GONE);

                    if( ((ListenerActivity)activity).isListenerServiceActive()) {
                        /*Notify Disconnection to the Listener Activity*/
                        intent = new Intent();
                        intent.setAction(DISCONNECT_NOTIFY);
                        LocalBroadcastManager.getInstance(activity).sendBroadcast(intent);
                    }
                }
                if(SensorActivity.class == activity.getClass()) {

                    ((SensorActivity)activity).disconnectButton.setVisibility(View.GONE);
                }
                //Toast.makeText(activity, "WiFi P2P Disconnected!", Toast.LENGTH_SHORT).show();
            }
        }
        /*
        * Handling changer streamInput my device --> i.e. P2p Connection status
        * */
        else if(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {

            WifiP2pDevice myDev = (WifiP2pDevice) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);

            if (myDev != null) {
                if (ListenerActivity.class == activity.getClass())
                    ((ListenerActivity) activity).updateMyDevice(myDev);

                if (SensorActivity.class == activity.getClass())
                    ((SensorActivity) activity).updateMyDevice(myDev);
            }
        }
    }

}
