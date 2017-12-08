package com.example.angelo.babymonitor;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiresApi(api = Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class SensorActivity extends AppCompatActivity implements View.OnClickListener, WifiP2pManager.ConnectionInfoListener, WifiP2pManager.ChannelListener, WifiP2pManager.GroupInfoListener {

    private final static  String TAG = "BABY_MONITOR_DEBUG";
    /*
    * UI elements
    */
    Button disconnectButton = null;
    Button disconnectDevicesButton = null;
    Button createGroupButton = null;

    Button startServerButton = null;

    TextView myDeviceName = null;
    TextView myDeviceAddress = null;
    TextView myDeviceStatus = null;

    ListView connectedDeviceList = null;
    private DeviceListAdapter connectedDeviceListAdapter = null ;

    /*WiFi Direct components*/
    private final IntentFilter intentFilter = new IntentFilter();
    public BroadcastReceiver receiver = null;

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;

    private WifiP2pDevice myDevice;
    private List<WifiP2pDevice> connectedPeers = new ArrayList<WifiP2pDevice>();

    private WifiP2pInfo info;

    private boolean isWifiP2pEnabled = false;

    /*Discovery Service*/
    final HashMap<String, String> buddies = new HashMap<String, String>();
    public static final String TXTRECORD_PROP_AVAILABLE = "available";
    public static final String SERVICE_INSTANCE = "_noiseservice";
    public static final String SERVICE_REG_TYPE = "_presence._tcp";


    /*control flag for sensor service status*/
    private boolean sensorServiceActive = false;

    public static final String DISCONNECT_NOTIFY = "DISCONNECT_NOTIFY";
    public static final String EXTERNAL_DISCONNECT_NOTIFY = "EXT_DISCONNECT NOTIFY";
    public static final String CONNECT_NOTIFY = "CONNECT_NOTIFY";

    private Intent intent = null;

    /*BroadCast Receiver to listen SensorService notifications*/
    private BroadcastReceiver connectionNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent != null) {
                String action = intent.getAction();
                Log.d(TAG, "connectionNotificationReceiver: " + action);
                if (DISCONNECT_NOTIFY.equals(action)) {
                    /*Handling the UI reset when DISCONNECT Button is clicked*/
                    disconnectButton.setEnabled(true);
                    disconnectDevicesButton.setVisibility(View.GONE);

                    //startServerButton.setVisibility(View.VISIBLE);

                    if(info != null && !sensorServiceActive) {
                        Log.d(TAG, "DISCONNECT NOTIFY: startActionStartConnection():");
                        SensorService.startActionStartConnection(SensorActivity.this, info.groupOwnerAddress);
                        sensorServiceActive = true;
                    }
                }
                if(EXTERNAL_DISCONNECT_NOTIFY.equals(action)){
                    /*Handling the UI reset when is RECEIVED a DISCONNECT COMMAND*/
                    disconnectButton.setEnabled(true);
                    disconnectDevicesButton.setVisibility(View.GONE);

                    if(info != null) {
                        Log.d(TAG, "EXTERNAL DISCONNECT NOTIFY: startActionStartConnection():");
                        SensorService.startActionStartConnection(SensorActivity.this, info.groupOwnerAddress);
                        sensorServiceActive = true;
                    }
                }
                if(CONNECT_NOTIFY.equals(action)) {
                    /*Handling the UI reset when is Estabilished a Socket Communication!*/
                    disconnectButton.setEnabled(false);
                    disconnectDevicesButton.setVisibility(View.VISIBLE);

                    startServerButton.setVisibility(View.GONE);
                    sensorServiceActive = true;
                }
            }
        }
    };

    /*Audio Record Permission*/
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionRecordAccepted = false;
    private String[] permissions = {android.Manifest.permission.RECORD_AUDIO};


    /*
    * #############################################################################################
    *                           PERMISSION
    * */

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionRecordAccepted = (grantResults[0] == PackageManager.PERMISSION_GRANTED)? true: false;
                break;
        }
        if (!permissionRecordAccepted){
            Toast.makeText(this, getString(R.string.record_audio_permission_alert), Toast.LENGTH_SHORT).show();
            //ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
            finish();
        }
    }

    /*
    * #############################################################################################
    *                           ACTIVITY LIFECYClE
    * */

    /*
    * Initializating all Views, adapters, the intent filter for a dynamic broadcast receiver
    * and WifiP2p Manager & Channel
    * */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor);

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        createGroupButton = (Button) findViewById(R.id.createGroupButton);
        createGroupButton.setOnClickListener(this);

        disconnectButton = (Button) findViewById(R.id.disconnectButton);
        disconnectButton.setOnClickListener(this);

        disconnectDevicesButton = (Button)findViewById(R.id.disconnectDevicesButton);
        disconnectDevicesButton.setOnClickListener(this);

        myDeviceName = (TextView) findViewById(R.id.myDeviceName);
        myDeviceAddress = (TextView) findViewById(R.id.myDeviceAddress);
        myDeviceStatus = (TextView) findViewById(R.id.myDeviceStatus);

        connectedDeviceList = (ListView) findViewById(R.id.connectedDeviceList);
        connectedDeviceListAdapter = new DeviceListAdapter(SensorActivity.this, connectedPeers);
        connectedDeviceList.setAdapter(connectedDeviceListAdapter);

        startServerButton = (Button) findViewById(R.id.startServer);
        startServerButton.setOnClickListener(this);

        //Change in Wi-Fi P2P status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);

        //Change in Wi-Fi P2P connectivity.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

        //Change in device details.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        /*DELETING ALL EXISTENT WIFI P2P GROUPS to ensure a good group creation!*/
        deletePersistentGroup(manager, channel);
    }

    /*
    * Enable the broadcast receiver and showing Views...
    * */

    @Override
    protected void onStart() {
        super.onStart();

        startServiceRegistration();
    }

    @Override
    public void onResume() {
        super.onResume();

        /* Enabling the receiver if Device is not Connected*/
        receiver = new WifiP2pBroadcastReceiver(manager, channel, this);
        registerReceiver(this.receiver, intentFilter);

        /*Enabling the Local Broadcast Receiver for Sensor Service Connection Notification*/
        IntentFilter disconnectIntentFilter = new IntentFilter();
        disconnectIntentFilter.addAction(SensorService.DISCONNECT_NOTIFY);
        disconnectIntentFilter.addAction(SensorService.CONNECT_NOTIFY);
        disconnectIntentFilter.addAction(SensorService.EXTERNAL_DISCONNECT_NOTIFY);
        LocalBroadcastManager.getInstance(this).registerReceiver(connectionNotificationReceiver, disconnectIntentFilter);

        showMyDevice();

        if(myDevice != null && myDevice.status != WifiP2pDevice.CONNECTED)
            createGroupButton.setVisibility(View.VISIBLE);
    }

    /*
    * Disabling the broadcast receiver
    * */
    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);

        LocalBroadcastManager.getInstance(this).unregisterReceiver(connectionNotificationReceiver);
    }


    /*
    * Disconnetting the WifiP2p Connections (deallocating the group and deleting it!)
    * */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if( myDevice != null)
            disconnect();

        Log.d(TAG, "onDestroy(): Stop SensorService!");
        intent = new Intent(this, SensorService.class);
        stopService(intent);
    }

    /*
    * ############################################################################################
    * *                                 WIFI DIRECT SERVICE DISOVERY
    * */

    /*
    * Registrating the service: creating a service with its informations in a record and adding it!
    * */
    private void startServiceRegistration() {

        Map<String, String> record = new HashMap<String, String>();
        record.put(TXTRECORD_PROP_AVAILABLE, "visible");

        WifiP2pDnsSdServiceInfo service = WifiP2pDnsSdServiceInfo.newInstance(SERVICE_INSTANCE, SERVICE_REG_TYPE, record);
        manager.addLocalService(channel, service, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "startServiceRegistration(): addLocalService() SUCCESS!");
                //Toast.makeText(SensorActivity.this, "Added Local Service!", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onFailure(int error) {
                Log.d(TAG, "startServiceRegistration(): addLocalService() ERROR!"+ error);
                Toast.makeText(SensorActivity.this, "Error Adding Local Service!", Toast.LENGTH_SHORT).show();
            }
        });

    }
    /*
    * #############################################################################################
    *                           WIFI DIRECT CONNECTION & GROUP MANAGEMENT
    * */

    /*
    * CallBack method triggered bye the requesteConnectionInfo on the Broadcast Receiver when the P2p connection status change!
    * Start the related service when the connection is estabilished!
    * */
    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo wifiP2pInfo) {

        info = wifiP2pInfo;

        if(info.isGroupOwner && info.groupFormed){

            /*Sensor handle only one client! so when is connected with a client, we unregister the service!*/
            manager.clearLocalServices(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "startServiceRegistration(): clearLocalServices() SUCCESS!");
                }
                @Override
                public void onFailure(int error) {
                    Log.d(TAG, "startServiceRegistration(): clearLocalServices() ERROR!"+error);
                }
            });

            //sensor is alwyas the group owner
            TextView myDeviceName = (TextView) findViewById(R.id.myDeviceName);
            myDeviceName.setText(getString(R.string.my_device_name_textview) + myDevice.deviceName + "(OWNER)");

            if(!sensorServiceActive) {
                Log.d(TAG, "onGroupInfoAvailable(): startActionStartConnection():");
                /*automatically start the sensor service! (instead of using the button..)*/
                SensorService.startActionStartConnection(this, info.groupOwnerAddress);
                sensorServiceActive = true;
            }
        }
    }

    /*
    * Callback method triggered by RequestGroupInfo on the Broadcast Receiver when P2p connection status changes!
    * We can Update the List of connected devices (with the group owner --> the Sensor Device! that will act as Server!)
    * */
    @Override
    public void onGroupInfoAvailable(WifiP2pGroup wifiP2pGroup) {

        /*The sensor handle just one client! so when it accept a client unregister the service!*/
        if(wifiP2pGroup.getClientList().isEmpty()) {
            startServiceRegistration();
            if(sensorServiceActive) {
                        /*Notify Disconnection to the Listener Activity*/
                intent = new Intent();
                intent.setAction(DISCONNECT_NOTIFY);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            }
        }

        connectedPeers.clear();
        connectedPeers.addAll(wifiP2pGroup.getClientList());
        connectedDeviceListAdapter.notifyDataSetChanged();
    }

    /*
    * Disconnecting the channel (by removing the group!)
    * & calling deletePersistentGroup to delete the static groups stored!
    * */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void disconnect() {

        if(myDevice.status == WifiP2pDevice.CONNECTED)
            manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "disconnect(): removeGroup() SUCCESS!");
                    //Toast.makeText(SensorActivity.this, "Disconnected!", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onFailure(int error) {
                    Log.d(TAG, "disconnect(): removeGroup() ERROR!"+ error);
                    Toast.makeText(SensorActivity.this, "Disconnection error! (" + error + ")", Toast.LENGTH_SHORT).show();
                }
            });

        deletePersistentGroup(manager, channel);

        hideDeviceList();

         /*Handling socket communication disconnections*/
        if(sensorServiceActive)
            if(connectedPeers != null && !connectedPeers.isEmpty())
                SensorService.startActionDisconnect(this);
            else
                SensorService.startActionStopConnection(this);

        sensorServiceActive = false;
    }

    /*
    * Deleting persistent groups!
    * (because estabilishing a WifiP2p connection leads the memoration of a persistent group, and all future connections
    * between the same devices, will restore that groups, with that choosen group owner...)
    * */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public static void deletePersistentGroup(WifiP2pManager manager, WifiP2pManager.Channel channel) {
        try {
            Method method = WifiP2pManager.class.getMethod("deletePersistentGroup",
                    WifiP2pManager.Channel.class, int.class, WifiP2pManager.ActionListener.class);

            for (int netId = 0; netId < 32; netId++) {
                method.invoke(manager, channel, netId, null);
            }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    /*
    * Callback method to inizialize the Manager when the channel is disconnected!
    * */
    @Override
    public void onChannelDisconnected() {
        if(manager != null){
            Toast.makeText(this, "Channel Lost! Trying to restore ...", Toast.LENGTH_SHORT).show();

            manager.initialize(this, getMainLooper(), this);
        }
    }

    /*
    *  Creating a group with Group Owner the Sensor node!
    *  So Listener Devices can discover and connect to it!
    * */
    protected void createSensorGroup(){
        manager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "createSensorGroup(): createGroup() SUCCESS!");
                //Toast.makeText(SensorActivity.this, "Group created! Listening ...", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int error) {
                Log.d(TAG, "createSensorGroup(): createGroup() ERROR!"+error);
                Toast.makeText(SensorActivity.this, "Error creating the group! ("+error+")", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /*
    * #############################################################################################
    *                           HANDLE USER CLICKS on LISTVIEW and BUTTONS
    * */

    /*
    * Handle the USER ACTIONS -->
    *  #CREATE GROUP in case of Sensor Device (after a disconnection)
    *  and the DISCONNECT BUTTON to delete a group
    * */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public void onClick(View view) {

        if(view.getId() == R.id.createGroupButton) {
            createSensorGroup();
            createGroupButton.setVisibility(View.GONE);
        }

        if(view.getId() == R.id.disconnectButton){

            createGroupButton.setVisibility(View.VISIBLE);
            disconnect();
        }

        if(view.getId() == R.id.disconnectDevicesButton){

            /*Advertise the listener and close all sockets and thrads*/
            if(sensorServiceActive) {
                SensorService.startActionDisconnect(this);
                sensorServiceActive = false;
            }
        }

        if(view.getId() == R.id.startServer) {

            /*start socket communication*/
            if(!sensorServiceActive)
                Log.d(TAG, "startServerButton: startActionStartConnection():");
                SensorService.startActionStartConnection(this, info.groupOwnerAddress);
                sensorServiceActive = true;
        }
    }

    /*
    * #############################################################################################
    *                           MANAGE GUI ELEMETS
    * */

    /*
    *  Showing and Hiding the List Views
    * */
    public void showDeviceList() {

        findViewById(R.id.peersBar).setVisibility(View.VISIBLE);
        connectedDeviceList.setVisibility(View.VISIBLE);
    }

    public void hideDeviceList(){

        findViewById(R.id.peersBar).setVisibility(View.GONE);

        connectedDeviceList.setVisibility(View.GONE);
        connectedPeers.clear();
        connectedDeviceListAdapter.notifyDataSetChanged();
    }

    /*
    * Showing and Hiding the TextAreas with Info about my device
    * */
    public void showMyDevice(){

        findViewById(R.id.myDeviceBar).setVisibility(View.VISIBLE);
        myDeviceName.setVisibility(View.VISIBLE);
        myDeviceAddress.setVisibility(View.VISIBLE);
        myDeviceStatus.setVisibility(View.VISIBLE);

        if( myDevice!= null && myDevice.status == WifiP2pDevice.CONNECTED) {
            disconnectButton.setVisibility(View.VISIBLE);
            createGroupButton.setVisibility(View.GONE);
        }
    }

    public void hideMyDevice(){

        findViewById(R.id.myDeviceBar).setVisibility(View.GONE);
        myDeviceName.setVisibility(View.GONE);
        myDeviceAddress.setVisibility(View.GONE);
        myDeviceStatus.setVisibility(View.GONE);
    }


    /*
    * Utility function to convert the WifiP2p Device Status from number to string
    * */
    public String getStatus(int statusCode){
        String status = null;
        if(statusCode == WifiP2pDevice.AVAILABLE)
            status = "Available";
        if(statusCode == WifiP2pDevice.CONNECTED)
            status = "Connected";
        if(statusCode == WifiP2pDevice.FAILED)
            status = "Failed";
        if(statusCode == WifiP2pDevice.INVITED)
            status = "Invited";
        if(statusCode == WifiP2pDevice.UNAVAILABLE)
            status = "Unavailable";
        return status;
    }

    /*
    * #############################################################################################
    *                       UPDATE INFO BY THE BROADCAST RECEIVER
    * */

    /*
    * Function called by the Broadcast Receiver when the infos about my device change..
    * */
    public void updateMyDevice(WifiP2pDevice device){
        myDevice = device;

        myDeviceName.setText(getString(R.string.my_device_name_textview) + myDevice.deviceName);
        myDeviceAddress.setText(getString(R.string.my_device_address_textview) + myDevice.deviceAddress);
        myDeviceStatus.setText(getString(R.string.my_device_status_textview) + getStatus(myDevice.status));

        if(myDevice != null){

            if(myDevice.status == WifiP2pDevice.AVAILABLE) {
                connectedPeers.clear();
                connectedDeviceListAdapter.notifyDataSetChanged();

                createGroupButton.setVisibility(View.VISIBLE);
                disconnectDevicesButton.setVisibility(View.GONE);
            }
            if(myDevice.status == WifiP2pDevice.CONNECTED)
                createGroupButton.setVisibility(View.GONE);
        }

    }

    /*
    * Setting a boolean to know if the Wifi is enabled...
    */
    public void setIsWifiP2pEnabled(boolean isEnabled){
        isWifiP2pEnabled = isEnabled;
    }

    public boolean isSensorServiceActive(){
        return sensorServiceActive;
    }
}
